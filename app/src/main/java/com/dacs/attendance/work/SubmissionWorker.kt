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
import com.dacs.attendance.domain.ProjectSystem
import com.dacs.attendance.domain.QueuedSubmission
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.nextToSend
import io.github.jan.supabase.auth.auth
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
    private val scheduler: SubmissionScheduler,
    private val client: io.github.jan.supabase.SupabaseClient
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Whose session is this? With none, nothing may be sent: the RPC
        // would file the record against whoever signs in next.
        val workerId = client.auth.currentUserOrNull()?.id.orEmpty()
        if (workerId.isEmpty()) return Result.success()

        val queue = database.pendingSubmissions().sendable(workerId)
        if (queue.isEmpty()) return Result.success()

        val next = nextToSend(queue.map { it.toQueued() }, workerId)
            ?: return Result.success()
        val row = queue.first { it.eventId == next.eventId }

        val outcome = send(row, workerId)

        // Anything still queued gets another run. Chaining rather than
        // looping here keeps each attempt inside WorkManager's own
        // backoff and constraints.
        if (database.pendingSubmissions().sendable(workerId).isNotEmpty()) {
            scheduler.enqueue(row.eventId + "-next")
        }
        return outcome
    }

    private suspend fun send(row: PendingSubmissionEntity, workerId: String): Result {
        val photo = File(row.photoLocalPath)
        if (!photo.exists()) {
            // The file is gone (cleared storage, restored backup). The
            // RPC requires a photo path, and inventing one would record
            // attendance with no evidence.
            Log.e(TAG, "photo missing for ${row.eventId}; failing permanently")
            database.pendingSubmissions().markFailed(row.eventId, "PHOTO_MISSING")
            return Result.success()
        }

        // A row queued before 0059 names an integer id from a project list
        // the server has dropped, and carries no system. There is nothing
        // to send it against, so it fails here rather than being retried
        // forever against a project that no longer exists.
        val system = ProjectSystem.of(row.projectSystem)
        if (system == null) {
            Log.e(TAG, "no project system on ${row.eventId}; failing permanently")
            database.pendingSubmissions().markFailed(row.eventId, "PROJECT_RETIRED")
            return Result.success()
        }

        val result = remote.submit(
            SubmissionRequest(
                direction = TimeDirection.valueOf(row.direction),
                projectSystem = system,
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
                database.cachedRecords().upsert(record.toEntity(workerId, pending = false))
                database.pendingSubmissions().deleteById(row.eventId)
                // Only now is the photo safe to delete.
                photos.discard(row.photoLocalPath)
                Log.i(TAG, "submitted ${row.direction} for ${record.workDate}")
            },
            onFailure = { error ->
                val failure = AttendanceFailure.of(error)
                database.pendingSubmissions().recordAttempt(row.eventId, failure.name)

                // What the server actually said, not just the enum it maps to.
                // AttendanceFailure.Unexpected is the catch-all for anything we
                // have no code for -- so the one case where the enum tells you
                // nothing is exactly the case you need to debug. This queue
                // retries for days; without the underlying reason there is no
                // way to find out why.
                //
                // The FIRST LINE only, and never the throwable itself. supabase
                // -kt puts the whole request in its message -- including
                // "Authorization: Bearer <the worker's access token>" -- and
                // passing the exception to Log would write that token into
                // logcat on every retry. The first line carries the reason
                // ("new row violates row-level security policy") and none of
                // the credentials.
                if (failure == AttendanceFailure.Unexpected) {
                    val reason = (error.message ?: error::class.java.simpleName)
                        .lineSequence().firstOrNull().orEmpty().take(200)
                    Log.e(TAG, "unmapped failure for ${row.eventId}: $reason")
                }

                when (outcomeFor(failure)) {
                    QueueOutcome.Retry -> {
                        Log.w(TAG, "retrying ${row.eventId} (attempt ${row.attempts + 1}): $failure")
                        return Result.retry()
                    }

                    QueueOutcome.DropAndReconcile -> {
                        // The server already holds this day. Its version
                        // wins; ours is discarded rather than retried
                        // forever against a record that already exists.
                        Log.i(TAG, "server already has this day; reconciling ${row.eventId}")
                        remote.today().getOrNull()?.let {
                            database.cachedRecords().upsert(it.toEntity(workerId, pending = false))
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
    createdAt = Instant.ofEpochMilli(createdAt),
    workerId = workerId
)
