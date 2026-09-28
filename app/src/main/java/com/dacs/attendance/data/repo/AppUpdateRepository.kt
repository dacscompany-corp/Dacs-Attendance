package com.dacs.attendance.data.repo

import android.content.Context
import android.util.Log
import com.dacs.attendance.BuildConfig
import com.dacs.attendance.data.local.AppUpdateCache
import com.dacs.attendance.data.remote.AppReleaseRow
import com.dacs.attendance.data.remote.UpdateNudges
import com.dacs.attendance.domain.AppRelease
import com.dacs.attendance.domain.AppUpdatePolicy
import com.dacs.attendance.domain.ApkVerification
import com.dacs.attendance.domain.CopyResult
import com.dacs.attendance.domain.UpdateFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "AppUpdateRepository"

/** The `app_latest_release` RPC from migration 0079. Callable signed out. */
private const val LATEST_RELEASE = "app_latest_release"

/** Under cacheDir, and the only directory res/xml/update_file_paths.xml exposes. */
private const val UPDATE_DIR = "updates"

sealed interface DownloadResult {
    data class Ready(val file: File) : DownloadResult
    data class Failed(val reason: UpdateFailure) : DownloadResult
}

interface AppUpdateRepository {

    /**
     * The release this build must be replaced by, or null when it is
     * current -- or when the server cannot be asked and nothing is
     * cached. Never throws: see [AppUpdatePolicy.required].
     */
    suspend fun requiredRelease(): AppRelease?

    /** Downloads [release] and proves it is the published file before returning it. */
    suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): DownloadResult

    /** Deletes every downloaded APK. Called once this build is current. */
    fun discardDownloads()

    /**
     * Fires when something outside the dialog learns this build is out of
     * date -- the server refusing a Time In with APP_UPDATE_REQUIRED
     * because the office published while the app was open.
     */
    val nudges: SharedFlow<Unit>
}

@Singleton
class SupabaseAppUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: SupabaseClient,
    private val cache: AppUpdateCache,
    updateNudges: UpdateNudges
) : AppUpdateRepository {

    // A plain client, not Supabase's: the APK is a public Storage object,
    // and the version header / clock anchor on the Supabase engine have
    // nothing to say about a file download.
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override val nudges: SharedFlow<Unit> = updateNudges.events

    override suspend fun requiredRelease(): AppRelease? {
        val fetched = runCatchingExceptCancellation {
            client.postgrest.rpc(LATEST_RELEASE)
                .decodeList<AppReleaseRow>()
                .firstOrNull()
                ?.toDomain()
        }
        fetched.exceptionOrNull()?.let { Log.w(TAG, "Update check failed, using cache", it) }

        val required = AppUpdatePolicy.required(BuildConfig.VERSION_CODE, fetched, cache.read())
        cache.write(required)
        return required
    }

    override suspend fun download(
        release: AppRelease,
        onProgress: (Float) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
        // One APK on disk at a time: an old, abandoned download is 5 MB
        // of a cheap phone's storage for nothing.
        dir.listFiles()?.forEach { it.delete() }

        val target = File(dir, "dacs-attendance-${release.versionCode}.apk")
        val partial = File(dir, "${target.name}.part")

        try {
            // runInterruptible: leaving the dialog cancels the scope, and
            // OkHttp answers the interrupt by closing the socket.
            val result = runInterruptible {
                http.newCall(Request.Builder().url(release.downloadUrl).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty body")
                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            ApkVerification.copyVerifying(
                                input, output, release.sizeBytes, release.sha256, onProgress
                            )
                        }
                    }
                }
            }

            when (result) {
                CopyResult.Verified -> {
                    if (!partial.renameTo(target)) throw IOException("Could not keep the download")
                    DownloadResult.Ready(target)
                }
                CopyResult.WrongSize, CopyResult.WrongHash -> {
                    Log.w(TAG, "Downloaded APK failed verification: $result")
                    partial.delete()
                    DownloadResult.Failed(UpdateFailure.FileDamaged)
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Update download failed", e)
            partial.delete()
            DownloadResult.Failed(UpdateFailure.DownloadFailed)
        }
    }

    override fun discardDownloads() {
        File(context.cacheDir, UPDATE_DIR).listFiles()?.forEach { it.delete() }
    }
}
