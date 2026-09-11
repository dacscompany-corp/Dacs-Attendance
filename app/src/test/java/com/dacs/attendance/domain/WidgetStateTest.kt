package com.dacs.attendance.domain

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The widget is a glance at the home screen, so what it claims has to be
 * exactly what the app would say -- and nothing about anyone else.
 */
class WidgetStateTest {

    private val today = WorkDate(LocalDate.of(2026, 9, 11))
    /** 8:52 AM in Manila. */
    private val timeIn = Instant.parse("2026-09-11T00:52:00Z")
    /** 5:10 PM in Manila. */
    private val timeOut = Instant.parse("2026-09-11T09:10:00Z")

    private fun record(
        status: AttendanceStatus,
        workDate: String = "2026-09-11",
        timeInAt: Instant? = timeIn,
        timeOutAt: Instant? = null
    ) = AttendanceRecord(
        id = "rec-1",
        workDate = workDate,
        status = status,
        timeInAt = timeInAt,
        timeOutAt = timeOutAt,
        timeInProjectName = "ABC Building Project",
        timeOutProjectName = null,
        totalMinutes = null
    )

    @Test
    fun `nobody signed in shows the sign-in state even when a record is lying around`() {
        val state = widgetStateFor(null, record(AttendanceStatus.WORKING), today, hasPending = true)

        assertEquals(WidgetState.SignedOut, state)
        assertNull(state.action)
    }

    @Test
    fun `a blank worker id is nobody`() {
        assertEquals(WidgetState.SignedOut, widgetStateFor("", null, today, hasPending = false))
    }

    @Test
    fun `no record today offers Time In`() {
        val state = widgetStateFor("w1", null, today, hasPending = false)

        assertEquals(WidgetState.NotTimedIn(notSentYet = false), state)
        assertEquals(TimeDirection.IN, state.action)
    }

    @Test
    fun `yesterday's record is not today's`() {
        // The mirror keeps past days for History. Showing yesterday's
        // "Done for today" on this morning's home screen would hide the
        // Time In button from a worker who has not timed in.
        val state = widgetStateFor(
            "w1",
            record(AttendanceStatus.COMPLETE, workDate = "2026-09-10", timeOutAt = timeOut),
            today,
            hasPending = false
        )

        assertEquals(WidgetState.NotTimedIn(notSentYet = false), state)
    }

    @Test
    fun `an open day offers Time Out`() {
        val state = widgetStateFor("w1", record(AttendanceStatus.WORKING), today, hasPending = false)

        assertEquals(WidgetState.Working(timeInAt = timeIn, notSentYet = false), state)
        assertEquals(TimeDirection.OUT, state.action)
    }

    @Test
    fun `a finished day offers nothing`() {
        val state = widgetStateFor(
            "w1",
            record(AttendanceStatus.COMPLETE, timeOutAt = timeOut),
            today,
            hasPending = false
        )

        assertEquals(WidgetState.Complete(timeIn, timeOut, notSentYet = false), state)
        assertNull(state.action)
    }

    @Test
    fun `an abandoned day offers nothing`() {
        val state = widgetStateFor("w1", record(AttendanceStatus.ABANDONED), today, hasPending = false)

        assertEquals(WidgetState.Abandoned(notSentYet = false), state)
        assertNull(state.action)
    }

    @Test
    fun `a status this build does not know offers nothing rather than guessing`() {
        val state = widgetStateFor("w1", record(AttendanceStatus.UNKNOWN), today, hasPending = false)

        assertEquals(WidgetState.Unknown(notSentYet = false), state)
        assertNull(state.action)
    }

    @Test
    fun `a queued submission is flagged not sent yet`() {
        val state = widgetStateFor("w1", record(AttendanceStatus.WORKING), today, hasPending = true)

        assertEquals(WidgetState.Working(timeInAt = timeIn, notSentYet = true), state)
    }

    @Test
    fun `a queued row with no record today is still flagged`() {
        assertEquals(
            WidgetState.NotTimedIn(notSentYet = true),
            widgetStateFor("w1", null, today, hasPending = true)
        )
    }
}
