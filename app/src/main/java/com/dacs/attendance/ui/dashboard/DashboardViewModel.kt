package com.dacs.attendance.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dacs.attendance.data.repo.AttendanceRepository
import com.dacs.attendance.data.repo.ProjectRepository
import com.dacs.attendance.data.repo.RewardRepository
import com.dacs.attendance.domain.AttendanceFailure
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.HistorySpan
import com.dacs.attendance.domain.TotalHours
import com.dacs.attendance.domain.WeekDayCell
import com.dacs.attendance.domain.historyRange
import com.dacs.attendance.domain.weekStrip
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import com.dacs.attendance.domain.AttendanceZone
import com.dacs.attendance.domain.RewardCell
import com.dacs.attendance.domain.RewardSummary
import com.dacs.attendance.domain.rewardCells
import com.dacs.attendance.domain.rewardSummary
import com.dacs.attendance.domain.rewardWeekStart
import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One of the three states a step of the day can be in. */
enum class StepState { Done, Now, Locked }

data class DashboardUiState(
    val loading: Boolean = true,
    val record: AttendanceRecord? = null,
    val failure: AttendanceFailure? = null,
    val totalHoursLabel: String = TotalHours.NOTHING_YET,
    /**
     * The same figure as [totalHoursLabel], as a number, for the meter
     * under it. Kept beside the label rather than re-derived in the
     * composable so the bar and the text can never disagree about how
     * long the worker has been on site.
     */
    val totalMinutes: Int? = null,
    /**
     * Monday to Saturday, for the strip at the foot of the design's Home
     * screen. Empty until the week has been read -- which draws six
     * unfilled cells, the same as a week with nothing in it yet. That is
     * the one honest ambiguity here: the strip claims only "there is a
     * record", and a cell cannot say "still loading" in 8dp of dot.
     */
    val week: List<WeekDayCell> = emptyList(),
    /**
     * The Monday-to-FRIDAY reward strip -- five cells, where [week] has
     * six. Both are true at once: DACs works Saturdays, and the reward
     * only ever asks about Mon-Fri. Folding one into the other would
     * tell a worker that missing a Saturday cost them the money.
     */
    val rewardWeek: List<RewardCell> = emptyList(),
    val reward: RewardSummary? = null,
    /**
     * The server could not be reached, or refused.
     *
     * Distinct from an empty [rewardWeek], which only means "not read
     * yet". This is the one part of the screen with no offline answer,
     * deliberately (see SupabaseRewardRepository), so it has to be able
     * to say so rather than draw five empty cells that read as five
     * missed days.
     */
    val rewardUnavailable: Boolean = false,
    /**
     * What a qualifying week pays. Null until read, and null stays null
     * -- the strip reports the standing without a figure rather than
     * naming ₱500 the database never agreed to.
     */
    val rewardAmount: Double? = null
) {
    /**
     * The strip as it is actually drawn.
     *
     * [week] comes from the SERVER's history; [record] comes from the
     * local mirror, which knows about a Time In that is still queued. So
     * the two disagree about today for as long as a submission is
     * unsent -- and the screen was showing "Timed in at 7:45" directly
     * above an empty dot for today.
     *
     * Only ever turns a day ON. A missing local record is not evidence
     * that a day the server has was not worked.
     */
    val weekWithToday: List<WeekDayCell>
        get() = if (record == null) {
            week
        } else {
            week.map { cell -> if (cell.isToday) cell.copy(worked = true) else cell }
        }

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
    private val projects: ProjectRepository,
    private val rewards: RewardRepository
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

        // The reward strip. Separate again, for the same reason, and
        // with more force: this is the only read on the screen with no
        // offline fallback at all, so on a site with no signal it is the
        // one that WILL fail. It must not take anything else with it.
        viewModelScope.launch {
            val today = LocalDate.now(AttendanceZone)
            val weekStart = rewardWeekStart(today)
            // The amount is a separate, failable read for the same
            // reason: a missing config row must cost the strip its
            // figure, not its standing.
            rewards.rewardAmount().onSuccess { amount ->
                _uiState.update { it.copy(rewardAmount = amount) }
            }

            rewards.weekProgress(weekStart)
                .onSuccess { days ->
                    _uiState.update {
                        it.copy(
                            rewardWeek = rewardCells(weekStart, days, today),
                            reward = rewardSummary(days, today),
                            rewardUnavailable = false
                        )
                    }
                }
                .onFailure {
                    // No guess, and no stale figure either. The strip
                    // disappears and says why.
                    _uiState.update { s -> s.copy(rewardUnavailable = true) }
                }
        }

        // The week strip. Read separately from today so a failure here
        // cannot take the TIME IN button down with it -- the strip is a
        // glance, and today's record is the screen's whole job.
        viewModelScope.launch {
            val range = historyRange(HistorySpan.WEEK)
            attendance.history(range.start.toString(), range.endInclusive.toString())
                .onSuccess { records ->
                    _uiState.update { it.copy(week = weekStrip(records)) }
                }
        }

        viewModelScope.launch {
            attendance.today().fold(
                onSuccess = { record ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            record = record,
                            failure = null,
                            totalHoursLabel = labelFor(record, Instant.now()),
                            totalMinutes = minutesFor(record, Instant.now())
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

    /** Drives the live "Hours so far" while a shift is open. */
    fun onTick(now: Instant = Instant.now()) = _uiState.update {
        it.copy(
            totalHoursLabel = labelFor(it.record, now),
            totalMinutes = minutesFor(it.record, now)
        )
    }

    /**
     * The elapsed figure as a number, by the same rule [labelFor] uses.
     *
     * Null means "no figure to show" -- no record, or a day the server
     * has not totalled yet -- which the meter draws as an empty track
     * rather than as zero hours worked.
     */
    private fun minutesFor(record: AttendanceRecord?, now: Instant): Int? = when {
        record == null -> null
        record.status == AttendanceStatus.WORKING && record.timeInAt != null ->
            java.time.Duration.between(record.timeInAt, now).toMinutes()
                .coerceAtLeast(0).toInt()
        else -> record.totalMinutes
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
