package com.dacs.attendance.domain

import java.time.Instant

/**
 * What the home-screen widget shows.
 *
 * Deliberately says nothing about WHO: no name, no project, no photo. A
 * site phone lies on a table in front of everyone, and the widget is
 * readable without unlocking it.
 */
sealed interface WidgetState {

    /** This worker still has a submission queued on the phone. */
    val notSentYet: Boolean

    data object SignedOut : WidgetState {
        override val notSentYet: Boolean = false
    }

    data class NotTimedIn(override val notSentYet: Boolean = false) : WidgetState

    /** [timeInAt] is null only for a mirror row that lost it; the line then omits the time. */
    data class Working(
        val timeInAt: Instant?,
        override val notSentYet: Boolean = false
    ) : WidgetState

    data class Complete(
        val timeInAt: Instant?,
        val timeOutAt: Instant?,
        override val notSentYet: Boolean = false
    ) : WidgetState

    data class Abandoned(override val notSentYet: Boolean = false) : WidgetState

    /**
     * A status this build cannot name, or a read that failed. No button:
     * guessing IN or OUT here is how a worker ends up with a photo the
     * server refuses.
     */
    data class Unknown(override val notSentYet: Boolean = false) : WidgetState

    /** The one button the widget offers, or null for none. Same rule as Home's. */
    val action: TimeDirection?
        get() = when (this) {
            is NotTimedIn -> TimeDirection.IN
            is Working -> TimeDirection.OUT
            else -> null
        }
}

/**
 * Today's widget, from what the phone already knows.
 *
 * [record] must already be scoped to [workerId] -- the caller reads it
 * with that id. The date is checked here because the mirror keeps past
 * days, and yesterday's "done" must not hide this morning's Time In.
 */
fun widgetStateFor(
    workerId: String?,
    record: AttendanceRecord?,
    today: WorkDate,
    hasPending: Boolean
): WidgetState {
    if (workerId.isNullOrBlank()) return WidgetState.SignedOut

    val todays = record?.takeIf { it.workDate == today.toString() }
        ?: return WidgetState.NotTimedIn(hasPending)

    return when (todays.status) {
        AttendanceStatus.WORKING -> WidgetState.Working(todays.timeInAt, hasPending)
        AttendanceStatus.COMPLETE ->
            WidgetState.Complete(todays.timeInAt, todays.timeOutAt, hasPending)
        AttendanceStatus.ABANDONED -> WidgetState.Abandoned(hasPending)
        AttendanceStatus.UNKNOWN -> WidgetState.Unknown(hasPending)
    }
}
