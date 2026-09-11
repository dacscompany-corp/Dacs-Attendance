package com.dacs.attendance.widget

import com.dacs.attendance.data.repo.AttendanceRepository
import com.dacs.attendance.data.repo.AuthRepository
import com.dacs.attendance.data.repo.SubmissionRequest
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.ProjectSystem
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.WorkerProfile
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget follows the app; it must never lead it. A refresh that throws
 * is a widget that lags, and that is acceptable. A submission that fails
 * because of it is not.
 */
class WidgetAwareRepositoriesTest {

    private class CountingRefresher(private val fails: Boolean = false) : WidgetRefresher {
        var refreshes = 0
            private set

        override suspend fun refresh() {
            refreshes++
            if (fails) throw IllegalStateException("no widget host")
        }
    }

    private val record = AttendanceRecord(
        id = "rec-1",
        workDate = "2026-09-11",
        status = AttendanceStatus.WORKING,
        timeInAt = Instant.parse("2026-09-11T00:52:00Z"),
        timeOutAt = null,
        timeInProjectName = "ABC Building Project",
        timeOutProjectName = null,
        totalMinutes = null,
        pending = true
    )

    private val request = SubmissionRequest(
        direction = TimeDirection.IN,
        projectSystem = ProjectSystem.PC,
        projectId = "p1",
        capturedAt = Instant.parse("2026-09-11T00:52:00Z"),
        photo = File("unused.jpg"),
        description = null,
        eventId = "e1"
    )

    private inner class FakeAttendance : AttendanceRepository {
        val submitResult = Result.success(record)
        val todayResult = Result.success<AttendanceRecord?>(record)

        override suspend fun submit(request: SubmissionRequest) = submitResult
        override suspend fun today() = todayResult
        override suspend fun history(fromWorkDate: String, toWorkDate: String) =
            Result.success(listOf(record))
    }

    private class FakeAuth : AuthRepository {
        val worker = WorkerProfile("w1", "w1@example.com", "Juan", null, 42, "worker", "active")
        var signOuts = 0
            private set

        override suspend fun signIn(email: String, password: String) = Result.success(worker)
        override suspend fun signOut() { signOuts++ }
        override suspend fun currentWorker(): WorkerProfile? = worker
        override suspend fun changePassword(newPassword: String) = Result.success(Unit)
    }

    // ── attendance ─────────────────────────────────────────────────

    @Test
    fun `a submission refreshes the widget and returns the inner result`() = runTest {
        val inner = FakeAttendance()
        val widgets = CountingRefresher()

        val result = WidgetAwareAttendanceRepository(inner, widgets).submit(request)

        assertSame(inner.submitResult, result)
        assertEquals(1, widgets.refreshes)
    }

    @Test
    fun `a failing widget never fails the submission`() = runTest {
        val widgets = CountingRefresher(fails = true)

        val result = WidgetAwareAttendanceRepository(FakeAttendance(), widgets).submit(request)

        assertTrue(result.isSuccess)
        assertEquals(1, widgets.refreshes)
    }

    @Test
    fun `reading today refreshes the widget, so a server correction reaches it`() = runTest {
        val inner = FakeAttendance()
        val widgets = CountingRefresher()

        val result = WidgetAwareAttendanceRepository(inner, widgets).today()

        assertSame(inner.todayResult, result)
        assertEquals(1, widgets.refreshes)
    }

    @Test
    fun `history does not touch the widget`() = runTest {
        val widgets = CountingRefresher()

        WidgetAwareAttendanceRepository(FakeAttendance(), widgets).history("2026-09-01", "2026-09-11")

        assertEquals(0, widgets.refreshes)
    }

    // ── auth ───────────────────────────────────────────────────────

    @Test
    fun `signing in refreshes the widget`() = runTest {
        val widgets = CountingRefresher()

        val result = WidgetAwareAuthRepository(FakeAuth(), widgets).signIn("a@b.c", "pw")

        assertTrue(result.isSuccess)
        assertEquals(1, widgets.refreshes)
    }

    @Test
    fun `signing out refreshes the widget so no times outlive the session`() = runTest {
        val inner = FakeAuth()
        val widgets = CountingRefresher()

        WidgetAwareAuthRepository(inner, widgets).signOut()

        assertEquals(1, inner.signOuts)
        assertEquals(1, widgets.refreshes)
    }

    @Test
    fun `a failing widget never fails a sign-out`() = runTest {
        val inner = FakeAuth()
        val widgets = CountingRefresher(fails = true)

        WidgetAwareAuthRepository(inner, widgets).signOut()

        assertEquals(1, inner.signOuts)
    }

    @Test
    fun `reading the current worker does not touch the widget`() = runTest {
        val widgets = CountingRefresher()

        WidgetAwareAuthRepository(FakeAuth(), widgets).currentWorker()

        assertEquals(0, widgets.refreshes)
    }
}
