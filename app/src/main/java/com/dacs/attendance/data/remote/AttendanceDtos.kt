package com.dacs.attendance.data.remote

import com.dacs.attendance.domain.AttendanceProject
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.ProjectSystem
import com.dacs.attendance.domain.RewardDay
import com.dacs.attendance.domain.RewardDayStatus
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of `attendance_projects_for_worker()`.
 *
 * The column names are the function's own, and they are deliberately not
 * `system` / `name`: 0059 renamed them because `name` is a built-in type
 * and both would have shadowed the columns the function body selects.
 *
 * This is an RPC, not a table read, and that is a SECURITY decision made
 * on the server: workers have no select on `folders`, because the money
 * hanging off it (budgets, contract values) is owner-confidential. A
 * function returning three columns cannot leak a fourth.
 */
@Serializable
data class ProjectRow(
    @SerialName("project_system") val system: String,
    @SerialName("project_id") val id: String,
    @SerialName("project_name") val name: String
) {
    /**
     * Null when the server names a project system this build does not
     * know. Dropping the row is the safe reading: a project we cannot
     * label cannot be submitted either, and showing it would offer the
     * worker a choice that fails four screens later.
     */
    fun toDomain(): AttendanceProject? =
        ProjectSystem.of(system)?.let { AttendanceProject(system = it, id = id, name = name) }
}

/**
 * What the RPCs return: the whole attendance_records row. Only the
 * columns the app renders are declared -- `ignoreUnknownKeys` handles the
 * rest, including the location columns, which are admin-only by design.
 */
@Serializable
data class AttendanceRecordRow(
    val id: String,
    @SerialName("work_date") val workDate: String,
    val status: String? = null,
    @SerialName("timein_at") val timeInAt: String? = null,
    @SerialName("timeout_at") val timeOutAt: String? = null,
    @SerialName("timein_project_name") val timeInProjectName: String? = null,
    @SerialName("timeout_project_name") val timeOutProjectName: String? = null,
    @SerialName("total_minutes") val totalMinutes: Int? = null,
    // Storage object PATHS, never URLs. A path is stable and cheap to
    // carry; the signed URL that actually fetches bytes is minted only
    // when a row is on screen.
    @SerialName("timein_photo_path") val timeInPhotoPath: String? = null,
    @SerialName("timeout_photo_path") val timeOutPhotoPath: String? = null
) {
    fun toDomain() = AttendanceRecord(
        id = id,
        workDate = workDate,
        status = AttendanceStatus.parse(status),
        timeInAt = timeInAt?.toInstantOrNull(),
        timeOutAt = timeOutAt?.toInstantOrNull(),
        timeInProjectName = timeInProjectName,
        timeOutProjectName = timeOutProjectName,
        totalMinutes = totalMinutes,
        timeInPhotoPath = timeInPhotoPath,
        timeOutPhotoPath = timeOutPhotoPath
    )

    companion object {
        const val COLUMNS =
            "id,work_date,status,timein_at,timeout_at," +
                "timein_project_name,timeout_project_name,total_minutes," +
                "timein_photo_path,timeout_photo_path"
    }
}

/**
 * Postgres renders timestamptz as "2026-08-19T07:45:00.123456+08:00",
 * which Instant.parse cannot read directly -- it wants an offset it can
 * resolve, and the fractional seconds run to microseconds.
 * OffsetDateTime handles both.
 */
internal fun String.toInstantOrNull(): Instant? = runCatching {
    java.time.OffsetDateTime.parse(this).toInstant()
}.getOrElse {
    runCatching { Instant.parse(this) }.getOrNull()
}

/**
 * One row of `attendance_reward_progress` (migration 0066).
 *
 * `start_time` is the cutoff the SERVER compared against, carried so the
 * screen can say "due 09:00" without a second round trip and without the
 * app deciding lateness for itself. Whether the worker was late is
 * [dayStatus], and that judgement is the server's alone -- 0066 and
 * WeeklyReward.kt are written to agree, and the app re-deriving it would
 * be a second, drifting copy of the rule.
 */
@Serializable
data class RewardDayRow(
    @SerialName("work_date") val workDate: String,
    val required: Boolean,
    @SerialName("timein_at") val timeInAt: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("day_status") val dayStatus: String? = null
) {
    /**
     * Null when the work date is unreadable. Dropping the row is the
     * safe reading, the same call [ProjectRow.toDomain] makes: a day the
     * app cannot place on the calendar cannot be drawn in the right cell
     * either, and [rewardCells] fills the gap as unrecorded rather than
     * shifting the rest of the week along by one.
     */
    fun toDomain(): RewardDay? = runCatching {
        RewardDay(
            date = java.time.LocalDate.parse(workDate),
            required = required,
            status = RewardDayStatus.parse(dayStatus)
        )
    }.getOrNull()
}

/**
 * `attendance_config` (migration 0065), read straight off the table --
 * workers hold a select policy on their own owner's row.
 *
 * The amount is READ, never assumed. ₱500 is the MVP default and it is
 * configurable per owner, so a constant in the app would be a second
 * copy of a business value the database owns, silently wrong the first
 * time anybody changes it.
 */
@Serializable
data class RewardConfigRow(
    @SerialName("reward_amount") val rewardAmount: Double? = null
)
