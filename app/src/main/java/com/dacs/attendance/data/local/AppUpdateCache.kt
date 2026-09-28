package com.dacs.attendance.data.local

import android.content.Context
import com.dacs.attendance.domain.AppRelease
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last release this device was told it must install.
 *
 * Same role as [TermsCache]: a copy of a server fact for when the server
 * cannot be asked. A phone that learned about version 4 on the jeepney
 * keeps showing the update on a site with no signal, instead of quietly
 * letting the worker record attendance the server will refuse (0077).
 * [com.dacs.attendance.domain.AppUpdatePolicy] discards it whenever the
 * server actually answers.
 */
@Singleton
class AppUpdateCache @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("dacs_attendance_update", Context.MODE_PRIVATE)

    fun read(): AppRelease? {
        val code = prefs.getInt(KEY_CODE, 0).takeIf { it > 0 } ?: return null
        return AppRelease(
            versionCode = code,
            versionName = prefs.getString(KEY_NAME, null) ?: return null,
            releaseNotes = prefs.getString(KEY_NOTES, null),
            downloadUrl = prefs.getString(KEY_URL, null) ?: return null,
            sizeBytes = prefs.getLong(KEY_SIZE, 0L),
            sha256 = prefs.getString(KEY_SHA, null) ?: return null
        )
    }

    fun write(release: AppRelease?) {
        val edit = prefs.edit().clear()
        if (release != null) {
            edit.putInt(KEY_CODE, release.versionCode)
                .putString(KEY_NAME, release.versionName)
                .putString(KEY_NOTES, release.releaseNotes)
                .putString(KEY_URL, release.downloadUrl)
                .putLong(KEY_SIZE, release.sizeBytes)
                .putString(KEY_SHA, release.sha256)
        }
        edit.apply()
    }

    private companion object {
        const val KEY_CODE = "version_code"
        const val KEY_NAME = "version_name"
        const val KEY_NOTES = "release_notes"
        const val KEY_URL = "download_url"
        const val KEY_SIZE = "size_bytes"
        const val KEY_SHA = "sha256"
    }
}
