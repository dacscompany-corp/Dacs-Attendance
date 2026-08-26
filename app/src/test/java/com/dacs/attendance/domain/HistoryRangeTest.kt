package com.dacs.attendance.domain

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Screen 10's history list.
 *
 * The design shows days with NO record ("Walang naitalang pasok") sitting
 * between the days that have one. That is the whole value of the screen:
 * a worker checking whether a day was recorded needs to see the gap, not
 * a list that quietly skips it.
 */
class HistoryRangeTest {

    private val today = LocalDate.of(2026, 8, 26)   // a Wednesday

    @Test
    fun `this week runs Monday to today, not seven days back`() {
        // A worker asking "this week" means the working week they are in.
        val range = historyRange(HistorySpan.WEEK, today)

        assertEquals(LocalDate.of(2026, 8, 24), range.start)  // Monday
        assertEquals(today, range.endInclusive)
    }

    @Test
    fun `this month runs from the first, not thirty days back`() {
        val range = historyRange(HistorySpan.MONTH, today)

        assertEquals(LocalDate.of(2026, 8, 1), range.start)
        assertEquals(today, range.endInclusive)
    }

    @Test
    fun `a day with no record still appears, newest first`() {
        val worked = AttendanceRecord(
            id = "r1", workDate = "2026-08-25", status = AttendanceStatus.COMPLETE,
            timeInAt = Instant.parse("2026-08-24T23:45:00Z"),
            timeOutAt = Instant.parse("2026-08-25T09:30:00Z"),
            timeInProjectName = "ABC Building Project",
            timeOutProjectName = "ABC Building Project", totalMinutes = 585
        )

        val days = historyDays(
            range = historyRange(HistorySpan.WEEK, today),
            records = listOf(worked)
        )

        // Mon 24, Tue 25, Wed 26 -> newest first
        assertEquals(listOf("2026-08-26", "2026-08-25", "2026-08-24"), days.map { it.workDate })
        assertEquals(null, days[0].record)                 // today, nothing yet
        assertEquals(worked, days[1].record)               // yesterday, worked
        assertEquals(null, days[2].record)                 // Monday, absent
    }

    @Test
    fun `a record outside the range is not shown`() {
        // Stale rows from the mirror must not leak into "this week".
        val old = AttendanceRecord(
            id = "old", workDate = "2026-08-01", status = AttendanceStatus.COMPLETE,
            timeInAt = null, timeOutAt = null, timeInProjectName = null,
            timeOutProjectName = null, totalMinutes = 480
        )

        val days = historyDays(historyRange(HistorySpan.WEEK, today), listOf(old))

        assertEquals(3, days.size)
        assertTrue(days.all { it.record == null })
    }

    @Test
    fun `the totals summarise only the days that were worked`() {
        val a = AttendanceRecord("a", "2026-08-25", AttendanceStatus.COMPLETE,
            null, null, null, null, 585)
        val b = AttendanceRecord("b", "2026-08-24", AttendanceStatus.COMPLETE,
            null, null, null, null, 480)

        val summary = historySummary(historyDays(historyRange(HistorySpan.WEEK, today), listOf(a, b)))

        assertEquals(2, summary.daysWorked)
        assertEquals(1065, summary.totalMinutes)
        assertEquals("17h 45m", TotalHours.format(summary.totalMinutes))
    }

    @Test
    fun `a still-open day contributes no hours to the total`() {
        // total_minutes is null until Time Out. Counting it as zero is
        // right; inventing a number from the clock would put a figure on
        // the screen that the server has never agreed to.
        val open = AttendanceRecord("a", "2026-08-26", AttendanceStatus.WORKING,
            Instant.parse("2026-08-25T23:45:00Z"), null, "ABC", null, null)

        val summary = historySummary(historyDays(historyRange(HistorySpan.WEEK, today), listOf(open)))

        assertEquals(1, summary.daysWorked)
        assertEquals(0, summary.totalMinutes)
    }
}
