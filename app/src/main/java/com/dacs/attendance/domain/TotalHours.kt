package com.dacs.attendance.domain

import java.time.Duration
import java.time.Instant

/**
 * Hours, as the worker reads them.
 *
 * The authoritative total is computed once, server-side, into
 * `attendance_records.total_minutes`. This only formats it -- so the
 * number on the worker's phone and the number in the admin's report
 * cannot disagree, which is the entire reason the RPC computes it.
 */
object TotalHours {

    /** "9h 45m". Null minutes means nothing is recorded yet. */
    fun format(minutes: Int?): String {
        if (minutes == null) return NOTHING_YET
        val safe = minutes.coerceAtLeast(0)
        return "${safe / 60}h ${safe % 60}m"
    }

    /**
     * The live "HOURS SO FAR" on the working dashboard.
     *
     * Clamped at zero: cheap phones drift, and a device clock a few
     * minutes behind the server would otherwise render "-0h 5m", which a
     * worker reads as a broken app and reports as one.
     */
    fun since(timeIn: Instant, now: Instant): String =
        format(Duration.between(timeIn, now).toMinutes().coerceAtLeast(0).toInt())

    const val NOTHING_YET = "--"
}
