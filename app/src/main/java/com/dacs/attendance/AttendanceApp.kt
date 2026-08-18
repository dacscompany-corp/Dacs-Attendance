package com.dacs.attendance

import android.app.Application

/**
 * Application entry point. Holds process-wide wiring (Supabase client,
 * Room database, WorkManager configuration) as those land in B2-B4.
 */
class AttendanceApp : Application()
