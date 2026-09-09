package com.dacs.attendance.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * The weekly attendance reward, as the worker's own screen shows it.
 *
 * ── WHAT THIS DECIDES, AND WHAT IT DOES NOT.
 *    Nothing here is authoritative. The reward that gets paid is frozen
 *    server-side by `attendance_evaluate_week` after the week ends and
 *    its grace period has passed (migration 0066). This file renders the
 *    week the worker is standing in, from the day rows the server hands
 *    back, so they can see where they are before it is settled.
 *
 *    Both must agree, which is why the rules below are written to match
 *    0066's `attendance_week_days` exactly rather than being re-derived.
 *
 * ── WHY THERE IS NO MENTION OF TIME OUT ANYWHERE IN THIS FILE.
 *    Qualification reads the Time In and nothing else. A forgotten Time
 *    Out, an open day, a day an admin closed with `attendance_abandon`
 *    -- none of it can move the reward. Workers are still required to
 *    Time Out; it is simply not what this measures. Adding a Time Out
 *    check here would put an admin's cleanup work in charge of someone's
 *    money.
 *
 * ── FIVE DAYS, NOT SIX. The reward week is Monday to Friday, while
 *    [weekStrip] in History.kt deliberately draws SIX cells because DACs
 *    works Saturdays. Those are two different true things, and they get
 *    two different rows on screen. Folding the reward into the six-day
 *    strip would tell a worker that missing Saturday cost them ₱500.
 */

/** A day's outcome, exactly as `attendance_week_days` reports it. */
enum class RewardDayStatus {
    OnTime, Late, Missing, NotRequired,

    /**
     * Something the server has taught itself since this build shipped.
     * Kept rather than crashing, and deliberately NOT folded into
     * [Missing] -- see [rewardSummary].
     */
    Unknown;

    companion object {
        fun parse(raw: String?): RewardDayStatus = when (raw?.lowercase()) {
            "on_time" -> OnTime
            "late" -> Late
            "missing" -> Missing
            "not_required" -> NotRequired
            else -> Unknown
        }
    }
}

/** §43's three statuses. */
enum class RewardStatus { InProgress, Qualified, Disqualified }

/** One row from `attendance_reward_progress`. */
data class RewardDay(
    val date: LocalDate,
    val required: Boolean,
    val status: RewardDayStatus
)

/** How a single cell of the week strip should be drawn. */
enum class RewardCellState {
    OnTime, Late, Missing, NotRequired,

    /** Later this week. Nothing is wrong yet, and it must not look as if it is. */
    Pending
}

data class RewardCell(
    val date: LocalDate,
    /** Two letters, matching the History strip: Mo Tu We Th Fr. */
    val label: String,
    val state: RewardCellState,
    val isToday: Boolean
)

data class RewardSummary(
    val requiredDays: Int,
    val onTimeDays: Int,
    val lateDays: Int,
    val missingDays: Int,
    /** Required days still ahead of the worker. Never counted as missed. */
    val pendingDays: Int,
    /** Days with a Time In, whether on time or late. Reporting only. */
    val completedDays: Int,
    val status: RewardStatus
)

/** The Monday of the week [date] falls in. */
fun rewardWeekStart(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Monday to Friday of that week, in order. */
fun rewardWeekDates(weekStart: LocalDate): List<LocalDate> =
    (0L until 5L).map { weekStart.plusDays(it) }

/**
 * The week's standing, from the server's day rows.
 *
 * ── A DAY LATER THAN TODAY IS PENDING, NEVER MISSING. The server
 *    returns all five days whether or not they have happened, and a day
 *    with no Time In reads as `missing` in that data. Taking it at face
 *    value on a Wednesday would report Thursday and Friday as missed and
 *    tell every worker in the company they were already disqualified.
 *
 * ── DISQUALIFICATION IS REPORTED AS SOON AS IT IS CERTAIN. One late day
 *    settles the week; there is no arithmetic left that could save it,
 *    and pretending otherwise until Friday would be a kinder lie rather
 *    than a kindness.
 *
 * ── AN UNKNOWN STATUS YIELDS [RewardStatus.InProgress], not
 *    [RewardStatus.Disqualified]. If a later migration adds a day
 *    outcome this build has never heard of, the honest answer is "this
 *    app cannot tell yet" -- the server's frozen record decides in the
 *    end. Guessing "disqualified" would show a worker a ₱0 the database
 *    never agreed to.
 */
fun rewardSummary(days: List<RewardDay>, today: LocalDate): RewardSummary {
    var required = 0
    var onTime = 0
    var late = 0
    var missing = 0
    var pending = 0
    var completed = 0
    var unknown = 0

    days.forEach { day ->
        if (day.status == RewardDayStatus.OnTime || day.status == RewardDayStatus.Late) completed++
        if (!day.required) return@forEach

        required++
        when (day.status) {
            RewardDayStatus.OnTime -> onTime++
            RewardDayStatus.Late -> late++
            RewardDayStatus.Unknown -> unknown++
            // A required day the server calls not_required is a
            // contradiction; count it with the ones we cannot read
            // rather than silently treating it as a pass.
            RewardDayStatus.NotRequired -> unknown++
            // TODAY counts as pending, not missed. The server reports a
            // day with no Time In as `missing` whether or not it has
            // finished, and taking that at face value told a worker
            // opening the app at 07:00 -- two hours before the cutoff --
            // that they were already disqualified for a day they had not
            // yet had the chance to record.
            //
            // Only a day strictly in the PAST can be a miss. A day still
            // running is a day still winnable, even late in it.
            RewardDayStatus.Missing -> if (day.date.isBefore(today)) missing++ else pending++
        }
    }

    val status = when {
        late > 0 || missing > 0 -> RewardStatus.Disqualified
        unknown > 0 || pending > 0 -> RewardStatus.InProgress
        required > 0 -> RewardStatus.Qualified
        // Every day of the week was closed. There was nothing to be on
        // time for, so there is nothing to reward -- 0066 takes the same
        // view, and the two must not disagree.
        else -> RewardStatus.Disqualified
    }

    return RewardSummary(
        requiredDays = required,
        onTimeDays = onTime,
        lateDays = late,
        missingDays = missing,
        pendingDays = pending,
        completedDays = completed,
        status = status
    )
}

/**
 * The five cells of the reward strip.
 *
 * Built from [rewardWeekDates] rather than from [days], so a week the
 * server answered incompletely still draws five columns instead of
 * silently losing a day. A date with no row is treated as required and
 * unrecorded, which is what the server would have said.
 */
fun rewardCells(
    weekStart: LocalDate,
    days: List<RewardDay>,
    today: LocalDate = LocalDate.now(AttendanceZone)
): List<RewardCell> {
    val byDate = days.associateBy { it.date }

    return rewardWeekDates(weekStart).map { date ->
        val day = byDate[date]
        val state = when {
            day != null && !day.required -> RewardCellState.NotRequired
            day?.status == RewardDayStatus.OnTime -> RewardCellState.OnTime
            day?.status == RewardDayStatus.Late -> RewardCellState.Late
            // Same rule as the summary: today is still running, so an
            // empty cell for it is pending rather than a red miss.
            !date.isBefore(today) -> RewardCellState.Pending
            else -> RewardCellState.Missing
        }
        RewardCell(
            date = date,
            label = date.dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                .take(2),
            state = state,
            isToday = date == today
        )
    }
}
