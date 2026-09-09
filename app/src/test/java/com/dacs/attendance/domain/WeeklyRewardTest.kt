package com.dacs.attendance.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The weekly reward strip, as the worker sees it mid-week.
 *
 * These tests fence the rules that were expensive to agree on and are
 * easy to lose: a Time Out never counts, a day that has not happened is
 * not a day that was missed, and a closed site shrinks the week instead
 * of failing it.
 *
 * They must stay in step with 0066's `attendance_week_days`. If the two
 * ever disagree, the reward a worker was shown all week is not the
 * reward they get.
 */
class WeeklyRewardTest {

    // 2026-09-07 is a Monday; the reward week runs to Friday the 11th.
    private val monday = LocalDate.of(2026, 9, 7)
    private val tuesday = LocalDate.of(2026, 9, 8)
    private val wednesday = LocalDate.of(2026, 9, 9)
    private val thursday = LocalDate.of(2026, 9, 10)
    private val friday = LocalDate.of(2026, 9, 11)

    private fun day(
        date: LocalDate,
        status: RewardDayStatus,
        required: Boolean = true
    ) = RewardDay(date, required, status)

    private fun fullWeek(vararg statuses: RewardDayStatus) =
        rewardWeekDates(monday).mapIndexed { i, d -> day(d, statuses[i]) }

    @Test
    fun `the week starts on Monday, from any day inside it`() {
        assertEquals(monday, rewardWeekStart(wednesday))
        assertEquals(monday, rewardWeekStart(monday))
        // The Sunday at the end of the week still belongs to it.
        assertEquals(monday, rewardWeekStart(LocalDate.of(2026, 9, 13)))
    }

    @Test
    fun `the reward week is five days, not the six the strip draws`() {
        val dates = rewardWeekDates(monday)

        assertEquals(5, dates.size)
        assertEquals(monday, dates.first())
        assertEquals(friday, dates.last())
    }

    @Test
    fun `a day later than today is pending, never missing`() {
        // On Wednesday, Thursday and Friday have not happened. Reading
        // the server's `missing` literally would tell every worker in
        // the company they were already disqualified.
        val summary = rewardSummary(
            fullWeek(
                RewardDayStatus.OnTime, RewardDayStatus.OnTime, RewardDayStatus.OnTime,
                RewardDayStatus.Missing, RewardDayStatus.Missing
            ),
            today = wednesday
        )

        assertEquals(2, summary.pendingDays)
        assertEquals(0, summary.missingDays)
        assertEquals(RewardStatus.InProgress, summary.status)
    }

    @Test
    fun `one late day settles the week immediately`() {
        val summary = rewardSummary(
            fullWeek(
                RewardDayStatus.OnTime, RewardDayStatus.Late, RewardDayStatus.OnTime,
                RewardDayStatus.Missing, RewardDayStatus.Missing
            ),
            today = wednesday
        )

        assertEquals(1, summary.lateDays)
        // Nothing left in the week could save it, and saying otherwise
        // until Friday would be a kinder lie rather than a kindness.
        assertEquals(RewardStatus.Disqualified, summary.status)
    }

    @Test
    fun `five on-time days qualify`() {
        val summary = rewardSummary(
            fullWeek(
                RewardDayStatus.OnTime, RewardDayStatus.OnTime, RewardDayStatus.OnTime,
                RewardDayStatus.OnTime, RewardDayStatus.OnTime
            ),
            today = friday
        )

        assertEquals(5, summary.requiredDays)
        assertEquals(5, summary.onTimeDays)
        assertEquals(RewardStatus.Qualified, summary.status)
    }

    @Test
    fun `a closed day shrinks the week, so four of four still qualifies`() {
        // The rule that stops a weekday holiday zeroing the reward for
        // every worker, eight to ten times a year, through no fault of
        // their own.
        val days = listOf(
            day(monday, RewardDayStatus.OnTime),
            day(tuesday, RewardDayStatus.OnTime),
            day(wednesday, RewardDayStatus.NotRequired, required = false),
            day(thursday, RewardDayStatus.OnTime),
            day(friday, RewardDayStatus.OnTime)
        )

        val summary = rewardSummary(days, today = friday)

        assertEquals(4, summary.requiredDays)
        assertEquals(4, summary.onTimeDays)
        assertEquals(0, summary.missingDays)
        assertEquals(RewardStatus.Qualified, summary.status)
    }

    @Test
    fun `a past day with no Time In disqualifies`() {
        val summary = rewardSummary(
            fullWeek(
                RewardDayStatus.OnTime, RewardDayStatus.Missing, RewardDayStatus.OnTime,
                RewardDayStatus.OnTime, RewardDayStatus.OnTime
            ),
            today = friday
        )

        assertEquals(1, summary.missingDays)
        assertEquals(RewardStatus.Disqualified, summary.status)
    }

    @Test
    fun `a whole week of closed days is not a free reward`() {
        val days = rewardWeekDates(monday).map {
            day(it, RewardDayStatus.NotRequired, required = false)
        }

        val summary = rewardSummary(days, today = friday)

        assertEquals(0, summary.requiredDays)
        // There was nothing to be on time for. 0066 takes the same view,
        // and the two must not disagree.
        assertEquals(RewardStatus.Disqualified, summary.status)
    }

    @Test
    fun `an unknown day status says in progress, never disqualified`() {
        // If a later migration adds an outcome this build has not heard
        // of, the honest answer is that the app cannot tell. Guessing
        // would show a worker a zero the database never agreed to.
        val summary = rewardSummary(
            fullWeek(
                RewardDayStatus.OnTime, RewardDayStatus.OnTime, RewardDayStatus.Unknown,
                RewardDayStatus.OnTime, RewardDayStatus.OnTime
            ),
            today = friday
        )

        assertEquals(RewardStatus.InProgress, summary.status)
    }

    @Test
    fun `an unrecognised status string parses to Unknown rather than throwing`() {
        assertEquals(RewardDayStatus.OnTime, RewardDayStatus.parse("on_time"))
        assertEquals(RewardDayStatus.NotRequired, RewardDayStatus.parse("not_required"))
        assertEquals(RewardDayStatus.Unknown, RewardDayStatus.parse("excused"))
        assertEquals(RewardDayStatus.Unknown, RewardDayStatus.parse(null))
    }

    @Test
    fun `the strip always draws five cells, even on a short answer`() {
        // A server that answered for only two days must not silently
        // lose three columns off the worker's week.
        val cells = rewardCells(
            weekStart = monday,
            days = listOf(
                day(monday, RewardDayStatus.OnTime),
                day(tuesday, RewardDayStatus.Late)
            ),
            today = wednesday
        )

        assertEquals(5, cells.size)
        assertEquals(listOf("Mo", "Tu", "We", "Th", "Fr"), cells.map { it.label })
        assertEquals(RewardCellState.OnTime, cells[0].state)
        assertEquals(RewardCellState.Late, cells[1].state)
        // Today, unrecorded -- PENDING, not missed. This assertion used to
        // say Missing, which is the bug it was written before: a worker
        // opening the app before the cutoff was shown a red day and a
        // disqualified week for a day they could still record.
        assertEquals(RewardCellState.Pending, cells[2].state)
        assertEquals(RewardCellState.Pending, cells[3].state)   // not yet
        assertEquals(RewardCellState.Pending, cells[4].state)
    }

    @Test
    fun `a closed day draws as not required, not as a miss`() {
        val cells = rewardCells(
            weekStart = monday,
            days = listOf(day(wednesday, RewardDayStatus.NotRequired, required = false)),
            today = friday
        )

        assertEquals(RewardCellState.NotRequired, cells[2].state)
    }

    @Test
    fun `today is marked, and only today`() {
        val cells = rewardCells(weekStart = monday, days = emptyList(), today = wednesday)

        assertEquals(1, cells.count { it.isToday })
        assertEquals(wednesday, cells.single { it.isToday }.date)
    }

    @Test
    fun `a late day still counts as a day worked`() {
        // completedDays reports attendance, not qualification. A worker
        // who turned up late was still there.
        val summary = rewardSummary(
            fullWeek(
                RewardDayStatus.Late, RewardDayStatus.OnTime, RewardDayStatus.OnTime,
                RewardDayStatus.OnTime, RewardDayStatus.OnTime
            ),
            today = friday
        )

        assertEquals(5, summary.completedDays)
        assertEquals(RewardStatus.Disqualified, summary.status)
    }

    @Test
    fun `today with no Time In yet is pending, not a miss`() {
        // A worker opening the app at 07:00, before the cutoff. Reporting
        // this as missed told them they were disqualified for a day they
        // still had two hours to record -- and there is no way for them to
        // tell that claim was premature rather than wrong.
        val summary = rewardSummary(
            fullWeek(
                RewardDayStatus.OnTime, RewardDayStatus.Missing, RewardDayStatus.Missing,
                RewardDayStatus.Missing, RewardDayStatus.Missing
            ),
            today = tuesday
        )

        assertEquals(0, summary.missingDays)
        assertEquals(4, summary.pendingDays)
        assertEquals(RewardStatus.InProgress, summary.status)
    }

    @Test
    fun `today draws as pending, and yesterday as missed`() {
        val cells = rewardCells(
            weekStart = monday,
            days = listOf(
                day(monday, RewardDayStatus.Missing),
                day(tuesday, RewardDayStatus.Missing)
            ),
            today = tuesday
        )

        assertEquals(RewardCellState.Missing, cells[0].state)
        assertEquals(RewardCellState.Pending, cells[1].state)
    }
}
