package com.dacs.attendance.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The two filters the design puts above the history list. */
enum class HistorySpan { WEEK, MONTH }

/** One row in the history list: a calendar day, worked or not. */
data class HistoryDay(
    val workDate: String,
    val date: LocalDate,
    val record: AttendanceRecord?
)

data class HistorySummary(
    val daysWorked: Int,
    val totalMinutes: Int
)

/**
 * The date range a filter covers.
 *
 * "This week" is Monday-to-today, NOT the last seven days: a worker
 * asking about this week means the working week they are standing in,
 * and a rolling window would put last Thursday under "this week" every
 * Wednesday. Same reasoning for the month.
 */
fun historyRange(span: HistorySpan, today: LocalDate = LocalDate.now(AttendanceZone)): ClosedRange<LocalDate> {
    val start = when (span) {
        HistorySpan.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        HistorySpan.MONTH -> today.withDayOfMonth(1)
    }
    return start..today
}

/**
 * Every day in the range, newest first, with its record if there is one.
 *
 * Days with NO record are included deliberately. The design shows them
 * as "Walang naitalang pasok", and they are the entire point of the
 * screen: a worker checking whether a day got recorded needs to SEE the
 * gap. A list built only from records would quietly skip it and answer
 * the opposite question.
 */
fun historyDays(
    range: ClosedRange<LocalDate>,
    records: List<AttendanceRecord>
): List<HistoryDay> {
    val byDate = records.associateBy { it.workDate }
    val days = mutableListOf<HistoryDay>()

    var day = range.endInclusive
    while (!day.isBefore(range.start)) {
        val key = day.toString()
        days += HistoryDay(workDate = key, date = day, record = byDate[key])
        day = day.minusDays(1)
    }
    return days
}

/**
 * Days worked and hours in the range.
 *
 * An open day (no Time Out yet) counts as a day worked but contributes
 * ZERO minutes: total_minutes is null until the server computes it, and
 * inventing a figure from the phone's clock would put a number on screen
 * the server has never agreed to.
 */
fun historySummary(days: List<HistoryDay>): HistorySummary {
    val worked = days.filter { it.record != null }
    return HistorySummary(
        daysWorked = worked.size,
        totalMinutes = worked.sumOf { it.record?.totalMinutes ?: 0 }
    )
}
