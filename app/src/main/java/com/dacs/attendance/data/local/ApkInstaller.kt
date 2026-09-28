package com.dacs.attendance.data.local

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ApkInstaller"
private const val APK_MIME = "application/vnd.android.package-archive"

/** Hands a verified APK to Android. An interface so the dialog's logic tests without a device. */
interface InstallGateway {

    /**
     * Whether Android will let this app open its installer.
     *
     * The "Install unknown apps" switch is per-app from Android 8. On 7
     * and below it is one global switch this app cannot read reliably, so
     * the answer is yes and the system installer asks the worker itself.
     */
    fun canInstall(): Boolean

    /** Opens the "Install unknown apps" switch for THIS app. */
    fun openInstallPermission()

    fun install(apk: File)
}

@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) : InstallGateway {

    override fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    override fun openInstallPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val exact = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(exact)
        } catch (e: ActivityNotFoundException) {
            // Some vendor ROMs drop the per-app screen; the list is
            // one tap further but it is there.
            Log.w(TAG, "No per-app unknown-sources screen, opening the list", e)
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
