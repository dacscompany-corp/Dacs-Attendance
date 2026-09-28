package com.dacs.attendance.data.remote

import com.dacs.attendance.BuildConfig
import com.dacs.attendance.domain.AppRelease
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Public bucket (0079): the download has to work before sign-in. */
private const val RELEASE_BUCKET = "app-releases"

/** One row of `app_latest_release()` (0079). */
@Serializable
data class AppReleaseRow(
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("release_notes") val releaseNotes: String? = null,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    val sha256: String
) {
    fun toDomain() = AppRelease(
        versionCode = versionCode,
        versionName = versionName,
        releaseNotes = releaseNotes?.trim()?.takeIf { it.isNotEmpty() },
        downloadUrl = "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/$RELEASE_BUCKET/$storagePath",
        sizeBytes = sizeBytes,
        sha256 = sha256
    )
}
