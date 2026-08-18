package com.dacs.attendance.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dacs.attendance.BuildConfig
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
import javax.inject.Singleton

private const val TAG = "SupabaseModule"
private const val SESSION_PREFS = "dacs_attendance_session"

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(
        @ApplicationContext context: Context
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
        // Declared now, used in B3. It shares this client's ktor engine and
        // session, so adding it later would swap the HTTP stack underneath
        // an app that is already working.
        install(Storage)
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
 *    try encrypted, retry once with a clean store in case of corruption,
 *    and only then degrade -- loudly.
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
        Log.w(TAG, "Encrypted session store unreadable, clearing and retrying", first)
        context.deleteSharedPreferences(SESSION_PREFS)
        try {
            encrypted()
        } catch (second: Exception) {
            Log.e(TAG, "Keystore unusable on this device -- session stored unencrypted", second)
            context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        }
    }
}
