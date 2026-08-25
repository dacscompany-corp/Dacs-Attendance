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

@Singleton
class SubmissionScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager get() = WorkManager.getInstance(context)

    /**
     * Queues an upload attempt.
     *
     * Unique per event id with KEEP, so a double tap -- or the sweeper
     * firing while a submission is already scheduled -- is a no-op
     * rather than a second upload.
     */
    fun enqueue(eventId: String) {
        workManager.enqueueUniqueWork(
            "submit-$eventId",
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
