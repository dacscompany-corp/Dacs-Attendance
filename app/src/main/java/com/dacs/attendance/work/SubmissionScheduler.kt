package com.dacs.attendance.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val SWEEP_WORK = "attendance-submission-sweep"

/**
 * ONE name for every upload attempt, and that is the whole point.
 *
 * This used to be unique per EVENT ("submit-$eventId") while the worker
 * drained the queue GLOBALLY. So three queued records meant three work
 * requests, each calling nextToSend(), each getting the same oldest row,
 * and each uploading the same photo. Observed on a device: one event sent
 * three times inside two seconds.
 *
 * The server was never at risk -- the event id is the idempotency key and
 * the unique index refuses a second record -- but the PHOTO went to
 * Storage three times, on a project already over its egress quota.
 *
 * A single unique name makes WorkManager serialise them, so only one
 * attempt is ever in flight.
 */
private const val SUBMIT_WORK = "attendance-submission"

/**
 * The seam for "send what is waiting".
 *
 * An interface for the same reason [LocationSource] is one: the
 * dashboard decides when to kick the queue, and that decision has to be
 * testable without a Context or a real WorkManager.
 */
interface UploadScheduler {
    fun sendNow()
}

@Singleton
class SubmissionScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : UploadScheduler {
    private val workManager get() = WorkManager.getInstance(context)

    /**
     * Queues an upload attempt.
     *
     * Unique per event id with KEEP, so a double tap -- or the sweeper
     * firing while a submission is already scheduled -- is a no-op
     * rather than a second upload.
     */
    fun enqueue(eventId: String) {
        // eventId is no longer part of the name -- see SUBMIT_WORK. It
        // stays in the signature because callers name what they queued,
        // and losing that would make the call site read as "kick the
        // queue" when it means "I have added something to it".
        workManager.enqueueUniqueWork(
            SUBMIT_WORK,
            // KEEP: an attempt already scheduled will drain this row too,
            // because the worker re-reads the queue on every pass.
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SubmissionWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                // Exponential from 30s: a site with intermittent signal
                // should not be hammered, and nothing here is urgent to
                // the second -- the record already exists on the device.
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        )
    }

    /**
     * Send anything waiting, NOW, ignoring any backoff already accrued.
     *
     * ── WHY THIS IS NEEDED. Each failed attempt doubles WorkManager's
     *    backoff from 30s. A worker on a site with no signal accrues
     *    several failures over a morning, so by the time they walk back
     *    into coverage the next attempt can be ten minutes or more away
     *    -- measured on a device at seven minutes after only a few.
     *
     *    They are standing there looking at "Not sent yet" on a record
     *    they made an hour ago, with no way to tell whether it is broken
     *    or merely waiting. The backoff is right for a background retry
     *    loop and wrong for the moment a human opens the app and can see
     *    the queue.
     *
     * ── REPLACE, not KEEP. That is the whole point: an existing request
     *    is exactly what is sitting in backoff, so keeping it would
     *    change nothing. This is a NEW attempt, deliberately starting the
     *    backoff over.
     *
     * Still constrained to CONNECTED -- calling it with no signal queues
     * an attempt rather than wasting one.
     */
    override fun sendNow() {
        workManager.enqueueUniqueWork(
            SUBMIT_WORK,
            // REPLACE, not KEEP: what is sitting there is the request in
            // backoff, so keeping it would change nothing. This is a
            // fresh attempt that deliberately restarts the delay.
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SubmissionWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        )
    }

    /**
     * The backstop. If the process dies between writing the pending row
     * and enqueuing its work, nothing else would ever pick that row up.
     * Fifteen minutes is WorkManager's own floor for periodic work.
     */
    fun ensureSweeper() {
        workManager.enqueueUniquePeriodicWork(
            SWEEP_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SubmissionWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        )
    }
}
