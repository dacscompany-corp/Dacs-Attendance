package com.dacs.attendance

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt builds the process-wide graph here: the
 * Supabase client and its encrypted session store (B2), then Room and
 * WorkManager as those land in B3-B4.
 */
@HiltAndroidApp
class AttendanceApp : Application()
