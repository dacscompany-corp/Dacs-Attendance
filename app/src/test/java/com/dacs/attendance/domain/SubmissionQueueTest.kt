package com.dacs.attendance.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rules that make a queue of attendance submissions safe to drain.
 *
 * Every one of these exists because getting it wrong produces a WRONG
 * ATTENDANCE RECORD rather than a crash -- a worker paid for the wrong
 * hours, or marked absent for a day they worked.
 */
class SubmissionQueueTest {

    private val me = "worker-a"

    private fun pending(
        eventId: String,
        direction: TimeDirection,
        createdAt: Instant,
        workerId: String = me
    ) = QueuedSubmission(
        eventId = eventId,
        direction = direction,
        createdAt = createdAt,
        workerId = workerId
    )

    @Test
    fun `the queue drains oldest first`() {
        // Not just tidiness: a Time Out sent before its Time In is
        // rejected with NOT_TIMED_IN, and the worker's day is lost.
        val out = pending("b", TimeDirection.OUT, Instant.parse("2026-08-19T09:30:00Z"))
        val timeIn = pending("a", TimeDirection.IN, Instant.parse("2026-08-18T23:45:00Z"))

        assertEquals(timeIn, nextToSend(listOf(out, timeIn), me))
    }

    @Test
    fun `a time out waits for its own time in even when it was queued first`() {
        // Clock skew or a retry can reorder created_at. Direction is the
        // stronger signal: an unsent IN always goes before an OUT.
        val out = pending("b", TimeDirection.OUT, Instant.parse("2026-08-18T23:00:00Z"))
        val timeIn = pending("a", TimeDirection.IN, Instant.parse("2026-08-18T23:45:00Z"))

        assertEquals(timeIn, nextToSend(listOf(out, timeIn), me))
    }

    @Test
    fun `an empty queue has nothing to send`() {
        assertNull(nextToSend(emptyList(), me))
    }

    @Test
    fun `a lone time out is still sent`() {
        // Its Time In already reached the server on an earlier drain.
        val out = pending("b", TimeDirection.OUT, Instant.parse("2026-08-19T09:30:00Z"))

        assertEquals(out, nextToSend(listOf(out), me))
    }

    @Test
    fun `another worker's queued submission is never sent under my session`() {
        // Shared and borrowed phones are normal on a site. The RPC files
        // the record against auth.uid(), so sending a row queued by
        // someone else would record THEIR attendance as MINE -- a wrong
        // record about a real person, which is the one failure this
        // system cannot tolerate.
        val theirs = pending("b", TimeDirection.IN,
            Instant.parse("2026-08-18T23:00:00Z"), workerId = "worker-b")

        assertNull(nextToSend(listOf(theirs), me))
    }

    @Test
    fun `my own submission is still sent when someone else's is queued too`() {
        val theirs = pending("b", TimeDirection.IN,
            Instant.parse("2026-08-18T23:00:00Z"), workerId = "worker-b")
        val mine = pending("a", TimeDirection.IN, Instant.parse("2026-08-19T00:00:00Z"))

        assertEquals(mine, nextToSend(listOf(theirs, mine), me))
    }

    @Test
    fun `a row with no owner is not sent to anyone`() {
        // Rows migrated from before submissions carried a worker id.
        // Losing an upload is bad; attributing it to the wrong worker is
        // worse and cannot be detected afterwards.
        val orphan = pending("c", TimeDirection.IN,
            Instant.parse("2026-08-19T00:00:00Z"), workerId = "")

        assertNull(nextToSend(listOf(orphan), me))
    }

    // ── What to do when the server refuses ──────────────────────────

    @Test
    fun `a refusal about the state of the day drops the row instead of retrying`() {
        // ALREADY_TIMED_IN means the server already has this day -- most
        // likely from the worker's other device. Retrying can never
        // succeed, and keeping it queued would retry forever.
        assertEquals(
            QueueOutcome.DropAndReconcile,
            outcomeFor(AttendanceFailure.AlreadyTimedIn)
        )
        assertEquals(
            QueueOutcome.DropAndReconcile,
            outcomeFor(AttendanceFailure.AlreadyComplete)
        )
    }

    @Test
    fun `no signal is retried, not dropped`() {
        // The whole reason the queue exists.
        assertEquals(QueueOutcome.Retry, outcomeFor(AttendanceFailure.NoConnection))
    }

    @Test
    fun `a not-timed-in refusal is retried, because its time in may still be queued`() {
        assertEquals(QueueOutcome.Retry, outcomeFor(AttendanceFailure.NotTimedIn))
    }

    @Test
    fun `a refusal the worker must act on is surfaced, not silently dropped`() {
        // A deactivated account or an unassigned owner will never
        // succeed, and the worker needs to be told to call the office --
        // not left believing their day was recorded.
        assertEquals(QueueOutcome.FailPermanently, outcomeFor(AttendanceFailure.AccountInactive))
        assertEquals(QueueOutcome.FailPermanently, outcomeFor(AttendanceFailure.NoOwnerAssigned))
        assertEquals(QueueOutcome.FailPermanently, outcomeFor(AttendanceFailure.NotAWorker))
    }

    @Test
    fun `a clock problem is permanent, since the same captured_at will always be refused`() {
        // CAPTURED_IN_FUTURE is judged against a timestamp frozen at the
        // shutter. Retrying an hour later sends the identical value.
        assertEquals(QueueOutcome.FailPermanently, outcomeFor(AttendanceFailure.DeviceClockWrong))
    }
}
