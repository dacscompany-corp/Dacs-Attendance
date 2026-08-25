package com.dacs.attendance.domain

import java.io.IOException
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the dashboard should show for today, given three inputs that can
 * disagree: what the server says, what the phone remembers, and whether
 * a submission is still queued.
 *
 * The trap is that "the server has no record" and "the server could not
 * be reached" are different facts that a nullable result collapses into
 * the same value.
 */
class TodayReconcileTest {

    private val cached = AttendanceRecord(
        id = "local", workDate = "2026-08-25", status = AttendanceStatus.WORKING,
        timeInAt = Instant.parse("2026-08-24T23:45:00Z"), timeOutAt = null,
        timeInProjectName = "ABC Building Project", timeOutProjectName = null,
        totalMinutes = null, pending = true
    )

    private val server = cached.copy(id = "server-row", pending = false)

    @Test
    fun `the server's row wins whenever the server answers`() {
        assertEquals(
            TodayDecision.Use(server),
            reconcileToday(Result.success(server), cached, hasPending = false)
        )
    }

    @Test
    fun `offline falls back to what the phone remembers`() {
        assertEquals(
            TodayDecision.Use(cached),
            reconcileToday(Result.failure(IOException("no signal")), cached, hasPending = true)
        )
    }

    @Test
    fun `a queued submission survives the server saying there is nothing yet`() {
        // The upload has not landed, so of course the server has no row.
        // Clearing the mirror here would erase a Time In the worker was
        // told was saved -- the single worst thing this app could do.
        assertEquals(
            TodayDecision.Use(cached),
            reconcileToday(Result.success(null), cached, hasPending = true)
        )
    }

    @Test
    fun `with nothing queued, the server saying no record clears a stale mirror`() {
        // The day was deleted or belongs to a previous device. Keeping a
        // stale "complete" would hide the TIME IN button all day.
        assertEquals(
            TodayDecision.Clear,
            reconcileToday(Result.success(null), cached, hasPending = false)
        )
    }

    @Test
    fun `offline with nothing cached is unknown, not empty`() {
        assertEquals(
            TodayDecision.Unknown,
            reconcileToday(Result.failure(IOException("no signal")), null, hasPending = false)
        )
    }

    @Test
    fun `no record anywhere and nothing queued really is an empty day`() {
        assertEquals(
            TodayDecision.Clear,
            reconcileToday(Result.success(null), null, hasPending = false)
        )
    }
}
