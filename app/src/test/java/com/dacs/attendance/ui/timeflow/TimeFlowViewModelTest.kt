package com.dacs.attendance.ui.timeflow

import com.dacs.attendance.data.repo.AttendanceRepository
import com.dacs.attendance.data.repo.ProjectRepository
import com.dacs.attendance.data.repo.SubmissionRequest
import com.dacs.attendance.domain.AttendanceFailure
import com.dacs.attendance.domain.AttendanceProject
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.ProjectSystem
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.support.MainDispatcherRule
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The four-step flow: project -> photo -> check -> description -> submit.
 * One flow for both directions; Time Out is the same five screens in
 * brown. Building it twice is how the two halves drift.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimeFlowViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // One from each project system: since 0059 the picker merges folders
    // ('pc') and construction_projects ('pm'), and a project is the PAIR.
    private val projects = listOf(
        AttendanceProject(ProjectSystem.PC, "11111111-1111-4111-8111-111111111111", "ABC Building Project"),
        AttendanceProject(ProjectSystem.PM, "22222222-2222-4222-8222-222222222222", "Residential Project")
    )
    private val first = projects[0]
    private val second = projects[1]

    private val savedRecord = AttendanceRecord(
        id = "rec-1",
        workDate = "2026-08-19",
        status = AttendanceStatus.WORKING,
        timeInAt = Instant.parse("2026-08-18T23:45:00Z"),
        timeOutAt = null,
        timeInProjectName = "ABC Building Project",
        timeOutProjectName = null,
        totalMinutes = null
    )

    private class FakeProjects(
        private val result: Result<List<AttendanceProject>> = Result.success(emptyList())
    ) : ProjectRepository {
        override suspend fun activeProjects() = result
    }

    private class FakeAttendance(
        private val results: MutableList<Result<AttendanceRecord>>
    ) : AttendanceRepository {
        val requests = mutableListOf<SubmissionRequest>()

        override suspend fun submit(request: SubmissionRequest): Result<AttendanceRecord> {
            requests += request
            return if (results.size > 1) results.removeAt(0) else results.first()
        }

        override suspend fun history(fromWorkDate: String, toWorkDate: String) =
            Result.success(emptyList<AttendanceRecord>())

        override suspend fun today(): Result<AttendanceRecord?> = Result.success(null)
    }

    private fun viewModel(
        attendance: AttendanceRepository,
        projectRepo: ProjectRepository = FakeProjects(Result.success(projects)),
        direction: TimeDirection = TimeDirection.IN
    ) = TimeFlowViewModel(attendance, projectRepo).also { it.start(direction) }

    private fun photoFile() = File.createTempFile("photo", ".jpg").apply { deleteOnExit() }

    // ── Getting through the steps ───────────────────────────────────

    @Test
    fun `the flow opens on the project list`() = runTest {
        val vm = viewModel(FakeAttendance(mutableListOf(Result.success(savedRecord))))
        advanceUntilIdle()

        assertEquals(FlowStep.PickProject, vm.uiState.value.step)
        assertEquals(projects, vm.uiState.value.projects)
    }

    @Test
    fun `a worker cannot leave the project step without choosing one`() = runTest {
        val vm = viewModel(FakeAttendance(mutableListOf(Result.success(savedRecord))))
        advanceUntilIdle()

        vm.onProjectConfirmed()

        assertEquals(FlowStep.PickProject, vm.uiState.value.step)
    }

    @Test
    fun `choosing a project opens the camera`() = runTest {
        val vm = viewModel(FakeAttendance(mutableListOf(Result.success(savedRecord))))
        advanceUntilIdle()

        vm.onProjectSelected(second.key)
        vm.onProjectConfirmed()

        assertEquals(FlowStep.TakePhoto, vm.uiState.value.step)
    }

    @Test
    fun `a taken photo goes to the check screen, and retake goes back`() = runTest {
        val vm = viewModel(FakeAttendance(mutableListOf(Result.success(savedRecord))))
        advanceUntilIdle()
        vm.onProjectSelected(first.key)
        vm.onProjectConfirmed()

        vm.onPhotoTaken(photoFile(), Instant.parse("2026-08-18T23:45:00Z"))
        assertEquals(FlowStep.CheckPhoto, vm.uiState.value.step)

        vm.onRetakePhoto()
        assertEquals(FlowStep.TakePhoto, vm.uiState.value.step)
        assertNull(vm.uiState.value.photo)
    }

    // ── What actually gets submitted ────────────────────────────────

    @Test
    fun `captured_at is when the photo was taken, not when submit was tapped`() = runTest {
        // The photo IS the evidence of when the worker was there. Sending
        // the submit time instead would quietly overstate a Time In made
        // after walking back into signal.
        val attendance = FakeAttendance(mutableListOf(Result.success(savedRecord)))
        val vm = viewModel(attendance)
        advanceUntilIdle()

        val shutter = Instant.parse("2026-08-18T23:45:00Z")
        vm.onProjectSelected(first.key)
        vm.onProjectConfirmed()
        vm.onPhotoTaken(photoFile(), shutter)
        vm.onPhotoAccepted()
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(shutter, attendance.requests.single().capturedAt)
    }

    @Test
    fun `an empty description is sent as nothing, not as an empty string`() = runTest {
        val attendance = FakeAttendance(mutableListOf(Result.success(savedRecord)))
        val vm = viewModel(attendance)
        advanceUntilIdle()
        vm.onProjectSelected(first.key)
        vm.onProjectConfirmed()
        vm.onPhotoTaken(photoFile(), Instant.parse("2026-08-18T23:45:00Z"))
        vm.onPhotoAccepted()

        vm.onDescriptionChange("   ")
        vm.onSubmit()
        advanceUntilIdle()

        assertNull(attendance.requests.single().description)
    }

    @Test
    fun `the chosen project and direction reach the request`() = runTest {
        val attendance = FakeAttendance(mutableListOf(Result.success(savedRecord)))
        val vm = viewModel(attendance, direction = TimeDirection.OUT)
        advanceUntilIdle()
        vm.onProjectSelected(second.key)
        vm.onProjectConfirmed()
        vm.onPhotoTaken(photoFile(), Instant.parse("2026-08-19T09:30:00Z"))
        vm.onPhotoAccepted()
        vm.onSubmit()
        advanceUntilIdle()

        val request = attendance.requests.single()
        assertEquals(second.id, request.projectId)
        assertEquals(ProjectSystem.PM, request.projectSystem)
        assertEquals(TimeDirection.OUT, request.direction)
    }

    // ── The idempotency guarantee ───────────────────────────────────

    @Test
    fun `a retry after a failure reuses the SAME event id`() = runTest {
        // This is the property the whole offline queue will rest on in B4:
        // the RPC is idempotent on event_id, so a replayed submission
        // returns the existing row instead of creating a second one. A
        // fresh id per attempt would produce two records for one shift.
        val attendance = FakeAttendance(
            mutableListOf(
                Result.failure(IOException("no signal")),
                Result.success(savedRecord)
            )
        )
        val vm = viewModel(attendance)
        advanceUntilIdle()
        vm.onProjectSelected(first.key)
        vm.onProjectConfirmed()
        vm.onPhotoTaken(photoFile(), Instant.parse("2026-08-18T23:45:00Z"))
        vm.onPhotoAccepted()

        vm.onSubmit()
        advanceUntilIdle()
        assertEquals(AttendanceFailure.NoConnection, vm.uiState.value.failure)

        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(2, attendance.requests.size)
        assertEquals(attendance.requests[0].eventId, attendance.requests[1].eventId)
    }

    @Test
    fun `two separate flows do not share an event id`() = runTest {
        val attendance = FakeAttendance(mutableListOf(Result.success(savedRecord)))
        val first = viewModel(attendance)
        advanceUntilIdle()
        first.onProjectSelected(this@TimeFlowViewModelTest.first.key)
        first.onProjectConfirmed()
        first.onPhotoTaken(photoFile(), Instant.parse("2026-08-18T23:45:00Z"))
        first.onPhotoAccepted()
        first.onSubmit()
        advanceUntilIdle()

        val second = viewModel(attendance, direction = TimeDirection.OUT)
        advanceUntilIdle()
        second.onProjectSelected(this@TimeFlowViewModelTest.first.key)
        second.onProjectConfirmed()
        second.onPhotoTaken(photoFile(), Instant.parse("2026-08-19T09:30:00Z"))
        second.onPhotoAccepted()
        second.onSubmit()
        advanceUntilIdle()

        assertEquals(2, attendance.requests.size)
        assertTrue(attendance.requests[0].eventId != attendance.requests[1].eventId)
    }

    // ── Outcomes ────────────────────────────────────────────────────

    @Test
    fun `a successful submit lands on the confirmation with the saved record`() = runTest {
        val vm = viewModel(FakeAttendance(mutableListOf(Result.success(savedRecord))))
        advanceUntilIdle()
        vm.onProjectSelected(first.key)
        vm.onProjectConfirmed()
        vm.onPhotoTaken(photoFile(), Instant.parse("2026-08-18T23:45:00Z"))
        vm.onPhotoAccepted()
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(FlowStep.Confirmed, vm.uiState.value.step)
        assertNotNull(vm.uiState.value.saved)
        assertFalse(vm.uiState.value.submitting)
    }

    @Test
    fun `a refused submit keeps the worker on the description step with the reason`() = runTest {
        // Never drop them back to the start: they would have to retake the
        // photo, and the photo is the evidence of when they arrived.
        val refusal = RuntimeException("""{"code":"P0001","message":"ALREADY_TIMED_IN"}""")
        val vm = viewModel(FakeAttendance(mutableListOf(Result.failure(refusal))))
        advanceUntilIdle()
        vm.onProjectSelected(first.key)
        vm.onProjectConfirmed()
        vm.onPhotoTaken(photoFile(), Instant.parse("2026-08-18T23:45:00Z"))
        vm.onPhotoAccepted()
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(FlowStep.Describe, vm.uiState.value.step)
        assertEquals(AttendanceFailure.AlreadyTimedIn, vm.uiState.value.failure)
        assertNotNull(vm.uiState.value.photo)
    }

    @Test
    fun `a double tap on submit sends one request, not two`() = runTest {
        // The single most likely way to create a duplicate: a slow phone,
        // no visible feedback, and a worker who taps again.
        val attendance = FakeAttendance(mutableListOf(Result.success(savedRecord)))
        val vm = viewModel(attendance)
        advanceUntilIdle()
        vm.onProjectSelected(first.key)
        vm.onProjectConfirmed()
        vm.onPhotoTaken(photoFile(), Instant.parse("2026-08-18T23:45:00Z"))
        vm.onPhotoAccepted()

        vm.onSubmit()
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(1, attendance.requests.size)
    }

    @Test
    fun `a project list that will not load is reported, not shown as empty`() = runTest {
        // An empty picker and a failed fetch look identical to a worker,
        // and one of them is fixable by waiting.
        val vm = viewModel(
            attendance = FakeAttendance(mutableListOf(Result.success(savedRecord))),
            projectRepo = FakeProjects(Result.failure(IOException("no signal")))
        )
        advanceUntilIdle()

        assertEquals(AttendanceFailure.NoConnection, vm.uiState.value.failure)
        assertTrue(vm.uiState.value.projects.isEmpty())
    }
}
