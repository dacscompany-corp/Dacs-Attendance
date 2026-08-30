package com.dacs.attendance.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last Terms version this device saw the server confirm, per worker.
 *
 * This exists for one situation: the app is opened at 07:45 on a site
 * with no signal by a worker who accepted the Terms weeks ago. Without
 * it the gate cannot be answered and the worker cannot time in -- which
 * is the whole job. It is a cache of a server fact, never the source of
 * one: [StartupGate] discards it whenever the server actually answers.
 */
@Singleton
class TermsCache @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("dacs_attendance_terms", Context.MODE_PRIVATE)

    fun acceptedVersion(workerId: String): String? = prefs.getString(key(workerId), null)

    fun remember(workerId: String, version: String) {
        prefs.edit().putString(key(workerId), version).apply()
    }

    /**
     * WHEN the acceptance was recorded, for the Profile screen's
     * "Accepted 3 Aug 2026". Same reason as the version above: the
     * profile is opened on site, and a row that says only "Terms &
     * Conditions" because there is no signal tells the worker nothing.
     */
    fun acceptedAt(workerId: String): Instant? =
        prefs.getLong(dateKey(workerId), 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli)

    fun rememberAcceptedAt(workerId: String, at: Instant) {
        prefs.edit().putLong(dateKey(workerId), at.toEpochMilli()).apply()
    }

    private fun key(workerId: String) = "accepted_version_$workerId"
    private fun dateKey(workerId: String) = "accepted_at_$workerId"
}
