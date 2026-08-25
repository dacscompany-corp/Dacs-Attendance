package com.dacs.attendance.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dacs.attendance.data.repo.AttendanceRepository
import com.dacs.attendance.data.repo.ProjectRepository
import com.dacs.attendance.domain.AttendanceFailure
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.TotalHours
import com.dacs.attendance.ui.components.StepState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val loading: Boolean = true,
    val record: AttendanceRecord? = null,
    val failure: AttendanceFailure? = null,
    val totalHoursLabel: String = TotalHours.NOTHING_YET
) {
    /**
     * The single decision this screen makes. Null means the day is done
     * (or we could not read it) and no hero button is shown.
     */
    val nextAction: TimeDirection?
        get() = when {
            // Nothing is offered until today's record has actually been
            // read. "Not loaded yet" and "no record today" are different
            // facts, and a worker who acts on the first one as though it
            // were the second times in twice.
            loading || failure != null -> null
            record == null -> TimeDirection.IN
            else -> record.nextAction
        }

    val working: Boolean get() = record?.status == AttendanceStatus.WORKING

    private val complete: Boolean get() = record?.status == AttendanceStatus.COMPLETE

    // The 1-2-3 stepper, derived here rather than assembled in the
    // composable. A finished day must read as finished on all three: the
    // stepper is the first thing a worker looks at, and it was saying
    // "Working: not yet" directly above "TOTAL HOURS 5h 45m".
    val timeInStep: StepState
        get() = if (record == null) StepState.Now else StepState.Done

    val workingStep: StepState
        get() = when {
            complete -> StepState.Done
            working -> StepState.Now
            else -> StepState.Locked
        }

    val timeOutStep: StepState
        get() = when {
            complete -> StepState.Done
            working -> StepState.Now
            else -> StepState.Locked
        }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val attendance: AttendanceRepository,
    private val projects: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(loading = true, failure = null) }

        // Warm the project cache while we have signal. The picker reads
        // it offline, and a worker who has never opened the picker while
        // online would otherwise reach step 1 of the flow and find
        // nothing there. The result is deliberately ignored -- this is a
        // cache fill, not something the dashboard displays.
        viewModelScope.launch { projects.activeProjects() }

        viewModelScope.launch {
            attendance.today().fold(
                onSuccess = { record ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            record = record,
                            failure = null,
                            totalHoursLabel = labelFor(record, Instant.now())
                        )
                    }
                },
                onFailure = { error ->
                    // Deliberately does NOT fall back to "no record yet":
                    // that reads as "you have not timed in", and a worker
                    // acting on it times in twice.
                    _uiState.update {
                        it.copy(loading = false, failure = AttendanceFailure.of(error))
                    }
                }
            )
        }
    }

    /** Drives the live "HOURS SO FAR" while a shift is open. */
    fun onTick(now: Instant = Instant.now()) = _uiState.update {
        it.copy(totalHoursLabel = labelFor(it.record, now))
    }

    private fun labelFor(record: AttendanceRecord?, now: Instant): String = when {
        record == null -> TotalHours.NOTHING_YET
        record.status == AttendanceStatus.WORKING && record.timeInAt != null ->
            TotalHours.since(record.timeInAt, now)
        // Once the day is closed the server's total is authoritative --
        // never recompute it here, or the worker and the admin report can
        // disagree by a minute of rounding.
        else -> TotalHours.format(record.totalMinutes)
    }
}
