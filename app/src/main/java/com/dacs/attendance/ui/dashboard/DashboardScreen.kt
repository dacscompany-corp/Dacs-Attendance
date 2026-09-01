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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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
import com.dacs.attendance.ui.components.StepState
import com.dacs.attendance.ui.theme.BodyFamily
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Brown
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.GreenBorder
import com.dacs.attendance.ui.theme.GreenPressed
import com.dacs.attendance.ui.theme.GreenTint
import com.dacs.attendance.ui.theme.Hairline
import com.dacs.attendance.ui.theme.MonoFamily
import com.dacs.attendance.ui.theme.Surface
import com.dacs.attendance.ui.theme.SurfaceRaised
import com.dacs.attendance.ui.theme.TextPrimary
import com.dacs.attendance.ui.theme.TextDisabled
import com.dacs.attendance.ui.theme.TextMuted
import com.dacs.attendance.ui.theme.TextSecondary
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    val record = state.record
    val today = Instant.now().atZone(AttendanceZone).format(DateHeading)

    Column(modifier = modifier.fillMaxSize().background(SurfaceRaised)) {
        // A white bar with a rule under it, as the design draws both
        // states. Once the worker has timed in the greeting goes and the
        // date moves up here -- the morning hello has done its job, and
        // what matters mid-shift is which day this record belongs to.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (record == null) {
                    Text(
                        text = stringResource(
                            when (greetingAt(Instant.now())) {
                                Greeting.MORNING -> R.string.greeting_morning
                                Greeting.AFTERNOON -> R.string.greeting_afternoon
                                Greeting.EVENING -> R.string.greeting_evening
                            }
                        ),
                        fontSize = 15.sp,
                        color = TextMuted
                    )
                }
                Text(
                    text = worker.displayName ?: worker.firstName,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    // Who you are before you have timed in; WHICH DAY once
                    // you have.
                    text = if (record == null) worker.positionAndId else today,
                    fontSize = 14.sp,
                    color = TextMuted
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(GreenTint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = worker.initials,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Green
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderDefault))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
        ) {
            state.failure?.let { AttendanceFailureNotice(it, onRetry = onRetry) }

            when {
                state.loading -> Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    CircularProgressIndicator(color = Green)
                }

                // Nothing recorded yet: the date, the action, and the
                // three steps of the day ahead.
                record == null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = today,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }

                    ActionFor(state.nextAction, onStartFlow, state.failure, state.loading)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Surface, RoundedCornerShape(Dimens.RadiusLarge))
                            .border(1.dp, BorderDefault, RoundedCornerShape(Dimens.RadiusLarge))
                            .padding(horizontal = Dimens.GapMedium, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        DayStepper(
                            timeIn = state.timeInStep,
                            working = state.workingStep,
                            timeOut = state.timeOutStep,
                            timeInCaption = stringResource(
                                state.timeInStep.captionFor(R.string.step_time_in_tl)
                            ),
                            workingCaption = stringResource(
                                state.workingStep.captionFor(R.string.step_working_now_tl)
                            ),
                            // "Naka-lock", not the generic "Hindi pa":
                            // Time Out is locked BY the day, and the
                            // design says so by name.
                            timeOutCaption = stringResource(
                                state.timeOutStep.captionFor(
                                    nowCaption = R.string.step_time_out_now_tl,
                                    lockedCaption = R.string.step_time_out_tl
                                )
                            )
                        )

                        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.no_time_yet),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                            // A dash pair reads as "no figure yet";
                            // "0h 0m" would read as a day worked to no
                            // hours, which is a different, untrue thing.
                            Text(
                                text = "— : —",
                                fontFamily = MonoFamily,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextDisabled
                            )
                        }
                    }
                }

                // Timed in: what was recorded, the running total, and the
                // way out. No stepper -- the day has moved past it.
                else -> {
                    TimedInCard(
                        timeInLabel = record.timeInAt?.atZone(AttendanceZone)?.format(ClockTime),
                        projectName = record.timeInProjectName,
                        pending = record.pending
                    )
                    HoursCard(
                        hours = state.totalHoursLabel,
                        complete = record.status == AttendanceStatus.COMPLETE
                    )
                    ActionFor(state.nextAction, onStartFlow, state.failure, state.loading)
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

/** TIME IN, TIME OUT, or the sentence that ends the day. */
@Composable
private fun ActionFor(
    next: TimeDirection?,
    onStartFlow: (TimeDirection) -> Unit,
    failure: com.dacs.attendance.domain.AttendanceFailure?,
    loading: Boolean
) {
    when (next) {
        TimeDirection.IN -> HeroActionButton(
            english = stringResource(R.string.action_time_in),
            tagalog = stringResource(R.string.action_time_in_hint),
            icon = Icons.AutoMirrored.Filled.Login,
            container = Green,
            onClick = { onStartFlow(TimeDirection.IN) }
        )

        TimeDirection.OUT -> HeroActionButton(
            english = stringResource(R.string.action_time_out),
            tagalog = stringResource(R.string.action_time_out_hint),
            icon = Icons.AutoMirrored.Filled.Logout,
            container = Brown,
            onClick = { onStartFlow(TimeDirection.OUT) }
        )

        // Day closed, or we could not read it. Offering TIME IN here
        // could only ever produce ALREADY_TIMED_IN.
        null -> if (failure == null && !loading) {
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

/** "Timed in at 7:45 AM" over the project, with the tick that says so. */
@Composable
private fun TimedInCard(timeInLabel: String?, projectName: String?, pending: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GreenTint, RoundedCornerShape(Dimens.RadiusLarge))
            .border(1.dp, GreenBorder, RoundedCornerShape(Dimens.RadiusLarge))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Green, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = timeInLabel?.let { stringResource(R.string.timed_in_at, it) }
                    ?: stringResource(R.string.status_working),
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GreenPressed
            )
            projectName?.let {
                Text(text = it, fontSize = 14.sp, color = Green)
            }
            if (pending) {
                // Reassurance, not a warning. The record is safe on the
                // phone and there is nothing for the worker to fix.
                Text(
                    text = stringResource(R.string.will_sync),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

/** The running total, label on the left and the figure on the right. */
@Composable
private fun HoursCard(hours: String, complete: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(Dimens.RadiusLarge))
            .border(1.dp, BorderDefault, RoundedCornerShape(Dimens.RadiusLarge))
            .padding(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (complete) R.string.hours_total else R.string.hours_so_far
                ).uppercase(Locale.getDefault()),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.em,
                color = TextMuted
            )
            Text(
                // Switches WITH the English above it. Leaving the Tagalog
                // on "hanggang ngayon" while the English said TOTAL HOURS
                // told a worker whose day was closed that the clock was
                // still running.
                text = stringResource(
                    if (complete) R.string.hours_total_tl else R.string.hours_so_far_tl
                ),
                fontSize = 14.sp,
                color = TextMuted
            )
        }
        Text(
            text = hours,
            fontFamily = MonoFamily,
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}


/**
 * The dashboard's one action, as the design draws it: a full-bleed panel
 * with the icon in a translucent disc above the words.
 *
 * Not [PrimaryActionButton] -- that one is 76dp and belongs on the flow
 * screens. This is the target a worker hits first thing in the morning
 * with gloves on, and the design gives it up to 230dp for the purpose.
 */
@Composable
private fun HeroActionButton(
    english: String,
    tagalog: String,
    icon: ImageVector,
    container: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 190.dp, max = Dimens.HeroMaxHeight),
        shape = RoundedCornerShape(Dimens.RadiusHero),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = Color.White
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .background(Color.White.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = english,
                    fontFamily = BodyFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 34.sp,
                    lineHeight = 36.sp
                )
                Text(
                    text = tagalog,
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
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
