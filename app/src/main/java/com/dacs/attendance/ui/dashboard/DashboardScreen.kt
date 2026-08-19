package com.dacs.attendance.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.WorkerProfile
import com.dacs.attendance.ui.components.AttendanceFailureNotice
import com.dacs.attendance.ui.components.DayStepper
import com.dacs.attendance.ui.components.PrimaryActionButton
import com.dacs.attendance.ui.components.StepState
import com.dacs.attendance.ui.theme.Brown
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.GreenBorder
import com.dacs.attendance.ui.theme.GreenTint
import com.dacs.attendance.ui.theme.MonoFamily
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
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
        Text(
            text = stringResource(R.string.greeting_morning),
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted
        )
        Text(
            text = worker.firstName,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = Instant.now().atZone(AttendanceZone).format(DateHeading),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(Dimens.GapSmall))

        DayStepper(
            timeIn = if (state.record == null) StepState.Now else StepState.Done,
            working = if (state.working) StepState.Now else StepState.Locked,
            timeOut = when {
                state.record?.status == AttendanceStatus.COMPLETE -> StepState.Done
                state.working -> StepState.Now
                else -> StepState.Locked
            },
            timeInCaption = stringResource(
                if (state.record == null) R.string.step_time_in_tl else R.string.step_done_tl
            ),
            workingCaption = stringResource(
                if (state.working) R.string.step_working_now_tl else R.string.step_working_tl
            ),
            timeOutCaption = stringResource(
                if (state.working) R.string.step_time_out_now_tl else R.string.step_time_out_tl
            )
        )

        state.failure?.let { AttendanceFailureNotice(it, onRetry = onRetry) }

        when {
            state.record == null && state.failure == null -> Text(
                text = stringResource(R.string.no_time_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            state.record != null -> TodaySummary(
                timeInLabel = state.record?.timeInAt?.atZone(AttendanceZone)?.format(ClockTime),
                projectName = state.record?.timeInProjectName,
                hours = state.totalHoursLabel,
                complete = state.record?.status == AttendanceStatus.COMPLETE
            )
        }

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

@Composable
private fun TodaySummary(
    timeInLabel: String?,
    projectName: String?,
    hours: String,
    complete: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GreenTint, RoundedCornerShape(Dimens.RadiusLarge))
            .border(1.dp, GreenBorder, RoundedCornerShape(Dimens.RadiusLarge))
            .padding(Dimens.GapMedium),
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
            style = MaterialTheme.typography.headlineMedium,
            color = Green
        )
    }
}
