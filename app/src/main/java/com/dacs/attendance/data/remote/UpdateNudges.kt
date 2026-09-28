package com.dacs.attendance.data.remote

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * "The server just said this build is too old."
 *
 * Fired by the OkHttp interceptor in [SupabaseModule] when any response
 * carries APP_UPDATE_REQUIRED (0077) -- the Time In screen and the
 * background queue alike -- so the update dialog appears the moment the
 * office publishes, not at the next resume.
 *
 * Its own class with no dependencies because the Supabase client is what
 * fires it, and the update repository that listens needs that client:
 * putting the flow on either would make the two depend on each other.
 */
@Singleton
class UpdateNudges @Inject constructor() {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun nudge() {
        _events.tryEmit(Unit)
    }
}
