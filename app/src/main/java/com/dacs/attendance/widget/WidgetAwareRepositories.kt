package com.dacs.attendance.widget

import com.dacs.attendance.data.repo.AttendanceRepository
import com.dacs.attendance.data.repo.AuthRepository
import com.dacs.attendance.data.repo.SubmissionRequest
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.WorkerProfile

/**
 * The attendance repository, telling the widget whenever today may have
 * changed.
 *
 * A decorator rather than calls sprinkled through the offline repository:
 * that class needs a live Supabase client and Room, and this rule -- the
 * widget follows every write -- deserves a test.
 *
 * [submit] is the optimistic write, so the widget flips to "Timed in" the
 * moment the worker submits, signal or not. [today] is where Home
 * reconciles with the server, so a correction made there reaches the
 * widget too.
 */
class WidgetAwareAttendanceRepository(
    private val inner: AttendanceRepository,
    private val widgets: WidgetRefresher
) : AttendanceRepository {

    override suspend fun submit(request: SubmissionRequest): Result<AttendanceRecord> {
        val result = inner.submit(request)
        widgets.refreshQuietly()
        return result
    }

    override suspend fun today(): Result<AttendanceRecord?> {
        val result = inner.today()
        widgets.refreshQuietly()
        return result
    }

    override suspend fun history(fromWorkDate: String, toWorkDate: String): Result<List<AttendanceRecord>> =
        inner.history(fromWorkDate, toWorkDate)
}

/**
 * The auth repository, telling the widget when the worker changes.
 *
 * Sign-out matters most: site phones are shared, and a widget still
 * showing the last worker's times after they signed out is exactly the
 * leak the worker cache is careful to avoid.
 */
class WidgetAwareAuthRepository(
    private val inner: AuthRepository,
    private val widgets: WidgetRefresher
) : AuthRepository {

    override suspend fun signIn(email: String, password: String): Result<WorkerProfile> {
        val result = inner.signIn(email, password)
        widgets.refreshQuietly()
        return result
    }

    override suspend fun signOut() {
        try {
            inner.signOut()
        } finally {
            widgets.refreshQuietly()
        }
    }

    override suspend fun currentWorker(): WorkerProfile? =
        inner.currentWorker()

    override suspend fun changePassword(newPassword: String): Result<Unit> =
        inner.changePassword(newPassword)
}
