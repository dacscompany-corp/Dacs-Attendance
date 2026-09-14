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

    /**
     * Location refusals (0069). Only the ones where the location is
     * KNOWN BAD reach here -- a vague fix or no fix at all is recorded
     * and flagged, never refused, so it never becomes a failure a worker
     * has to read.
     */

    /** Demonstrably somewhere other than the project. */
    OutsideRadius,

    /** A fake GPS provider. Never accidental. */
    MockLocation,

    /**
     * The worker declined the location permission. The one refusal on
     * this list they can fix themselves -- and on Android that may mean
     * going to system settings, because a permanent denial cannot be
     * re-prompted.
     */
    LocationPermissionDenied,

    /**
     * Location is switched off on the phone itself, which is a different
     * thing from the app's permission and is fixed on a different
     * screen. Refused rather than flagged: a phone that CAN locate and
     * simply cannot get a fix still records, but a switch somebody
     * turned off is a decision, not weather.
     *
     * Raised by the device at the shutter, never by the server -- the
     * flow stops before anything is submitted.
     */
    LocationDisabled,

    /** No fence configured for this project, once fences are required. */
    ProjectGeofenceUnavailable,

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
            "AUTH_REQUIRED" to SessionExpired,
            // 0069 raises these as the upper-cased §33 result code.
            // PROJECT_GEOFENCE_UNAVAILABLE does NOT contain the substring
            // PROJECT_UNAVAILABLE, so the two cannot shadow each other
            // whatever order they sit in.
            "OUTSIDE_RADIUS" to OutsideRadius,
            "MOCK_LOCATION" to MockLocation,
            "PERMISSION_DENIED" to LocationPermissionDenied,
            // No server raises this today; the device does. Mapped anyway
            // so that if one ever learns to, the app already reads it.
            "LOCATION_DISABLED" to LocationDisabled,
            "PROJECT_GEOFENCE_UNAVAILABLE" to ProjectGeofenceUnavailable
        )
    }
}
