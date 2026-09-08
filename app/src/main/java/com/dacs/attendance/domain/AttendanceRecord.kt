package com.dacs.attendance.domain

import java.time.Instant

/**
 * WHICH project list a project came from.
 *
 * Attendance keeps no project list of its own. Migration 0059 retired
 * `attendance_projects` and pointed attendance at the two lists the
 * business already runs, whose ids live in different tables and could
 * collide:
 *
 *   PC -> folders               (Project Control)
 *   PM -> construction_projects (Project Management)
 *
 * So a project is identified by the PAIR, never by the id alone. Passing
 * an id without its system is the one mistake this type exists to stop.
 */
enum class ProjectSystem(val wire: String) {
    PC("pc"),
    PM("pm");

    companion object {
        /** Null for anything the server has not taught us about yet. */
        fun of(wire: String?): ProjectSystem? =
            entries.firstOrNull { it.wire == wire?.lowercase() }
    }
}

/** A project a worker may record attendance against. */
data class AttendanceProject(
    val system: ProjectSystem,
    /** A uuid since 0059. It is only unique WITHIN [system]. */
    val id: String,
    val name: String,
    /**
     * The site's fence, as the device last cached it.
     *
     * Null means the phone has never seen one, NOT that the project has
     * none: the server keeps fences effective-dated and re-checks against
     * whichever was in force at capture. A null here simply means the
     * device cannot pre-judge the radius and leaves it to the server.
     */
    val geofence: Geofence? = null
) {
    /**
     * The pair as one string, for list keys and equality checks.
     *
     * Never send this to the server -- the RPCs take the system and the
     * id as separate arguments.
     */
    val key: String get() = "${system.wire}:$id"
}

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
    val totalMinutes: Int?,
    /**
     * Storage paths for the two photos, when the server sent them.
     * Null off the local mirror -- which is correct: with no signal
     * there is nothing to fetch anyway, and History falls back to the
     * plain colour bar rather than an empty grey square.
     */
    val timeInPhotoPath: String? = null,
    val timeOutPhotoPath: String? = null,
    /**
     * True while this day is still only on the phone. Shown to the
     * worker as "will sync" so they know it is safe to walk away --
     * NOT as a warning, because there is nothing for them to fix.
     */
    val pending: Boolean = false
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
