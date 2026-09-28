package com.dacs.attendance.domain

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * One APK the office published from the admin portal (`app_releases`, 0079).
 *
 * [sha256] and [sizeBytes] were computed by the office's browser from the
 * very file it uploaded, so a download that does not reproduce both is
 * not that file -- cut off, corrupted, or swapped -- and is never handed
 * to the installer.
 */
data class AppRelease(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String?,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String
)

/**
 * Whether this build must be replaced before the worker may continue.
 *
 * Every published release is REQUIRED -- publishing also raises
 * `attendance_config.min_app_version`, so the server already refuses
 * Time In / Time Out from anything older. The dialog is the explanation
 * of a refusal that is going to happen anyway.
 */
object AppUpdatePolicy {

    /**
     * The release this device must install, or null when it is current.
     *
     * The SERVER's answer wins whenever there is one: it is what
     * [cached] was copied from, and a cache that disagrees with it is
     * stale. Only when the check itself failed does the cache speak --
     * that is what keeps the dialog up on a site with no signal after
     * the phone has already learned a new version exists.
     *
     * With no answer and no cache the result is null, never a guess: a
     * worker on a no-signal site must still be able to record attendance
     * offline, and blocking them over a check that could not run would
     * stop the one thing the app is for.
     */
    fun required(
        installedVersionCode: Int,
        fetched: Result<AppRelease?>,
        cached: AppRelease?
    ): AppRelease? {
        val known = if (fetched.isSuccess) fetched.getOrNull() else cached
        return known?.takeIf { it.versionCode > installedVersionCode }
    }
}

/** Why a download did not produce an installable file. */
enum class UpdateFailure {
    /** No signal, or the connection dropped part-way. */
    DownloadFailed,

    /** The bytes arrived but are not the file the office published. */
    FileDamaged
}

/** How a streamed download ended. */
sealed interface CopyResult {
    data object Verified : CopyResult
    data object WrongSize : CopyResult
    data object WrongHash : CopyResult
}

object ApkVerification {

    private const val BUFFER = 64 * 1024

    /**
     * Streams [input] into [output], hashing as it goes, and reports
     * whether the result is exactly [expectedSize] bytes hashing to
     * [expectedSha256] (lower-case hex).
     *
     * One pass, so a 5 MB APK is never held in memory on a 2 GB phone.
     * [onProgress] gets a fraction in 0..1.
     */
    fun copyVerifying(
        input: InputStream,
        output: OutputStream,
        expectedSize: Long,
        expectedSha256: String,
        onProgress: (Float) -> Unit = {}
    ): CopyResult {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        var copied = 0L
        var lastReported = -1

        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            digest.update(buffer, 0, read)
            copied += read

            // A read longer than promised is already wrong; stop pulling
            // bytes for a file that will be thrown away.
            if (copied > expectedSize) return CopyResult.WrongSize

            // Whole percents only. Recomposing the button on every 8 KB
            // read is wasted work on the phones this app is for.
            val percent = if (expectedSize > 0) ((copied * 100) / expectedSize).toInt() else 0
            if (percent != lastReported) {
                lastReported = percent
                onProgress(percent / 100f)
            }
        }
        output.flush()

        if (copied != expectedSize) return CopyResult.WrongSize
        return if (digest.digest().toHex() == expectedSha256.lowercase()) {
            CopyResult.Verified
        } else {
            CopyResult.WrongHash
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
}
