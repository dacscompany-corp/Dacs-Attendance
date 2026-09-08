package com.dacs.attendance.ui.timeflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dacs.attendance.data.repo.AttendanceRepository
import com.dacs.attendance.data.repo.ProjectRepository
import com.dacs.attendance.data.repo.SubmissionRequest
import com.dacs.attendance.domain.AttendanceFailure
import com.dacs.attendance.domain.AttendanceProject
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.TimeDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The four steps of the design, plus the confirmation that ends them. */
enum class FlowStep { PickProject, TakePhoto, CheckPhoto, Describe, Confirmed }

/** A photo on disk and the moment its shutter fired. */
data class CapturedPhoto(val file: File, val capturedAt: Instant)

data class TimeFlowUiState(
    val direction: TimeDirection = TimeDirection.IN,
    val step: FlowStep = FlowStep.PickProject,
    val projects: List<AttendanceProject> = emptyList(),
    val loadingProjects: Boolean = true,
    /**
     * The picked project as "system:id". A bare id is not enough since
     * 0059 -- 'pc' and 'pm' ids come from different tables.
     */
    val selectedProjectKey: String? = null,
    val photo: CapturedPhoto? = null,
    val description: String = "",
    val submitting: Boolean = false,
    val failure: AttendanceFailure? = null,
    val saved: AttendanceRecord? = null
) {
    val selectedProject: AttendanceProject?
        get() = projects.firstOrNull { it.key == selectedProjectKey }

    /** "Step 2 of 4" in the design's counter. Confirmation is not a step. */
    val stepNumber: Int
        get() = when (step) {
            FlowStep.PickProject -> 1
            FlowStep.TakePhoto -> 2
            FlowStep.CheckPhoto -> 3
            FlowStep.Describe -> 4
            FlowStep.Confirmed -> 4
        }
}

/**
 * ONE flow for both directions. The design states Time Out is "the same
 * five, in brown", so direction is a parameter -- colour and copy come
 * from it. Building Time Out separately is how the two halves drift.
 */
@HiltViewModel
class TimeFlowViewModel @Inject constructor(
    private val attendance: AttendanceRepository,
    private val projects: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimeFlowUiState())
    val uiState: StateFlow<TimeFlowUiState> = _uiState.asStateFlow()

    /**
     * The idempotency key for THIS submission, minted once and reused for
     * every retry. A fresh id per attempt would turn one retried Time In
     * into two records for the same shift -- which the RPC could not
     * detect, because detecting it is exactly what this id is for.
     */
    private var eventId: String = UUID.randomUUID().toString()

    fun start(direction: TimeDirection) {
        eventId = UUID.randomUUID().toString()
        _uiState.value = TimeFlowUiState(direction = direction)
        loadProjects()
    }

    private fun loadProjects() {
        viewModelScope.launch {
            projects.activeProjects().fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            projects = list,
                            loadingProjects = false,
                            // Last-used would go here; for now the single
                            // project case still costs one tap, which is
                            // the design's own choice (it wants the
                            // worker to see which project they picked).
                            selectedProjectKey = it.selectedProjectKey
                        )
                    }
                },
                onFailure = { error ->
                    // An empty picker and a failed fetch look identical to
                    // a worker, and only one of them is fixed by waiting.
                    _uiState.update {
                        it.copy(loadingProjects = false, failure = AttendanceFailure.of(error))
                    }
                }
            )
        }
    }

    fun onRetryProjects() {
        _uiState.update { it.copy(loadingProjects = true, failure = null) }
        loadProjects()
    }

    fun onProjectSelected(projectKey: String) =
        _uiState.update { it.copy(selectedProjectKey = projectKey, failure = null) }

    fun onProjectConfirmed() {
        if (_uiState.value.selectedProject == null) return
        _uiState.update { it.copy(step = FlowStep.TakePhoto) }
    }

    fun onPhotoTaken(file: File, capturedAt: Instant) = _uiState.update {
        it.copy(photo = CapturedPhoto(file, capturedAt), step = FlowStep.CheckPhoto, failure = null)
    }

    fun onRetakePhoto() = _uiState.update {
        // Delete rather than orphan: these files pile up in cache, and a
        // rejected photo is never wanted again.
        it.photo?.file?.delete()
        it.copy(photo = null, step = FlowStep.TakePhoto)
    }

    fun onPhotoAccepted() {
        if (_uiState.value.photo == null) return
        _uiState.update { it.copy(step = FlowStep.Describe) }
    }

    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }

    fun onBack() = _uiState.update {
        when (it.step) {
            FlowStep.TakePhoto -> it.copy(step = FlowStep.PickProject)
            FlowStep.CheckPhoto -> it.copy(step = FlowStep.TakePhoto, photo = null)
            FlowStep.Describe -> it.copy(step = FlowStep.CheckPhoto)
            else -> it
        }
    }

    fun onSubmit() {
        val state = _uiState.value
        // Guards the commonest duplicate: a slow phone, no visible
        // feedback, and a worker who taps SUBMIT again.
        if (state.submitting) return

        // The whole project, not just its key: the RPC needs the system
        // and the id as separate arguments.
        val project = state.selectedProject ?: return
        val photo = state.photo ?: return

        _uiState.update { it.copy(submitting = true, failure = null) }
        viewModelScope.launch {
            val result = attendance.submit(
                SubmissionRequest(
                    direction = state.direction,
                    projectSystem = project.system,
                    projectId = project.id,
                    capturedAt = photo.capturedAt,
                    photo = photo.file,
                    description = state.description.trim().takeIf { it.isNotEmpty() },
                    eventId = eventId
                )
            )
            _uiState.update { current ->
                result.fold(
                    onSuccess = { record ->
                        current.copy(
                            submitting = false,
                            saved = record,
                            step = FlowStep.Confirmed
                        )
                    },
                    onFailure = { error ->
                        // Stay on Describe with the photo intact. Dropping
                        // them to the start would mean retaking the photo,
                        // and the photo is the evidence of when they
                        // arrived.
                        current.copy(
                            submitting = false,
                            failure = AttendanceFailure.of(error)
                        )
                    }
                )
            }
        }
    }
}
