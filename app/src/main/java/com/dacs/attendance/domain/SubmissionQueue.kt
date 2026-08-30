package com.dacs.attendance.domain

import java.time.Instant

/** A submission waiting to reach the server. */
data class QueuedSubmission(
    val eventId: String,
    val direction: TimeDirection,
    val createdAt: Instant,
    /** Who queued it. Empty for rows migrated from before this existed. */
    val workerId: String
)

/**
 * Which queued submission to send next.
 *
 * Two rules, and the second overrides the first:
 *
 *  1. Oldest first, so a day is rebuilt in the order it happened.
 *  2. An unsent Time In ALWAYS goes before a Time Out. Clock skew and
 *     retries can reorder created_at, and a Time Out that arrives first
 *     is refused with NOT_TIMED_IN -- costing the worker their day.
 *
 * And only ever [currentWorkerId]'s own rows. Phones get shared and
 * borrowed on a site; the RPC files each record against auth.uid(), so
 * uploading someone else's queued row under this session would record
 * THEIR attendance as THIS worker's -- a wrong record about a real
 * person, undetectable afterwards. A row with no owner (migrated from
 * before submissions carried one) belongs to nobody and is never sent.
 */
fun nextToSend(
    queue: List<QueuedSubmission>,
    currentWorkerId: String
): QueuedSubmission? =
    queue.filter { it.workerId.isNotEmpty() && it.workerId == currentWorkerId }.minWithOrNull(
        compareBy<QueuedSubmission> { if (it.direction == TimeDirection.IN) 0 else 1 }
            .thenBy { it.createdAt }
    )

/** What the queue does with a submission the server refused. */
enum class QueueOutcome {
    /** Transient. Keep the row and try again later. */
    Retry,

    /**
     * The server already holds this day (usually the worker's other
     * device). Retrying can never succeed, so reconcile the local mirror
     * to the server's row and drop the pending one.
     */
    DropAndReconcile,

    /**
     * Will never succeed and the worker must act -- call the office, fix
     * the phone's clock. Kept visible rather than silently discarded, so
     * nobody is left believing their day was recorded.
     */
    FailPermanently
}

fun outcomeFor(failure: AttendanceFailure): QueueOutcome = when (failure) {
    AttendanceFailure.AlreadyTimedIn,
    AttendanceFailure.AlreadyComplete -> QueueOutcome.DropAndReconcile

    // NotTimedIn is retryable on purpose: the matching Time In may still
    // be sitting in this very queue, one row ahead.
    AttendanceFailure.NoConnection,
    AttendanceFailure.NotTimedIn,
    AttendanceFailure.SessionExpired,
    AttendanceFailure.Unexpected -> QueueOutcome.Retry

    // captured_at was frozen at the shutter, so a later retry sends the
    // identical value and earns the identical refusal.
    AttendanceFailure.DeviceClockWrong,
    AttendanceFailure.TimeOutBeforeTimeIn,
    AttendanceFailure.ShiftTooLong,
    AttendanceFailure.ProjectUnavailable,
    AttendanceFailure.NoOwnerAssigned,
    AttendanceFailure.AccountInactive,
    AttendanceFailure.NotAWorker -> QueueOutcome.FailPermanently
}
