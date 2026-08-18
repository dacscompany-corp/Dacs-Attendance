package com.dacs.attendance.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Manila. Not the device's zone -- see [WorkDate]. */
val AttendanceZone: ZoneId = ZoneId.of("Asia/Manila")

/**
 * The calendar day an attendance record belongs to, derived exactly as
 * attendance_time_in derives it:
 *
 *     (captured_at at time zone 'Asia/Manila')::date
 *
 * DELIBERATELY NOT the device's time zone. A phone set to another zone
 * (or one that travelled) must still file its record under the Philippine
 * work day, because that is the key the server uses and the unique
 * constraint (worker_id, work_date, session_seq) is what makes "one
 * record per worker per day" true.
 *
 * And never derive it from a UTC date: PH is UTC+8, so anything captured
 * before 08:00 local rolls BACK a day -- which is most Time Ins.
 */
@JvmInline
value class WorkDate(val date: LocalDate) {

    override fun toString(): String = date.toString()

    companion object {
        fun of(captured: Instant): WorkDate = WorkDate(captured.atZone(AttendanceZone).toLocalDate())

        fun today(clock: Instant = Instant.now()): WorkDate = of(clock)
    }
}

/** Which half of the day this capture is. Drives colour and copy. */
enum class TimeDirection {
    IN, OUT;

    /** The `in` / `out` token in the storage path contract. */
    val slug: String get() = name.lowercase()
}

/**
 * The Storage object path, fixed by 0050 section 7:
 *
 *     {worker_id}/{work_date}/{in|out}-{event_id}.jpg
 *
 * The bucket's RLS policy compares the FIRST path segment to auth.uid(),
 * so this shape is a permission check, not a naming convention. Get it
 * wrong and the upload is refused.
 */
fun photoPath(
    workerId: String,
    workDate: WorkDate,
    direction: TimeDirection,
    eventId: String
): String = "$workerId/$workDate/${direction.slug}-$eventId.jpg"
