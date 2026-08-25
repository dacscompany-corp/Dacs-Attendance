package com.dacs.attendance.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the device had a usable connection at the moment of capture.
 *
 * This is what fills `attendance_records.was_offline`, and it is not
 * cosmetic: the admin views read `timein_received_at - timein_at` as
 * possible CLOCK TAMPERING, but that gap is legitimately hours long for
 * a record captured offline. Recording false for an offline capture
 * makes an honest worker look suspicious.
 *
 * Never shown to the worker -- section 6.3 is explicit that implying
 * suspicion to them would be wrong.
 */
@Singleton
class Connectivity @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isOnline(): Boolean {
        val manager = context.getSystemService<ConnectivityManager>() ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
