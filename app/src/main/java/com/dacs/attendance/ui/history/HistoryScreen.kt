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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
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
import com.dacs.attendance.ui.theme.GreenTint
import com.dacs.attendance.ui.theme.Hairline
import com.dacs.attendance.ui.theme.MonoFamily
import com.dacs.attendance.ui.theme.Surface
import com.dacs.attendance.ui.theme.SurfaceRaised
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

    Column(modifier = modifier.fillMaxSize().background(SurfaceRaised)) {
        // The same white bar the dashboard wears, so moving between tabs
        // does not move the title.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 18.dp)
        ) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.history_title_tl),
                fontSize = 14.sp,
                color = TextMuted
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderDefault))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)
        ) {
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

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 4.dp,
                        bottom = Dimens.GapMedium
                    )
                ) {
                    items(state.days, key = { it.workDate }) { day ->
                        DayCard(day, viewModel::photoUrl)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpanChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        // Filled when active, as the design draws it. An outline for both
        // states makes the current span something you have to read rather
        // than see.
        color = if (selected) Color.White else TextSecondary,
        modifier = Modifier
            .background(
                color = if (selected) Green else Surface,
                shape = RoundedCornerShape(999.dp)
            )
            .border(
                width = 1.dp,
                color = if (selected) Green else BorderDefault,
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    )
}

@Composable
private fun DayCard(day: HistoryDay, photoUrl: suspend (String?) -> String?) {
    val record = day.record

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(Dimens.RadiusLarge))
            .border(1.dp, BorderDefault, RoundedCornerShape(Dimens.RadiusLarge))
            .padding(Dimens.GapMedium),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (record == null) {
            // One row, not a heading with a body: there is nothing to
            // report, so the card says only which day and that it is empty.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = day.date.format(DayHeading),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        // Dimmed, never omitted.
                        color = TextDisabled
                    )
                    Text(
                        text = stringResource(R.string.history_no_record_tl),
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }
                StatusChip(null)
            }
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = day.date.format(DayHeading),
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
            StatusChip(record.status)
        }

        // The two legs side by side, as the design lays them out: a day
        // is a pair, and reading it as one line each makes the gap
        // between them obvious when a Time Out is missing.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Leg(
                accent = Green,
                label = stringResource(R.string.history_in),
                project = record.timeInProjectName,
                time = record.timeInAt?.atZone(AttendanceZone)?.format(ClockTime),
                photoPath = record.timeInPhotoPath,
                photoUrl = photoUrl,
                modifier = Modifier.weight(1f)
            )
            Leg(
                accent = Brown,
                label = stringResource(R.string.history_out),
                // Deliberately NOT falling back to the Time In project: a
                // leg that has not happened must not name a place. Showing
                // one reads as "timed out at ABC", which is a claim about a
                // record that does not exist.
                project = if (record.timeOutAt == null) null else record.timeOutProjectName,
                time = record.timeOutAt?.atZone(AttendanceZone)?.format(ClockTime),
                photoPath = record.timeOutPhotoPath,
                photoUrl = photoUrl,
                modifier = Modifier.weight(1f)
            )
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = stringResource(R.string.history_total),
                fontSize = 14.sp,
                color = TextMuted
            )
            Text(
                text = TotalHours.format(record.totalMinutes),
                fontFamily = MonoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Green
            )
        }
    }
}

@Composable
private fun Leg(
    accent: Color,
    label: String,
    project: String?,
    time: String?,
    photoPath: String?,
    photoUrl: suspend (String?) -> String?,
    modifier: Modifier = Modifier
) {
    // Resolved as the row composes, so a month of history costs only the
    // days the worker actually scrolled past.
    val url by produceState<String?>(initialValue = null, photoPath) {
        value = photoUrl(photoPath)
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Hairline)
            )
        } else {
            // No photo yet, or none to have: offline, on a leg that has
            // not happened, or while the link is still being minted. The
            // bar carries the same IN/OUT reading, so nothing about the
            // row becomes unreadable.
            Box(
                Modifier
                    .width(6.dp)
                    .height(44.dp)
                    .background(
                        if (time == null) Hairline else accent,
                        RoundedCornerShape(3.dp)
                    )
            )
        }
        Column {
            Text(
                text = "$label · ${project ?: "—"}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = time ?: "—",
                fontFamily = MonoFamily,
                fontSize = 16.sp,
                color = if (time == null) TextDisabled else MaterialTheme.colorScheme.onSurface
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
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}
