package com.dacs.attendance.data.local

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.dacs.attendance.domain.ClockAnchor
import com.dacs.attendance.domain.TrustedClock
import com.dacs.attendance.domain.parseServerDate
import com.dacs.attendance.domain.trustedTime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS = "dacs_clock_anchor"
private const val KEY_SERVER = "server_ms"
private const val KEY_UPTIME = "uptime_ms"
private const val KEY_BOOT = "boot_count"

/**
 * Where the last server clock reading lives, and the Android half of
 * [trustedTime]. See TrustedTime.kt for why any of this exists.
 *
 * Plain SharedPreferences, not the encrypted session store: nothing here is
 * secret, and a keystore hiccup must never cost the anchor (the session
 * store's history in SupabaseModule is the cautionary tale).
 */
@Singleton
class ClockAnchorStore @Inject constructor(
    @ApplicationContext private val context: Context
) : TrustedClock {

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    /**
     * Called for every server response. The newest reading wins -- the
     * fresher the anchor, the less the uptime subtraction has to carry.
     */
    fun recordServerDate(dateHeader: String?) {
        val server = parseServerDate(dateHeader) ?: return
        prefs.edit()
            .putLong(KEY_SERVER, server.toEpochMilli())
            .putLong(KEY_UPTIME, SystemClock.elapsedRealtime())
            .putInt(KEY_BOOT, bootCount() ?: -1)
            .apply()
    }

    override fun now(): Instant? = trustedTime(
        anchor = readAnchor(),
        uptimeNowMillis = SystemClock.elapsedRealtime(),
        bootCountNow = bootCount()
    )

    private fun readAnchor(): ClockAnchor? {
        if (!prefs.contains(KEY_SERVER)) return null
        return ClockAnchor(
            serverTime = Instant.ofEpochMilli(prefs.getLong(KEY_SERVER, 0L)),
            uptimeMillis = prefs.getLong(KEY_UPTIME, 0L),
            bootCount = prefs.getInt(KEY_BOOT, -1).takeIf { it >= 0 }
        )
    }

    /** Global since API 24 (our minSdk); some OEM builds still omit it. */
    private fun bootCount(): Int? = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrNull()
}
