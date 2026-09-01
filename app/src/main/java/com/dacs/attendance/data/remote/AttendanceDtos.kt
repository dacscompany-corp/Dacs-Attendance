package com.dacs.attendance.data.remote

import com.dacs.attendance.domain.AttendanceProject
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.AttendanceStatus
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectRow(
    val id: Long,
    val name: String
) {
    fun toDomain() = AttendanceProject(id = id, name = name)

    companion object {
        const val COLUMNS = "id,name"
    }
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
