package com.dacs.attendance.domain

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The time the reward is judged on (0078). Every null here is a case where
 * guessing would put a number the phone cannot vouch for in front of the
 * ₱500 rule.
 */
class TrustedTimeTest {

    private val server = Instant.parse("2026-09-28T23:30:00Z") // 07:30 Manila
    private val anchor = ClockAnchor(serverTime = server, uptimeMillis = 1_000_000L, bootCount = 12)

    @Test
    fun `trusted time follows uptime, not the wall clock`() {
        // Forty minutes of uptime later. Whatever the worker set the clock
        // to, this is 08:10 Manila -- which is the whole point.
        val t = trustedTime(anchor, uptimeNowMillis = 1_000_000L + 40 * 60_000L, bootCountNow = 12)
        assertEquals(server.plus(Duration.ofMinutes(40)), t)
    }

    @Test
    fun `no anchor yet means no trusted time`() {
        assertNull(trustedTime(null, 5_000L, 12))
    }

    @Test
    fun `a reboot since the anchor voids it`() {
        // Rebooting is also the cheapest way to try to beat this, so it
        // must never produce a number.
        assertNull(trustedTime(anchor, uptimeNowMillis = 2_000_000L, bootCountNow = 13))
    }

    @Test
    fun `uptime running backwards is a reboot the boot count did not reveal`() {
        assertNull(trustedTime(anchor.copy(bootCount = null), uptimeNowMillis = 500L, bootCountNow = null))
    }

    @Test
    fun `an anchor older than a week is not paid on`() {
        val tooOld = 1_000_000L + MAX_ANCHOR_AGE.toMillis() + 1
        assertNull(trustedTime(anchor, tooOld, 12))
    }

    @Test
    fun `an unknown boot count falls back to the uptime check alone`() {
        val t = trustedTime(anchor.copy(bootCount = null), 1_060_000L, bootCountNow = null)
        assertEquals(server.plusSeconds(60), t)
    }

    @Test
    fun `the HTTP Date header parses to the server's instant`() {
        assertEquals(
            Instant.parse("2026-09-27T17:12:43Z"),
            parseServerDate("Sun, 27 Sep 2026 17:12:43 GMT")
        )
    }

    @Test
    fun `a missing or garbled Date header is no anchor, never a crash`() {
        assertNull(parseServerDate(null))
        assertNull(parseServerDate(""))
        assertNull(parseServerDate("yesterday-ish"))
    }
}
