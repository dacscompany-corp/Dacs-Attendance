package com.dacs.attendance.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dacs.attendance.R
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.AttendanceZone
import com.dacs.attendance.domain.Greeting
import com.dacs.attendance.domain.greetingAt
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.WorkerProfile
import com.dacs.attendance.ui.components.AttendanceFailureNotice
import com.dacs.attendance.ui.components.DayStepper
import com.dacs.attendance.ui.components.PrimaryActionButton
import com.dacs.attendance.ui.components.StepState
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Brown
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.GreenTint
import com.dacs.attendance.ui.theme.Hairline
import com.dacs.attendance.ui.theme.MonoFamily
import com.dacs.attendance.ui.theme.Surface
import com.dacs.attendance.ui.theme.TextDisabled
import com.dacs.attendance.ui.theme.TextMuted
import com.dacs.attendance.ui.theme.TextSecondary
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val DateHeading = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")
private val ClockTime = DateTimeFormatter.ofPattern("h:mm a")

/**
 * Screens 03 and 09 -- the same screen in two states, because they are
 * the same screen. Everything on it is derived from today's record, so
 * "what may this worker do next" is decided in exactly one place
 * (DashboardUiState.nextAction) rather than inferred per widget.
 */
@Composable
fun DashboardScreen(
    worker: WorkerProfile,
    onStartFlow: (TimeDirection) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Bumped by the caller after a submission. The ViewModel is scoped to
     * the Activity, so it OUTLIVES this composable -- recreating the
     * composable (or keying it) does not re-run its init, and the
     * dashboard would keep showing the state from before the worker timed
     * in. This is the explicit re-read.
     */
    refreshKey: Int = 0,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) viewModel.refresh()
    }

    // The live "HOURS SO FAR". A minute is the smallest unit anyone reads
    // here, so ticking faster would just burn battery on a phone that has
    // to last a full shift.
    LaunchedEffect(state.working) {
        while (state.working) {
            viewModel.onTick(Instant.now())
            delay(30_000)
        }
    }

    DashboardContent(
        worker = worker,
        state = state,
        onStartFlow = onStartFlow,
        onRetry = viewModel::refresh,
        modifier = modifier
    )
}

/**
 * The dashboard with no ViewModel attached.
 *
 * Split out so every state -- idle, working, complete, offline -- can be
 * rendered in a preview and looked at without a device. The states differ
 * only by what today's record says, and seeing them side by side is how
 * you catch a stepper that lies.
 */
@Composable
fun DashboardContent(
    worker: WorkerProfile,
    state: DashboardUiState,
    onStartFlow: (TimeDirection) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
    ) {
        // Greeting, name, "Mason · W-0003", and the avatar -- the design's
        // header. The position and worker number are there because a
        // shared site phone is passed between people, and the first thing
        // to check before tapping TIME IN is whose account this is.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        when (greetingAt(Instant.now())) {
                            Greeting.MORNING -> R.string.greeting_morning
                            Greeting.AFTERNOON -> R.string.greeting_afternoon
                            Greeting.EVENING -> R.string.greeting_evening
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextMuted
                )
                Text(
                    text = worker.firstName,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = worker.positionAndId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(GreenTint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = worker.initials,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Green
                )
            }
        }

        // One card holding the day: date, the three steps, and where it
        // stands. The design groups them because they are one thought --
        // "what is today, and how far into it am I".
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface, RoundedCornerShape(Dimens.RadiusHero))
                .border(1.dp, BorderDefault, RoundedCornerShape(Dimens.RadiusHero))
                .padding(Dimens.GapMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarToday,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = Instant.now().atZone(AttendanceZone).format(DateHeading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            DayStepper(
                timeIn = state.timeInStep,
                working = state.workingStep,
                timeOut = state.timeOutStep,
                timeInCaption = stringResource(state.timeInStep.captionFor(R.string.step_time_in_tl)),
                workingCaption = stringResource(state.workingStep.captionFor(R.string.step_working_now_tl)),
                // "Naka-lock", not the generic "Hindi pa": Time Out is
                // locked BY the day, and the design says so by name.
                timeOutCaption = stringResource(
                    state.timeOutStep.captionFor(
                        nowCaption = R.string.step_time_out_now_tl,
                        lockedCaption = R.string.step_time_out_tl
                    )
                )
            )

            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))

            when {
                state.loading -> CircularProgressIndicator(
                    color = Green,
                    modifier = Modifier.size(24.dp)
                )

                state.record != null -> TodaySummary(
                    timeInLabel = state.record?.timeInAt?.atZone(AttendanceZone)?.format(ClockTime),
                    projectName = state.record?.timeInProjectName,
                    hours = state.totalHoursLabel,
                    complete = state.record?.status == AttendanceStatus.COMPLETE,
                    pending = state.record?.pending == true
                )

                else -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.no_time_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    // The design's placeholder. A dash pair reads as "no
                    // figure yet"; "0h 0m" would read as a day worked to
                    // no hours, which is a different and untrue thing.
                    Text(
                        text = "— : —",
                        fontFamily = MonoFamily,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextDisabled
                    )
                }
            }
        }

        state.failure?.let { AttendanceFailureNotice(it, onRetry = onRetry) }

        Spacer(Modifier.weight(1f))

        when (state.nextAction) {
            TimeDirection.IN -> PrimaryActionButton(
                english = stringResource(R.string.action_time_in),
                tagalog = stringResource(R.string.action_time_in_hint),
                onClick = { onStartFlow(TimeDirection.IN) },
                container = Green,
                modifier = Modifier.height(Dimens.HeroMaxHeight)
            )

            TimeDirection.OUT -> PrimaryActionButton(
                english = stringResource(R.string.action_time_out),
                tagalog = stringResource(R.string.action_time_out_hint),
                onClick = { onStartFlow(TimeDirection.OUT) },
                container = Brown,
                modifier = Modifier.height(Dimens.HeroMaxHeight)
            )

            // Day closed, or we could not read it. Offering TIME IN here
            // could only ever produce ALREADY_TIMED_IN.
            null -> if (state.failure == null && !state.loading) {
                Text(
                    text = stringResource(R.string.day_complete),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Green,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Where today stands, inside the day card rather than in a card of its
 * own -- the design puts the hours on the same line as their label, at
 * the foot of the same panel the stepper is in.
 */
@Composable
private fun TodaySummary(
    timeInLabel: String?,
    projectName: String?,
    hours: String,
    complete: Boolean,
    pending: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (timeInLabel != null) {
            Text(
                text = stringResource(R.string.timed_in_at, timeInLabel),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
        if (projectName != null) {
            Text(
                text = projectName,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    if (complete) R.string.hours_total else R.string.hours_so_far
                ),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Text(
                text = hours,
                fontFamily = MonoFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                color = Green
            )
        }
        if (pending) {
            // Reassurance, not a warning. The record is safe on the
            // phone and there is nothing for the worker to do about it.
            Text(
                text = stringResource(R.string.will_sync),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

/**
 * The caption under a step. Done and Locked read the same in every
 * column, so only the "now" wording differs per step -- which is why it
 * is the only one passed in.
 */
private fun StepState.captionFor(
    nowCaption: Int,
    lockedCaption: Int = R.string.step_locked_tl
): Int = when (this) {
    StepState.Done -> R.string.step_done_tl
    StepState.Now -> nowCaption
    StepState.Locked -> lockedCaption
}
