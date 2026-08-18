package com.dacs.attendance.ui.dashboard

import com.dacs.attendance.data.repo.AttendanceRepository
import com.dacs.attendance.data.repo.SubmissionRequest
import com.dacs.attendance.domain.AttendanceFailure
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.support.MainDispatcherRule
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * The dashboard is entirely a function of today's record. Screens 03 and
 * 09 in the design are the same screen in two states, so this is where
 * "what may the worker do next" is decided -- once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeAttendance(
        private val result: Result<AttendanceRecord?>
    ) : AttendanceRepository {
        var loads = 0
            private set

        override suspend fun submit(request: SubmissionRequest) = error("not used here")

        override suspend fun today(): Result<AttendanceRecord?> {
            loads++
            return result
        }
    }

    private fun record(
        status: AttendanceStatus,
        timeIn: Instant? = Instant.parse("2026-08-18T23:45:00Z"),
        timeOut: Instant? = null,
        totalMinutes: Int? = null
    ) = AttendanceRecord(
        id = "rec-1",
        workDate = "2026-08-19",
        status = status,
        timeInAt = timeIn,
        timeOutAt = timeOut,
        timeInProjectName = "ABC Building Project",
        timeOutProjectName = null,
        totalMinutes = totalMinutes
    )

    @Test
    fun `no record yet means the worker may time in`() = runTest {
        val vm = DashboardViewModel(FakeAttendance(Result.success(null)))
        advanceUntilIdle()

        assertNull(vm.uiState.value.record)
        assertEquals(TimeDirection.IN, vm.uiState.value.nextAction)
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun `an open record means the next action is TIME OUT`() = runTest {
        val vm = DashboardViewModel(FakeAttendance(Result.success(record(AttendanceStatus.WORKING))))
        advanceUntilIdle()

        assertEquals(TimeDirection.OUT, vm.uiState.value.nextAction)
    }

    @Test
    fun `a finished day offers nothing further`() = runTest {
        // One record per worker per day. Offering TIME IN again could only
        // ever produce ALREADY_TIMED_IN, so the button is not shown.
        val vm = DashboardViewModel(
            FakeAttendance(
                Result.success(
                    record(
                        AttendanceStatus.COMPLETE,
                        timeOut = Instant.parse("2026-08-19T09:30:00Z"),
                        totalMinutes = 585
                    )
                )
            )
        )
        advanceUntilIdle()

        assertNull(vm.uiState.value.nextAction)
        assertEquals("9h 45m", vm.uiState.value.totalHoursLabel)
    }

    @Test
    fun `hours so far counts up from time in while working`() = runTest {
        val vm = DashboardViewModel(FakeAttendance(Result.success(record(AttendanceStatus.WORKING))))
        advanceUntilIdle()

        vm.onTick(Instant.parse("2026-08-19T02:30:00Z")) // 10:30 Manila

        assertEquals("2h 45m", vm.uiState.value.totalHoursLabel)
    }

    @Test
    fun `a failed load is reported rather than shown as an empty day`() = runTest {
        // "No record yet" and "we could not check" look identical, and a
        // worker acting on the wrong one times in twice.
        val vm = DashboardViewModel(FakeAttendance(Result.failure(IOException("no signal"))))
        advanceUntilIdle()

        assertEquals(AttendanceFailure.NoConnection, vm.uiState.value.failure)
        assertNull(vm.uiState.value.nextAction)
    }

    @Test
    fun `refresh re-reads the record after a submission`() = runTest {
        val auth = FakeAttendance(Result.success(null))
        val vm = DashboardViewModel(auth)
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(2, auth.loads)
    }
}
