package com.dacs.attendance.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dacs.attendance.data.local.AttendanceDatabase
import com.dacs.attendance.data.local.PendingSubmissionEntity
import com.dacs.attendance.data.local.PhotoStore
import com.dacs.attendance.data.repo.SubmissionRequest
import com.dacs.attendance.data.repo.SupabaseAttendanceRepository
import com.dacs.attendance.data.repo.toEntity
import com.dacs.attendance.domain.AttendanceFailure
import com.dacs.attendance.domain.QueueOutcome
import com.dacs.attendance.domain.QueuedSubmission
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.nextToSend
import com.dacs.attendance.domain.outcomeFor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.time.Instant

private const val TAG = "SubmissionWorker"

/**
 * Drains the submission queue.
 *
 * Runs in WorkManager rather than a coroutine so it survives the app
 * being swiped away, the phone being pocketed, and a reboot -- all of
 * which happen between a worker tapping SUBMIT and walking back into
 * signal.
 *
 * It sends ONE submission per run, in queue order, and re-enqueues while
 * work remains. That keeps the "Time Out waits for its Time In" rule
 * trivially true instead of depending on parallel workers behaving.
 */
@HiltWorker
class SubmissionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: AttendanceDatabase,
    private val remote: SupabaseAttendanceRepository,
    private val photos: PhotoStore,
    private val scheduler: SubmissionScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val queue = database.pendingSubmissions().sendable()
        if (queue.isEmpty()) return Result.success()

        val next = nextToSend(queue.map { it.toQueued() })
            ?: return Result.success()
        val row = queue.first { it.eventId == next.eventId }

        val outcome = send(row)

        // Anything still queued gets another run. Chaining rather than
        // looping here keeps each attempt inside WorkManager's own
        // backoff and constraints.
        if (database.pendingSubmissions().sendable().isNotEmpty()) {
            scheduler.enqueue(row.eventId + "-next")
        }
        return outcome
    }

    private suspend fun send(row: PendingSubmissionEntity): Result {
        val photo = File(row.photoLocalPath)
        if (!photo.exists()) {
            // The file is gone (cleared storage, restored backup). The
            // RPC requires a photo path, and inventing one would record
            // attendance with no evidence.
            Log.e(TAG, "photo missing for ${row.eventId}; failing permanently")
            database.pendingSubmissions().markFailed(row.eventId, "PHOTO_MISSING")
            return Result.success()
        }

        val result = remote.submit(
            SubmissionRequest(
                direction = TimeDirection.valueOf(row.direction),
                projectId = row.projectId,
                capturedAt = Instant.ofEpochMilli(row.capturedAt),
                photo = photo,
                description = row.description,
                eventId = row.eventId,
                latitude = row.latitude,
                longitude = row.longitude,
                accuracyMetres = row.accuracyMetres,
                // Recorded for the admin, never shown to the worker.
                wasOffline = row.wasOffline
            )
        )

        result.fold(
            onSuccess = { record ->
                // The server's row replaces the optimistic mirror --
                // including its total_minutes, which is authoritative.
                database.cachedRecords().upsert(record.toEntity(pending = false))
                database.pendingSubmissions().deleteById(row.eventId)
                // Only now is the photo safe to delete.
                photos.discard(row.photoLocalPath)
                Log.i(TAG, "submitted ${row.direction} for ${record.workDate}")
            },
            onFailure = { error ->
                val failure = AttendanceFailure.of(error)
                database.pendingSubmissions().recordAttempt(row.eventId, failure.name)

                when (outcomeFor(failure)) {
                    QueueOutcome.Retry -> {
                        Log.w(TAG, "retrying ${row.eventId}: $failure")
                        return Result.retry()
                    }

                    QueueOutcome.DropAndReconcile -> {
                        // The server already holds this day. Its version
                        // wins; ours is discarded rather than retried
                        // forever against a record that already exists.
                        Log.i(TAG, "server already has this day; reconciling ${row.eventId}")
                        remote.today().getOrNull()?.let {
                            database.cachedRecords().upsert(it.toEntity(pending = false))
                        }
                        database.pendingSubmissions().deleteById(row.eventId)
                        photos.discard(row.photoLocalPath)
                    }

                    QueueOutcome.FailPermanently -> {
                        // Kept, flagged, and surfaced. Silently dropping
                        // would leave the worker believing their day was
                        // recorded when it never will be.
                        Log.e(TAG, "permanent failure for ${row.eventId}: $failure")
                        database.pendingSubmissions().markFailed(row.eventId, failure.name)
                    }
                }
            }
        )
        return Result.success()
    }
}

private fun PendingSubmissionEntity.toQueued() = QueuedSubmission(
    eventId = eventId,
    direction = TimeDirection.valueOf(direction),
    createdAt = Instant.ofEpochMilli(createdAt)
)
