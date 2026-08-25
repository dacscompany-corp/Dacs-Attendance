package com.dacs.attendance.ui.dashboard

import com.dacs.attendance.data.repo.AttendanceRepository
import com.dacs.attendance.data.repo.SubmissionRequest
import com.dacs.attendance.domain.AttendanceFailure
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.support.MainDispatcherRule
import com.dacs.attendance.ui.components.StepState
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `nothing is offered until today's record has actually been read`() = runTest {
        // Found on a real device: the dashboard rendered "no record yet"
        // AND the TIME IN button while the query was still in flight. On a
        // slow connection a worker acts on that and times in twice -- the
        // exact duplicate the whole day-key design exists to prevent.
        val vm = DashboardViewModel(FakeAttendance(Result.success(record(AttendanceStatus.WORKING))))

        // Deliberately BEFORE advanceUntilIdle: this is the in-flight state.
        assertTrue(vm.uiState.value.loading)
        assertNull(vm.uiState.value.nextAction)

        advanceUntilIdle()
        assertEquals(TimeDirection.OUT, vm.uiState.value.nextAction)
    }

    @Test
    fun `a finished day shows all three steps as done`() = runTest {
        // Found on a device: a complete day rendered "Working: not yet"
        // and "Time Out: locked" immediately above "TOTAL HOURS 5h 45m".
        // The stepper is the first thing a worker reads, and it was
        // contradicting the number underneath it.
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

        val state = vm.uiState.value
        assertEquals(StepState.Done, state.timeInStep)
        assertEquals(StepState.Done, state.workingStep)
        assertEquals(StepState.Done, state.timeOutStep)
    }

    @Test
    fun `an open day shows time in done, working now, time out available`() = runTest {
        val vm = DashboardViewModel(FakeAttendance(Result.success(record(AttendanceStatus.WORKING))))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(StepState.Done, state.timeInStep)
        assertEquals(StepState.Now, state.workingStep)
        assertEquals(StepState.Now, state.timeOutStep)
    }

    @Test
    fun `a day not started shows time in now and the rest locked`() = runTest {
        val vm = DashboardViewModel(FakeAttendance(Result.success(null)))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(StepState.Now, state.timeInStep)
        assertEquals(StepState.Locked, state.workingStep)
        assertEquals(StepState.Locked, state.timeOutStep)
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
