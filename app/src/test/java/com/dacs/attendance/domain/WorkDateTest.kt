package com.dacs.attendance.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The work_date key, derived the same way attendance_time_in derives it:
 *   (captured_at at time zone 'Asia/Manila')::date
 *
 * This is the single most expensive thing to get wrong in the app. PH is
 * UTC+8, so a UTC-derived date rolls BACK a day for anything captured
 * before 08:00 local -- which is most Time Ins. A worker who clocks in at
 * 07:45 would be filed under yesterday, and the unique key on
 * (worker, work_date, session_seq) would then reject their real Time In
 * tomorrow morning.
 */
class WorkDateTest {

    @Test
    fun `an early morning capture lands on today, not yesterday`() {
        // 2026-08-19 07:45 Manila == 2026-08-18 23:45 UTC. A naive
        // toISOString().slice(0,10) gives the 18th. It is the 19th.
        val captured = Instant.parse("2026-08-18T23:45:00Z")
        assertEquals("2026-08-19", WorkDate.of(captured).toString())
    }

    @Test
    fun `late night and just after midnight are different work dates`() {
        // 23:50 Manila on the 19th, and 00:10 Manila on the 20th.
        val beforeMidnight = WorkDate.of(Instant.parse("2026-08-19T15:50:00Z"))
        val afterMidnight = WorkDate.of(Instant.parse("2026-08-19T16:10:00Z"))

        assertEquals("2026-08-19", beforeMidnight.toString())
        assertEquals("2026-08-20", afterMidnight.toString())
        assertNotEquals(beforeMidnight, afterMidnight)
    }

    @Test
    fun `a capture at midday is on the obvious date`() {
        assertEquals("2026-08-19", WorkDate.of(Instant.parse("2026-08-19T04:00:00Z")).toString())
    }

    @Test
    fun `the photo path follows the storage contract exactly`() {
        // 0050 section 7: {worker_id}/{work_date}/{in|out}-{event_id}.jpg
        // The RLS policy keys on the FIRST path segment being the caller's
        // uid, so a wrong shape here is a rejected upload, not a tidy-up.
        val path = photoPath(
            workerId = "11111111-1111-1111-1111-111111111111",
            workDate = WorkDate.of(Instant.parse("2026-08-18T23:45:00Z")),
            direction = TimeDirection.IN,
            eventId = "abcdefab-1234-5678-9abc-def012345678"
        )
        assertEquals(
            "11111111-1111-1111-1111-111111111111/2026-08-19/" +
                "in-abcdefab-1234-5678-9abc-def012345678.jpg",
            path
        )
    }

    @Test
    fun `a time out photo is named out, not in`() {
        val path = photoPath(
            workerId = "w",
            workDate = WorkDate.of(Instant.parse("2026-08-19T09:30:00Z")),
            direction = TimeDirection.OUT,
            eventId = "e"
        )
        assertEquals("w/2026-08-19/out-e.jpg", path)
    }
}
