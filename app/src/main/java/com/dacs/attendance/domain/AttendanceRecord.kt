package com.dacs.attendance.domain

import java.time.Instant

/** A project a worker may record attendance against. */
data class AttendanceProject(
    val id: Long,
    val name: String
)

/**
 * The section 14 status machine, as the database spells it. `abandoned`
 * is set by an admin sweep, never by the app, but the app must render it
 * rather than crash on an unknown value.
 */
enum class AttendanceStatus {
    WORKING, COMPLETE, ABANDONED, UNKNOWN;

    companion object {
        fun parse(raw: String?): AttendanceStatus = when (raw?.lowercase()) {
            "working" -> WORKING
            "complete" -> COMPLETE
            "abandoned" -> ABANDONED
            else -> UNKNOWN
        }
    }
}

/**
 * Today's record, as the dashboard needs it. A subset of
 * attendance_records -- the app never needs the location columns back,
 * only the admin does.
 */
data class AttendanceRecord(
    val id: String,
    val workDate: String,
    val status: AttendanceStatus,
    val timeInAt: Instant?,
    val timeOutAt: Instant?,
    val timeInProjectName: String?,
    val timeOutProjectName: String?,
    val totalMinutes: Int?
) {
    /** What the worker may do next. The dashboard is built entirely from this. */
    val nextAction: TimeDirection?
        get() = when (status) {
            AttendanceStatus.WORKING -> TimeDirection.OUT
            // Complete or abandoned: the day is closed. One record per
            // worker per day is the rule the unique key enforces, so
            // offering TIME IN again would only produce ALREADY_TIMED_IN.
            else -> null
        }
}
