package com.dacs.attendance

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dacs.attendance.work.SubmissionScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Hilt builds the process-wide graph here: the
 * Supabase client and its encrypted session store, the Room database
 * holding the submission queue, and WorkManager.
 */
@HiltAndroidApp
class AttendanceApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scheduler: SubmissionScheduler

    /**
     * WorkManager is configured here rather than initialised on its own,
     * because SubmissionWorker needs the Hilt graph (database, Supabase
     * client) injected into it.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // The backstop for anything stranded by a process death between
        // writing a pending row and enqueuing its upload.
        scheduler.ensureSweeper()
    }
}
