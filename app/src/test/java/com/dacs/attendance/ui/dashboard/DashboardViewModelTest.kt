package com.dacs.attendance.ui.dashboard

import com.dacs.attendance.data.repo.AttendanceRepository
import com.dacs.attendance.data.repo.RewardRepository
import com.dacs.attendance.data.repo.SubmissionRequest
import com.dacs.attendance.domain.AttendanceFailure
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.RewardDay
import com.dacs.attendance.domain.RewardDayStatus
import com.dacs.attendance.domain.RewardStatus
import com.dacs.attendance.domain.rewardWeekStart
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.support.MainDispatcherRule
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
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

        override suspend fun history(fromWorkDate: String, toWorkDate: String) =
            Result.success(emptyList<AttendanceRecord>())

        override suspend fun today(): Result<AttendanceRecord?> {
            loads++
            return result
        }
    }

    private class FakeRewards(
        private val result: Result<List<RewardDay>> = Result.success(emptyList())
    ) : RewardRepository {
        override suspend fun weekProgress(weekStart: LocalDate) = result
        override suspend fun rewardAmount(): Result<Double?> = Result.success(500.0)
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

    /** Records that Home asked the queue to drain. */
    private class RecordingScheduler : com.dacs.attendance.work.UploadScheduler {
        var kicks = 0
            private set

        override fun sendNow() { kicks++ }
    }

    private fun viewModel(result: Result<AttendanceRecord?>) =
        DashboardViewModel(FakeAttendance(result), RecordingProjects(), FakeRewards(), RecordingScheduler())

    @Test
    fun `no record yet means the worker may time in`() = runTest {
        val vm = viewModel(Result.success(null))
        advanceUntilIdle()

        assertNull(vm.uiState.value.record)
        assertEquals(TimeDirection.IN, vm.uiState.value.nextAction)
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun `an open record means the next action is TIME OUT`() = runTest {
        val vm = viewModel(Result.success(record(AttendanceStatus.WORKING)))
        advanceUntilIdle()

        assertEquals(TimeDirection.OUT, vm.uiState.value.nextAction)
    }

    @Test
    fun `a finished day offers nothing further`() = runTest {
        // One record per worker per day. Offering TIME IN again could only
        // ever produce ALREADY_TIMED_IN, so the button is not shown.
        val vm = viewModel(
            Result.success(
                record(
                    AttendanceStatus.COMPLETE,
                    timeOut = Instant.parse("2026-08-19T09:30:00Z"),
                    totalMinutes = 585
                )
            )
        )
        advanceUntilIdle()

        assertNull(vm.uiState.value.nextAction)
        assertEquals("9h 45m", vm.uiState.value.totalHoursLabel)
    }

    @Test
    fun `hours so far counts up from time in while working`() = runTest {
        val vm = viewModel(Result.success(record(AttendanceStatus.WORKING)))
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
        val vm = viewModel(Result.success(record(AttendanceStatus.WORKING)))

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
        val vm = viewModel(
            Result.success(
                record(
                    AttendanceStatus.COMPLETE,
                    timeOut = Instant.parse("2026-08-19T09:30:00Z"),
                    totalMinutes = 585
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
        val vm = viewModel(Result.success(record(AttendanceStatus.WORKING)))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(StepState.Done, state.timeInStep)
        assertEquals(StepState.Now, state.workingStep)
        assertEquals(StepState.Now, state.timeOutStep)
    }

    @Test
    fun `a day not started shows time in now and the rest locked`() = runTest {
        val vm = viewModel(Result.success(null))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(StepState.Now, state.timeInStep)
        assertEquals(StepState.Locked, state.workingStep)
        assertEquals(StepState.Locked, state.timeOutStep)
    }

    @Test
    fun `a failed load is reported rather than shown as an empty day`() = runTest {
        // "No record yet" and "we could not check" look identical, so the
        // screen has to SAY which. It no longer withholds the action as
        // well -- that cost more than it protected; see the offline test
        // below and DashboardUiState.nextAction.
        val vm = viewModel(Result.failure(IOException("no signal")))
        advanceUntilIdle()

        assertEquals(AttendanceFailure.NoConnection, vm.uiState.value.failure)
        assertNull("an unread day is never shown as a recorded one", vm.uiState.value.record)
    }

    @Test
    fun `loading the dashboard also warms the project cache`() = runTest {
        // Found offline on a device: the project list was only ever
        // cached by opening the picker while online. A worker who had
        // never done that met an empty picker with no signal -- the flow
        // dead at step 1, which is the failure cached_project exists to
        // prevent. The dashboard is the one screen every worker sees, so
        // it is where the cache gets warmed.
        val projects = RecordingProjects()
        val vm = DashboardViewModel(FakeAttendance(Result.success(null)), projects, FakeRewards(), RecordingScheduler())
        advanceUntilIdle()

        assertEquals(1, projects.calls)
    }

    private class RecordingProjects : com.dacs.attendance.data.repo.ProjectRepository {
        var calls = 0
            private set

        override suspend fun activeProjects() = Result.success(emptyList<com.dacs.attendance.domain.AttendanceProject>())
            .also { calls++ }
    }

    @Test
    fun `refresh re-reads the record after a submission`() = runTest {
        val auth = FakeAttendance(Result.success(null))
        val vm = DashboardViewModel(auth, RecordingProjects(), FakeRewards(), RecordingScheduler())
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(2, auth.loads)
    }

    @Test
    fun `a reward strip that cannot be read does not take the screen down with it`() = runTest {
        // The reward is the ONLY read on this screen with no offline
        // fallback, so on a site with no signal it is the one that
        // fails. The TIME IN button is the screen's whole job and has to
        // survive it.
        val vm = DashboardViewModel(
            FakeAttendance(Result.success(null)),
            RecordingProjects(),
            FakeRewards(Result.failure(IOException("no signal"))),
            RecordingScheduler()
        )
        advanceUntilIdle()

        assertTrue(vm.uiState.value.rewardUnavailable)
        assertTrue(vm.uiState.value.rewardWeek.isEmpty())

        assertEquals(TimeDirection.IN, vm.uiState.value.nextAction)
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun `the reward strip is five cells, where the attendance strip is six`() = runTest {
        // Both are true at once: DACs works Saturdays, and the reward
        // only ever asks about Monday to Friday. Drawing one from the
        // other would tell a worker a missed Saturday cost them ₱500.
        val monday = rewardWeekStart(LocalDate.now(com.dacs.attendance.domain.AttendanceZone))
        val days = (0L until 5L).map {
            RewardDay(monday.plusDays(it), required = true, status = RewardDayStatus.OnTime)
        }

        val vm = DashboardViewModel(
            FakeAttendance(Result.success(null)),
            RecordingProjects(),
            FakeRewards(Result.success(days)),
            RecordingScheduler()
        )
        advanceUntilIdle()

        assertEquals(5, vm.uiState.value.rewardWeek.size)
        assertFalse(vm.uiState.value.rewardUnavailable)
        assertEquals(RewardStatus.Qualified, vm.uiState.value.reward?.status)
    }

    @Test
    fun `with no signal and nothing mirrored, the worker can still time in`() = runTest {
        // 07:45 on a site with no bars, first action of the day. The
        // dashboard used to hide the Time In button here, so a worker
        // reached it and could record nothing -- which is the exact
        // morning the whole offline layer exists for.
        val vm = viewModel(Result.failure(IOException("no signal")))
        advanceUntilIdle()

        assertEquals(AttendanceFailure.NoConnection, vm.uiState.value.failure)
        assertEquals(TimeDirection.IN, vm.uiState.value.nextAction)
    }

    @Test
    fun `a server refusal still withholds the action`() = runTest {
        // Unreachable and refusing are different. The caution stays where
        // the server actually answered.
        val vm = viewModel(Result.failure(IllegalStateException("ACCOUNT_INACTIVE")))
        advanceUntilIdle()

        assertNull(vm.uiState.value.nextAction)
    }

    @Test
    fun `opening Home asks the queue to drain`() = runTest {
        // A worker walking back into coverage opens the app and sees "Not
        // sent yet". WorkManager's backoff doubles on every failed
        // attempt, so after a morning with no signal the next try can be
        // ten minutes away -- measured at seven on a real device. Opening
        // Home is the clearest evidence a human is present and probably
        // back in signal, so it starts a fresh attempt rather than making
        // them wait out a delay they cannot see.
        val scheduler = RecordingScheduler()
        DashboardViewModel(
            FakeAttendance(Result.success(null)),
            RecordingProjects(),
            FakeRewards(),
            scheduler
        )
        advanceUntilIdle()

        assertEquals(1, scheduler.kicks)
    }
}
