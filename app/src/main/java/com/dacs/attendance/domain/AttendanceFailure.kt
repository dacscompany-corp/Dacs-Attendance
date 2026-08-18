package com.dacs.attendance.domain

import java.io.IOException

/**
 * Every way a Time In or Time Out can be refused, as something a worker
 * on scaffolding can act on.
 *
 * The RPCs raise stable codes (0050 / 0051) precisely so this mapping can
 * exist. If a later migration adds a code, [Unexpected] catches it and
 * the test that enumerates the surface fails -- which is the point.
 */
enum class AttendanceFailure {
    /** Already timed in today. The commonest one: a second tap on a slow phone. */
    AlreadyTimedIn,

    /** Timing out with no open record for today. */
    NotTimedIn,

    /** Today is already finished. */
    AlreadyComplete,

    /** Time Out earlier than Time In -- a device clock problem, not a worker one. */
    TimeOutBeforeTimeIn,

    /** captured_at more than 2 minutes ahead of the server. */
    DeviceClockWrong,

    ShiftTooLong,

    /** The project is gone or was deactivated while the worker was in the flow. */
    ProjectUnavailable,

    /** profiles.owner_id is null -- an admin setup problem (see 0051). */
    NoOwnerAssigned,

    AccountInactive,
    NotAWorker,

    /** The session expired mid-flow; the worker must log in again. */
    SessionExpired,

    NoConnection,

    /** Anything we have no specific words for. Never guesses. */
    Unexpected;

    companion object {

        fun of(error: Throwable): AttendanceFailure {
            if (error is IOException) return NoConnection

            val text = (error.message ?: "") + " " + (error.cause?.message ?: "")
            return BY_CODE.entries.firstOrNull { (code, _) -> text.contains(code) }?.value
                ?: Unexpected
        }

        // EVENT_ID_CONFLICT and EVENT_ID_REQUIRED are deliberately absent:
        // both mean the app generated a bad event id, which is our bug,
        // not something to explain to a worker in Tagalog.
        private val BY_CODE = mapOf(
            "ALREADY_TIMED_IN" to AlreadyTimedIn,
            "NOT_TIMED_IN" to NotTimedIn,
            "ALREADY_COMPLETE" to AlreadyComplete,
            "TIMEOUT_BEFORE_TIMEIN" to TimeOutBeforeTimeIn,
            "CAPTURED_IN_FUTURE" to DeviceClockWrong,
            "SHIFT_TOO_LONG" to ShiftTooLong,
            "PROJECT_UNAVAILABLE" to ProjectUnavailable,
            "NO_OWNER_ASSIGNED" to NoOwnerAssigned,
            "ACCOUNT_INACTIVE" to AccountInactive,
            "NOT_A_WORKER" to NotAWorker,
            "AUTH_REQUIRED" to SessionExpired
        )
    }
}
