package com.dacs.attendance.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dacs.attendance.BuildConfig
import com.dacs.attendance.data.local.ClockAnchorStore
import com.russhwolf.settings.SharedPreferencesSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton
import kotlinx.serialization.json.Json

private const val TAG = "SupabaseModule"
private const val SESSION_PREFS = "dacs_attendance_session"

/** Read by 0077's attendance_app_version_guard. Lower-case: PostgREST lower-cases header names. */
private const val APP_VERSION_HEADER = "x-dacs-app-version"

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(
        @ApplicationContext context: Context,
        clockAnchors: ClockAnchorStore
    ): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth) {
            // Workers stay logged in across restarts and reboots. This
            // matches the web's deliberate persistSession: true, and it
            // matters more here -- a worker at 07:45 on a site with no
            // signal cannot re-authenticate, so the session has to
            // already be on the device.
            sessionManager = SettingsSessionManager(
                SharedPreferencesSettings(sessionPreferences(context))
            )
            autoLoadFromStorage = true
            alwaysAutoRefresh = true
        }
        install(Postgrest)

        // ── WHICH BUILD IS TALKING. Sent on every request so the server
        //    can refuse attendance from a build it no longer trusts (0077).
        //
        //    Found necessary on 2026-09-27: the Location-off fix shipped
        //    in the app, not the server, so every phone still carrying
        //    the old APK kept the loophole -- and the server had no way
        //    to tell the two builds apart. The number is versionCode, not
        //    versionName: an integer the SQL can compare without parsing.
        httpConfig {
            defaultRequest {
                header(APP_VERSION_HEADER, BuildConfig.VERSION_CODE.toString())
            }
            // ── THE SERVER'S CLOCK, written down on every answer (0078).
            //    The anchor the trusted shutter time is measured from --
            //    see TrustedTime.kt. Every response carries a Date header,
            //    so any contact at all (the profile read at launch, a
            //    history page) refreshes it.
            ResponseObserver { response ->
                clockAnchors.recordServerDate(response.headers[HttpHeaders.Date])
            }
        }

        // Declared now, used in B3. It shares this client's ktor engine and
        // session, so adding it later would swap the HTTP stack underneath
        // an app that is already working.
        install(Storage)
    }

    /**
     * The client for the one call that is NOT a Supabase SDK call: the
     * sign-in Edge Function.
     *
     * Timeouts are short and explicit. A worker standing in the sun
     * needs to be told "no signal" quickly, not left watching a spinner
     * until ktor's default gives up.
     */
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            )
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
        // Ktor throwing on a 4xx would turn the function's refusal codes
        // into exceptions; SignInApi reads the status itself.
        expectSuccess = false
    }
}

/**
 * The session store, encrypted at rest.
 *
 * TWO deliberate compromises live here, both worth knowing about:
 *
 * 1. EncryptedSharedPreferences is DEPRECATED -- Google deprecated the
 *    whole of Jetpack Security in 2025 without shipping a replacement,
 *    pointing instead at app-private storage plus the Keystore. It still
 *    works and it is still the least-effort way to keep a refresh token
 *    off disk in the clear, so it stays until there is a real successor.
 *
 * 2. The plaintext fallback is not a shrug. Our users are on cheap, old,
 *    sometimes vendor-modified phones where the Android keystore is a
 *    known source of hard failures. A worker who cannot log in because
 *    their keystore is broken is a far worse outcome than a refresh
 *    token sitting in app-private storage on a non-rooted device. So:
 *    try encrypted, retry, clear only if the store itself looks broken,
 *    and only then degrade -- loudly.
 *
 * ── WHY THE PLAIN RETRY COMES BEFORE THE DELETE (fixed after reports of
 *    workers being signed out without signing out).
 *
 *    This store is where the REFRESH TOKEN lives, so deleting it is a
 *    sign-out -- a silent one the worker never asked for, on a phone that
 *    still shows them as logged in until the next launch.
 *
 *    It used to delete on the first exception of any kind. But the
 *    failures these phones actually produce are mostly TRANSIENT: the
 *    AndroidKeyStore is momentarily unavailable, busy, or wedged just
 *    after boot -- exactly when the app is opened. Treating that as a
 *    corrupt store destroyed a perfectly good session over a hiccup that
 *    a second attempt would have survived.
 *
 *    So the second attempt is made on the EXISTING store. Only if that
 *    fails too do we accept the store is unreadable and clear it. A
 *    genuinely corrupt store fails both attempts and is still recovered;
 *    a transient keystore failure now costs one retry instead of
 *    everyone's session.
 */
@Suppress("DEPRECATION")
private fun sessionPreferences(context: Context): SharedPreferences {
    fun encrypted(): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        SESSION_PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    return try {
        encrypted()
    } catch (first: Exception) {
        // Nothing destroyed yet. The session is still on disk.
        Log.w(TAG, "Encrypted session store failed to open, retrying before clearing it", first)
        try {
            encrypted()
        } catch (second: Exception) {
            // Twice in a row: treat the store as corrupt rather than the
            // keystore as busy. This is the branch that costs the worker
            // their session, so it is the last resort, not the first.
            Log.w(TAG, "Encrypted session store unreadable twice, clearing it", second)
            context.deleteSharedPreferences(SESSION_PREFS)
            try {
                encrypted()
            } catch (third: Exception) {
                Log.e(TAG, "Keystore unusable on this device -- session stored unencrypted", third)
                context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            }
        }
    }
}
