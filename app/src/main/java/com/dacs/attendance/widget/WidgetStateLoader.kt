package com.dacs.attendance.widget

import android.util.Log
import com.dacs.attendance.data.local.AttendanceDatabase
import com.dacs.attendance.data.local.WorkerCache
import com.dacs.attendance.data.repo.toDomain
import com.dacs.attendance.domain.WidgetState
import com.dacs.attendance.domain.WorkDate
import com.dacs.attendance.domain.widgetStateFor
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

private const val TAG = "WidgetStateLoader"

/**
 * Today, as the widget shows it, from the phone alone.
 *
 * Never the network. The widget is looked at on sites with no signal, and
 * the mirror is already the app's own answer to "have I timed in" there.
 */
@Singleton
class WidgetStateLoader @Inject constructor(
    private val database: AttendanceDatabase,
    private val workerCache: WorkerCache,
    private val client: SupabaseClient
) {

    suspend fun load(now: Instant = Instant.now()): WidgetState = try {
        // Same identity rule as the offline repository: offline, the auth
        // client reports nobody for a session it cannot refresh, and the
        // sign-in we witnessed stands in.
        client.auth.awaitInitialization()
        val workerId = client.auth.currentUserOrNull()?.id ?: workerCache.lastSignedInId
        val today = WorkDate.today(now)

        if (workerId.isNullOrBlank()) {
            WidgetState.SignedOut
        } else {
            // Both reads are scoped to this worker. That is what keeps the
            // last worker's day off a shared phone's home screen.
            widgetStateFor(
                workerId = workerId,
                record = database.cachedRecords().forDate(workerId, today.toString())?.toDomain(),
                today = today,
                hasPending = database.pendingSubmissions().hasPendingFor(workerId)
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        // No button is the safe failure: a guessed IN or OUT sends the
        // worker to a photo the server may refuse.
        Log.w(TAG, "could not read today for the widget", error)
        WidgetState.Unknown()
    }
}
