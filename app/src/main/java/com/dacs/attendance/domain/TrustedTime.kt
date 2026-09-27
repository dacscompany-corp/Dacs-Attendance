package com.dacs.attendance.domain

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * A time the worker cannot move by changing the phone's clock.
 *
 * ── WHY IT EXISTS. The weekly reward judges lateness, and `captured_at`
 *    is the phone's wall clock. Found 2026-09-27: set the clock back,
 *    switch on airplane mode, Time In, reconnect -- the day arrived "on
 *    time", because an honest offline queue ALSO sends a time in the past
 *    and the server cannot tell the two apart. Migration 0078 now judges
 *    the reward on this value instead (`p_trusted_at`).
 *
 * ── HOW. Whenever the server answers, its clock (the HTTP `Date` header)
 *    is written down next to the phone's UPTIME counter
 *    (`SystemClock.elapsedRealtime`) at that moment. Uptime counts
 *    forward from boot and nothing in Settings can change it. So at the
 *    shutter:
 *
 *        trusted = serverTimeAtAnchor + (uptimeNow - uptimeAtAnchor)
 *
 *    ...which is correct whatever the wall clock says, offline for days.
 *
 * ── WHEN IT REFUSES TO ANSWER (returns null, never a guess):
 *    - no anchor yet (fresh install that never reached the server);
 *    - the phone rebooted since the anchor -- uptime restarted from zero,
 *      so the subtraction means nothing. Detected by the boot count, and
 *      by uptime going backwards when the boot count is unavailable;
 *    - the anchor is older than [MAX_ANCHOR_AGE]. Uptime drift is tiny,
 *      but an answer built on a week-old reading is not one to pay on.
 *
 *    A null is not a penalty by itself: the server then falls back to the
 *    phone's time IF it arrived within 3 minutes (0078), which is every
 *    live Time In. Only an offline Time In with no trusted time becomes
 *    "unverified" for the reward.
 *
 * ── WHAT IT CANNOT DO. A modified APK can send anything. This closes the
 *    trick an ordinary worker can do from the Settings app.
 */
data class ClockAnchor(
    /** The server's clock when it answered. */
    val serverTime: Instant,
    /** `SystemClock.elapsedRealtime()` when that answer arrived. */
    val uptimeMillis: Long,
    /** `Settings.Global.BOOT_COUNT` then, or null if the phone does not say. */
    val bootCount: Int?
)

/** Past this, an anchor is not trusted (see [ClockAnchor]'s file doc). */
val MAX_ANCHOR_AGE: Duration = Duration.ofDays(7)

/**
 * The trusted time now, or null when it cannot be vouched for. Pure: the
 * caller supplies the uptime and boot count, so this is testable off-device.
 */
fun trustedTime(
    anchor: ClockAnchor?,
    uptimeNowMillis: Long,
    bootCountNow: Int?
): Instant? {
    if (anchor == null) return null

    // Rebooted since the anchor: uptime restarted, the anchor is void.
    if (anchor.bootCount != null && bootCountNow != null && anchor.bootCount != bootCountNow) {
        return null
    }
    val elapsed = uptimeNowMillis - anchor.uptimeMillis
    // Uptime never runs backwards within one boot. If it has, it was a
    // reboot the boot count did not reveal.
    if (elapsed < 0) return null
    if (elapsed > MAX_ANCHOR_AGE.toMillis()) return null

    return anchor.serverTime.plusMillis(elapsed)
}

/**
 * The server clock from an HTTP `Date` header ("Sun, 27 Sep 2026 17:12:43 GMT"),
 * or null when it is absent or unreadable. Whole seconds only -- the error
 * that adds is far below the minutes the reward cares about.
 */
fun parseServerDate(header: String?): Instant? {
    if (header.isNullOrBlank()) return null
    return runCatching {
        ZonedDateTime.parse(header.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
    }.getOrNull()
}

/** What the time flow asks at the shutter. A seam so tests need no device. */
fun interface TrustedClock {
    fun now(): Instant?
}
