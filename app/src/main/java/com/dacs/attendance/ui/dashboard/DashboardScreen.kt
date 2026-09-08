package com.dacs.attendance.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dacs.attendance.R
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.AttendanceZone
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.RewardCell
import com.dacs.attendance.domain.RewardCellState
import com.dacs.attendance.domain.RewardStatus
import com.dacs.attendance.domain.RewardSummary
import com.dacs.attendance.domain.WeekDayCell
import com.dacs.attendance.domain.WorkerProfile
import com.dacs.attendance.ui.components.AppCard
import com.dacs.attendance.ui.components.AttendanceFailureNotice
import com.dacs.attendance.ui.components.CardDivider
import com.dacs.attendance.ui.components.IconTile
import com.dacs.attendance.ui.components.SectionLabel
import com.dacs.attendance.ui.components.StatusPill
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Brown
import com.dacs.attendance.ui.theme.BrownDeep
import com.dacs.attendance.ui.theme.BrownTint
import com.dacs.attendance.ui.theme.Danger
import com.dacs.attendance.ui.theme.DangerBorder
import com.dacs.attendance.ui.theme.DangerTint
import com.dacs.attendance.ui.theme.BrownLight
import com.dacs.attendance.ui.theme.Canvas
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.GreenDeep
import com.dacs.attendance.ui.theme.GreenTint
import com.dacs.attendance.ui.theme.Hairline
import com.dacs.attendance.ui.theme.TextLabel
import com.dacs.attendance.ui.theme.MonoFamily
import com.dacs.attendance.ui.theme.Surface
import com.dacs.attendance.ui.theme.SurfaceRaised
import com.dacs.attendance.ui.theme.TextFaint
import com.dacs.attendance.ui.theme.Vacant
import com.dacs.attendance.ui.theme.TextDisabled
import com.dacs.attendance.ui.theme.TextMuted
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val DayOfWeekHeading = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
private val DateHeading = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
private val ClockTime = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** A shift the design draws the meter against: 8 hours. */
private const val SHIFT_MINUTES = 8 * 60f

/**
 * Home -- the same screen in two states, because they are the same
 * screen. Everything on it is derived from today's record, so "what may
 * this worker do next" is decided in exactly one place
 * ([DashboardUiState.nextAction]) rather than inferred per widget.
 *
 * v2 replaced v1's 1-2-3 stepper with two things that carry more: a
 * status card that says what actually happened and when, and a week
 * strip that answers "did I miss a day" without opening History.
 */
@Composable
fun DashboardScreen(
    worker: WorkerProfile,
    onStartFlow: (TimeDirection) -> Unit,
    onSeeHistory: () -> Unit,
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

    // The live "Hours so far". A minute is the smallest unit anyone reads
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
        onSeeHistory = onSeeHistory,
        onRetry = viewModel::refresh,
        modifier = modifier
    )
}

/**
 * Home with no ViewModel attached.
 *
 * Split out so every state -- idle, working, complete, offline -- can be
 * rendered in a preview and looked at without a device. The states differ
 * only by what today's record says, and seeing them side by side is how
 * you catch a card that lies.
 */
@Composable
fun DashboardContent(
    worker: WorkerProfile,
    state: DashboardUiState,
    onStartFlow: (TimeDirection) -> Unit,
    onSeeHistory: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val record = state.record
    val now = Instant.now().atZone(AttendanceZone)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.ScreenPadding)
            .padding(top = Dimens.GapSmall, bottom = Dimens.GapLarge),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WorkerRow(worker)

        Column {
            Text(
                text = now.format(DayOfWeekHeading).uppercase(Locale.ENGLISH),
                style = MaterialTheme.typography.bodySmall,
                letterSpacing = 0.04.em,
                color = TextMuted
            )
            Text(
                text = now.format(DateHeading),
                style = MaterialTheme.typography.headlineMedium
            )
        }

        state.failure?.let { AttendanceFailureNotice(it, onRetry = onRetry) }

        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), Alignment.Center) {
                CircularProgressIndicator(color = Green)
            }

            // Nothing recorded yet: the invitation, and a card that says
            // plainly that nothing has happened.
            record == null -> if (state.failure == null) {
                HeroAction(
                    title = stringResource(R.string.action_time_in),
                    subtitle = stringResource(R.string.action_time_in_hint),
                    icon = Icons.Filled.SouthWest,
                    gradient = listOf(Green, GreenDeep),
                    footnote = stringResource(R.string.home_flow_summary),
                    onClick = { onStartFlow(TimeDirection.IN) }
                )
                NoticeCard(
                    icon = Icons.Filled.Schedule,
                    title = stringResource(R.string.home_not_timed_in),
                    subtitle = stringResource(R.string.home_not_timed_in_sub)
                )
            }

            else -> {
                TimedInCard(
                    timeIn = record.timeInAt?.atZone(AttendanceZone)?.format(ClockTime),
                    projectName = record.timeInProjectName,
                    hours = state.totalHoursLabel,
                    minutes = state.totalMinutes,
                    complete = record.status == AttendanceStatus.COMPLETE,
                    pending = record.pending
                )

                when (state.nextAction) {
                    TimeDirection.OUT -> HeroAction(
                        title = stringResource(R.string.action_time_out),
                        subtitle = stringResource(R.string.action_time_out_hint),
                        icon = Icons.Filled.NorthEast,
                        gradient = listOf(BrownLight, BrownDeep),
                        footnote = null,
                        onClick = { onStartFlow(TimeDirection.OUT) }
                    )

                    // A day that has been closed, or one we could not read.
                    // Offering TIME IN here could only ever produce
                    // ALREADY_TIMED_IN.
                    TimeDirection.IN, null -> if (state.failure == null) {
                        NoticeCard(
                            icon = Icons.Filled.Check,
                            title = stringResource(R.string.home_day_complete),
                            subtitle = stringResource(R.string.home_day_complete_sub),
                            iconTint = Green,
                            iconBackground = GreenTint
                        )
                    }
                }
            }
        }

        RewardStrip(
            cells = state.rewardWeek,
            summary = state.reward,
            amount = state.rewardAmount,
            unavailable = state.rewardUnavailable
        )

        WeekStrip(days = state.weekWithToday, onSeeAll = onSeeHistory)
    }
}

/** Who is signed in, and the bell the design puts opposite the name. */
@Composable
private fun WorkerRow(worker: WorkerProfile) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Green, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = worker.initials,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = Color.White
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = worker.displayName ?: worker.firstName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = worker.positionAndId,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Box(
            modifier = Modifier
                .size(Dimens.IconButton)
                .background(Surface, RoundedCornerShape(Dimens.RadiusSmall))
                .border(1.dp, BorderDefault, RoundedCornerShape(Dimens.RadiusSmall)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsNone,
                contentDescription = stringResource(R.string.home_notifications),
                tint = TextLabel,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/**
 * The screen's one action, as a card rather than a slab.
 *
 * v1 gave this 230dp and a giant icon. v2 gives it a step badge, a
 * heading, a sentence and a footnote -- which is more surface for the
 * thumb AND more information, because a worker who has never used the
 * app can read what the next four screens will ask of them before
 * committing to the first tap.
 */
@Composable
private fun HeroAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: List<Color>,
    footnote: String?,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.RadiusHero)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(gradient), shape)
            .clickable(onClick = onClick)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .background(
                        Color.White.copy(alpha = 0.16f),
                        RoundedCornerShape(Dimens.RadiusPill)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.home_step_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp)
            )
        }

        Column {
            Text(
                text = title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.4).sp,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
        }

        footnote?.let {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.18f))
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Apartment,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

/** "You have not timed in yet", and the closed-day note that replaces it. */
@Composable
private fun NoticeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = TextDisabled,
    iconBackground: Color = Color.Transparent
) {
    AppCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBackground == Color.Transparent) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            } else {
                IconTile(icon = icon, tint = iconTint, background = iconBackground, size = 36.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

/**
 * What was recorded this morning, and how long ago that was.
 *
 * The meter under the total is a gauge against an eight-hour shift, not
 * a progress bar towards a goal -- it is there so a worker can see at a
 * glance whether they are near the end of a normal day, and it clamps
 * rather than overflowing when they are past it.
 */
@Composable
private fun TimedInCard(
    timeIn: String?,
    projectName: String?,
    hours: String,
    minutes: Int?,
    complete: Boolean,
    pending: Boolean
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconTile(
                    icon = Icons.Filled.Check,
                    tint = Green,
                    background = GreenTint,
                    size = 36.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = timeIn
                            ?.let { stringResource(R.string.timed_in_at, it) }
                            ?: stringResource(R.string.status_working),
                        style = MaterialTheme.typography.labelMedium
                    )
                    projectName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Reassurance, not a warning. The record is safe on the
                // phone and there is nothing for the worker to fix.
                StatusPill(
                    text = stringResource(
                        if (pending) R.string.home_pending else R.string.home_saved
                    ),
                    foreground = Green,
                    background = GreenTint
                )
            }

            CardDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = stringResource(
                            if (complete) R.string.hours_total else R.string.hours_so_far
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    timeIn?.let {
                        Text(
                            text = stringResource(R.string.hours_since, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDisabled
                        )
                    }
                }
                Text(
                    text = hours,
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    maxLines = 1
                )
            }

            ShiftMeter(minutes = minutes, complete = complete)
        }
    }
}

@Composable
private fun ShiftMeter(minutes: Int?, complete: Boolean) {
    val shape = RoundedCornerShape(4.dp)
    val fraction = ((minutes ?: 0) / SHIFT_MINUTES).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxWidth()
            .height(Dimens.MeterHeight)
            .background(Hairline, shape)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                // Brown once the day is closed, matching the Time Out that
                // closed it. Green while the clock is still running.
                .background(if (complete) Brown else Green, shape)
        )
    }
}

/**
 * Monday to Saturday, with a dot for every day that has a record.
 *
 * This is the cheapest possible answer to "did yesterday save?", which is
 * the question that otherwise sends a worker into History. Today is
 * outlined rather than filled, so "today" and "worked" stay separable.
 */
@Composable
private fun WeekStrip(days: List<WeekDayCell>, onSeeAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel(stringResource(R.string.home_this_week))
            Text(
                text = stringResource(R.string.home_see_all),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Green,
                modifier = Modifier.clickable(onClick = onSeeAll)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            days.forEach { day -> WeekCell(day, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun WeekCell(day: WeekDayCell, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Dimens.RadiusField)
    Column(
        modifier = modifier
            .background(if (day.isToday) GreenTint else Surface, shape)
            .border(
                width = if (day.isToday) 1.5.dp else 1.dp,
                color = if (day.isToday) Green else BorderDefault,
                shape = shape
            )
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = day.label,
            fontSize = 12.sp,
            fontWeight = if (day.isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = when {
                day.isToday -> Green
                day.future -> TextDisabled
                else -> TextMuted
            }
        )
        Box(
            Modifier
                .size(8.dp)
                .background(if (day.worked) Green else BorderDefault, CircleShape)
        )
    }
}

/**
 * The Monday-to-Friday reward standing (§37).
 *
 * FIVE cells, sitting directly above a SIX-cell attendance strip, and
 * that difference is the whole reason this is a separate row rather than
 * a colour change to [WeekStrip]. DACs works Saturdays; the reward only
 * ever asks about Mon-Fri. One strip carrying both meanings would tell a
 * worker that missing a Saturday cost them the money.
 *
 * Renders nothing at all until the server has answered. The reward is
 * the one read on this screen with no offline fallback, so an empty
 * [cells] means "not read yet" and [unavailable] means "could not read"
 * -- and five grey cells shown for either would read as five missed days.
 */
@Composable
private fun RewardStrip(
    cells: List<RewardCell>,
    summary: RewardSummary?,
    amount: Double?,
    unavailable: Boolean
) {
    if (cells.isEmpty() && !unavailable) return

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel(stringResource(R.string.home_reward_title))

            if (unavailable) {
                Text(
                    text = stringResource(R.string.home_reward_unavailable),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted
                )
            } else if (summary != null) {
                val (label, fg, bg) = when (summary.status) {
                    RewardStatus.Qualified ->
                        Triple(stringResource(R.string.home_reward_qualified), Green, GreenTint)
                    RewardStatus.Disqualified ->
                        Triple(stringResource(R.string.home_reward_disqualified), Danger, DangerTint)
                    RewardStatus.InProgress ->
                        Triple(stringResource(R.string.home_reward_in_progress), Brown, BrownTint)
                }
                // The amount is only ever shown ALONGSIDE a qualifying
                // standing, and only when the server named one. A peso
                // figure next to "Disqualified" reads as a promise.
                val text = if (summary.status == RewardStatus.Qualified && amount != null) {
                    label + " · " + pesos(amount)
                } else {
                    label
                }
                StatusPill(text = text, foreground = fg, background = bg)
            }
        }

        if (!unavailable) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                cells.forEach { cell -> RewardCellView(cell, Modifier.weight(1f)) }
            }
            summary?.let {
                Text(
                    text = stringResource(R.string.home_reward_of, it.onTimeDays, it.requiredDays),
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }
    }
}

/** "₱500", and "₱500.50" only when the centavos are real. */
private fun pesos(amount: Double): String =
    if (amount % 1.0 == 0.0) "₱" + amount.toLong()
    else "₱" + String.format(java.util.Locale.US, "%.2f", amount)

@Composable
private fun RewardCellView(cell: RewardCell, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Dimens.RadiusField)

    // Pending and NotRequired are deliberately the QUIETEST states here.
    // Nothing is wrong on either: one has not happened yet, and the other
    // is a day the company chose not to open. Drawing them in red would
    // blame a worker for the calendar.
    val (dot, border, fill) = when (cell.state) {
        RewardCellState.OnTime -> Triple(Green, BorderDefault, Surface)
        RewardCellState.Late -> Triple(Danger, DangerBorder, DangerTint)
        RewardCellState.Missing -> Triple(Danger, DangerBorder, DangerTint)
        RewardCellState.Pending -> Triple(BorderDefault, BorderDefault, Surface)
        RewardCellState.NotRequired -> Triple(Vacant, Hairline, SurfaceRaised)
    }

    Column(
        modifier = modifier
            .background(if (cell.isToday) GreenTint else fill, shape)
            .border(
                width = if (cell.isToday) 1.5.dp else 1.dp,
                color = if (cell.isToday) Green else border,
                shape = shape
            )
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = cell.label,
            fontSize = 12.sp,
            fontWeight = if (cell.isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = when {
                cell.isToday -> Green
                cell.state == RewardCellState.Pending -> TextDisabled
                cell.state == RewardCellState.NotRequired -> TextFaint
                else -> TextMuted
            }
        )
        Box(Modifier.size(8.dp).background(dot, CircleShape))
    }
}
