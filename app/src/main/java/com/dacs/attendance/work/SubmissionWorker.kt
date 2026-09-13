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
import com.dacs.attendance.widget.WidgetRefresher
import com.dacs.attendance.widget.refreshQuietly
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.time.Instant

private const val TAG = "SubmissionWorker"

/** A sane ceiling for one pass. A real queue is a handful of rows. */
private const val MAX_PER_RUN = 50

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
    private val client: io.github.jan.supabase.SupabaseClient,
    private val widgets: WidgetRefresher
) : CoroutineWorker(context, params) {

    /**
     * One drain, then the widget. The queue is what "Not sent yet" on the
     * home screen reports, and this is the only place it empties.
     *
     * Also the periodic sweeper's run, so a phone with signal re-reads the
     * widget at least every 15 minutes -- which is what rolls a finished
     * day over after Manila midnight. In `finally`, so a retry or an early
     * return still leaves the widget telling the truth about the queue.
     */
    override suspend fun doWork(): Result = try {
        drain()
    } finally {
        widgets.refreshQuietly()
    }

    private suspend fun drain(): Result {
        // Whose session is this? With none, nothing may be sent: the RPC
        // would file the record against whoever signs in next.
        val workerId = client.auth.currentUserOrNull()?.id.orEmpty()
        if (workerId.isEmpty()) return Result.success()

        // ── ONE RUN DRAINS THE QUEUE.
        //
        //    This used to send a single row and then chain another work
        //    request for the rest. Combined with a work name that was
        //    unique per EVENT, that meant several requests could be
        //    runnable at once -- and each one asked nextToSend() for the
        //    oldest row, so they all picked the SAME one and uploaded the
        //    same photo. Measured on a device: three sends of one event
        //    inside two seconds.
        //
        //    Draining here, under a single unique work name, means only
        //    one attempt is ever in flight.
        //
        //    A failure still stops the pass and hands control back to
        //    WorkManager, so retries keep its backoff and its network
        //    constraint. Whatever is left goes on the next run.
        repeat(MAX_PER_RUN) {
            val queue = database.pendingSubmissions().sendable(workerId)
            if (queue.isEmpty()) return Result.success()

            val next = nextToSend(queue.map { it.toQueued() }, workerId)
                ?: return Result.success()
            val row = queue.first { it.eventId == next.eventId }

            val outcome = send(row, workerId)
            // Anything but success is WorkManager's business: retry with
            // backoff, or a permanent failure that has already been
            // recorded on the row.
            if (outcome !is Result.Success) return outcome
        }

        // Only reachable with an implausibly long queue. Let WorkManager
        // schedule the remainder rather than looping here forever, which
        // would hold a wakelock for as long as the queue kept growing.
        return Result.success()
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

        // The one value this whole layer exists to protect. A queued row
        // must reach the server carrying the SHUTTER time, never the
        // upload time -- a Time In taken at 07:45 on a dead-signal site
        // has to still say 07:45 when it lands at noon, or the reward
        // calls every offline worker late.
        //
        // Logged because a record was once observed arriving stamped 13
        // minutes after its capture, and the queue row is deleted on
        // success, so there was nothing left to inspect afterwards.
        Log.i(
            TAG,
            "sending ${row.eventId.take(8)} captured=" +
                Instant.ofEpochMilli(row.capturedAt) + " offline=" + row.wasOffline
        )

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
                wasOffline = row.wasOffline,
                isMock = row.isMock,
                permissionDenied = row.permissionDenied
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
