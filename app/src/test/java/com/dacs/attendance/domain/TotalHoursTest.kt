package com.dacs.attendance.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "9h 45m" — the one number a worker checks, and the one an admin will
 * be asked about. It is computed server-side into total_minutes; this
 * only formats it, so the worker and the admin cannot disagree.
 */
class TotalHoursTest {

    @Test
    fun `the MVP's own example formats as 9h 45m`() {
        // 07:45 -> 17:30 is 585 minutes. Straight from the MVP document.
        assertEquals("9h 45m", TotalHours.format(585))
    }

    @Test
    fun `a whole number of hours still shows the minutes`() {
        // "8h" alone reads like a rounded estimate. It is not one.
        assertEquals("8h 0m", TotalHours.format(480))
    }

    @Test
    fun `under an hour shows zero hours rather than minutes alone`() {
        assertEquals("0h 20m", TotalHours.format(20))
    }

    @Test
    fun `a shift past midnight keeps counting up, not around`() {
        // 25 hours is nonsense on a clock but correct as a total, and the
        // RPC allows a long shift up to its SHIFT_TOO_LONG guard.
        assertEquals("25h 5m", TotalHours.format(1505))
    }

    @Test
    fun `nothing recorded yet is a dash, not a zero`() {
        // A dash reads as "no answer"; "0h 0m" reads as "you worked
        // nothing", which is a different and wrong claim.
        assertEquals("--", TotalHours.format(null))
    }

    @Test
    fun `hours so far counts from time in to now`() {
        val timeIn = Instant.parse("2026-08-18T23:45:00Z")  // 07:45 Manila
        val now = Instant.parse("2026-08-19T02:30:00Z")     // 10:30 Manila
        assertEquals("2h 45m", TotalHours.since(timeIn, now))
    }

    @Test
    fun `a device clock behind the server never shows negative hours`() {
        // Cheap phones drift. "-0h 5m" on the dashboard would look like a
        // bug to the worker and be reported as one.
        val timeIn = Instant.parse("2026-08-19T02:30:00Z")
        val now = Instant.parse("2026-08-19T02:25:00Z")
        assertEquals("0h 0m", TotalHours.since(timeIn, now))
    }
}
