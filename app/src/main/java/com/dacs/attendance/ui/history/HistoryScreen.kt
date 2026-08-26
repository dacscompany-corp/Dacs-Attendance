package com.dacs.attendance.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dacs.attendance.R
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.AttendanceZone
import com.dacs.attendance.domain.HistoryDay
import com.dacs.attendance.domain.HistorySpan
import com.dacs.attendance.domain.TotalHours
import com.dacs.attendance.ui.components.AttendanceFailureNotice
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Brown
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.GreenBorder
import com.dacs.attendance.ui.theme.GreenTint
import com.dacs.attendance.ui.theme.Hairline
import com.dacs.attendance.ui.theme.MonoFamily
import com.dacs.attendance.ui.theme.TextDisabled
import com.dacs.attendance.ui.theme.TextMuted
import com.dacs.attendance.ui.theme.TextSecondary
import java.time.format.DateTimeFormatter

private val DayHeading = DateTimeFormatter.ofPattern("EEEE, d MMM")
private val ClockTime = DateTimeFormatter.ofPattern("h:mm a")

/**
 * Screen 10 — "My attendance".
 *
 * Days with NO record are shown, not skipped. A worker opens this to
 * check whether a day was recorded, and a list built only from records
 * answers the opposite question: it can never show the day that is
 * missing, which is the day they are worried about.
 */
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPadding)
            .padding(top = Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringResource(R.string.history_title_tl),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
            SpanChip(
                label = stringResource(R.string.history_this_week),
                selected = state.span == HistorySpan.WEEK,
                onClick = { viewModel.onSpanChange(HistorySpan.WEEK) }
            )
            SpanChip(
                label = stringResource(R.string.history_this_month),
                selected = state.span == HistorySpan.MONTH,
                onClick = { viewModel.onSpanChange(HistorySpan.MONTH) }
            )
        }

        if (state.summary.daysWorked > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.history_summary,
                    state.summary.daysWorked,
                    state.summary.daysWorked,
                    TotalHours.format(state.summary.totalMinutes)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        state.failure?.let { AttendanceFailureNotice(it, onRetry = viewModel::refresh) }

        when {
            state.loading -> Box(Modifier.fillMaxWidth(), Alignment.Center) {
                CircularProgressIndicator(color = Green)
            }

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
                items(state.days, key = { it.workDate }) { day -> DayCard(day) }
            }
        }
    }
}

@Composable
private fun SpanChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) Green else TextMuted,
        modifier = Modifier
            .background(
                color = if (selected) GreenTint else Color.Transparent,
                shape = RoundedCornerShape(999.dp)
            )
            .border(
                width = 1.dp,
                color = if (selected) GreenBorder else BorderDefault,
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun DayCard(day: HistoryDay) {
    val record = day.record

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderDefault, RoundedCornerShape(Dimens.RadiusLarge))
            .padding(Dimens.GapMedium),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = day.date.format(DayHeading),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                // A day with nothing recorded is dimmed, never omitted.
                color = if (record == null) TextDisabled else MaterialTheme.colorScheme.onSurface
            )
            StatusChip(record?.status)
        }

        if (record == null) {
            Text(
                text = stringResource(R.string.history_no_record_tl),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            return@Column
        }

        Leg(
            dotColour = Green,
            label = stringResource(R.string.history_in),
            project = record.timeInProjectName,
            time = record.timeInAt?.atZone(AttendanceZone)?.format(ClockTime)
        )
        Leg(
            dotColour = Brown,
            label = stringResource(R.string.history_out),
            // Deliberately NOT falling back to the Time In project: a
            // leg that has not happened must not name a place. Showing
            // one reads as "timed out at ABC", which is a claim about a
            // record that does not exist.
            project = if (record.timeOutAt == null) null else record.timeOutProjectName,
            time = record.timeOutAt?.atZone(AttendanceZone)?.format(ClockTime)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.history_total),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Text(
                text = TotalHours.format(record.totalMinutes),
                fontFamily = MonoFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                color = Green
            )
        }
    }
}

@Composable
private fun Leg(dotColour: Color, label: String, project: String?, time: String?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(9.dp)
                .background(if (time == null) Hairline else dotColour, CircleShape)
        )
        Column {
            Text(
                text = "$label · ${project ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = time ?: "—",
                fontFamily = MonoFamily,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun StatusChip(status: AttendanceStatus?) {
    val (labelRes, fg, bg) = when (status) {
        AttendanceStatus.COMPLETE -> Triple(R.string.status_complete, Green, GreenTint)
        AttendanceStatus.WORKING -> Triple(R.string.status_working, Brown, Color(0xFFF3EDE2))
        AttendanceStatus.ABANDONED -> Triple(R.string.status_abandoned, TextMuted, Hairline)
        else -> Triple(R.string.status_no_record, TextMuted, Hairline)
    }

    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
