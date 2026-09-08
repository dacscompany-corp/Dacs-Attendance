package com.dacs.attendance.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dacs.attendance.R
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.AttendanceZone
import com.dacs.attendance.domain.HistoryDay
import com.dacs.attendance.domain.HistorySpan
import com.dacs.attendance.domain.TotalHours
import com.dacs.attendance.ui.components.AppCard
import com.dacs.attendance.ui.components.AttendanceFailureNotice
import com.dacs.attendance.ui.components.StatusPill
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Brown
import com.dacs.attendance.ui.theme.BrownTint
import com.dacs.attendance.ui.theme.Canvas
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.GreenBorder
import com.dacs.attendance.ui.theme.GreenTint
import com.dacs.attendance.ui.theme.Hairline
import com.dacs.attendance.ui.theme.TextLabel
import com.dacs.attendance.ui.theme.MonoFamily
import com.dacs.attendance.ui.theme.Surface
import com.dacs.attendance.ui.theme.TextDisabled
import com.dacs.attendance.ui.theme.TextFaint
import com.dacs.attendance.ui.theme.TextMuted
import com.dacs.attendance.ui.theme.TextSecondary
import com.dacs.attendance.ui.theme.Vacant
import com.dacs.attendance.ui.theme.ViewerBackdrop
import com.dacs.attendance.ui.theme.ViewerFrame
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DayHeading = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)
private val ClockTime = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/**
 * "My attendance".
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

    // The photo a worker has tapped open, if any. Held here rather than
    // per row so the viewer survives the row scrolling out from under it.
    var viewing by remember { mutableStateOf<OpenPhoto?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas)
            .padding(horizontal = Dimens.ScreenPadding)
            .padding(top = Dimens.GapSmall),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = stringResource(R.string.history_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
            SpanSegment(
                label = stringResource(R.string.history_this_week),
                selected = state.span == HistorySpan.WEEK,
                onClick = { viewModel.onSpanChange(HistorySpan.WEEK) },
                modifier = Modifier.weight(1f)
            )
            SpanSegment(
                label = stringResource(R.string.history_this_month),
                selected = state.span == HistorySpan.MONTH,
                onClick = { viewModel.onSpanChange(HistorySpan.MONTH) },
                modifier = Modifier.weight(1f)
            )
        }

        // Only once the range has actually been read. A summary rendered
        // mid-load says "0 days worked · 0h 0m total", which is not
        // "loading" -- it is a different, untrue answer to the question
        // the worker opened this screen to ask.
        if (!state.loading) {
            SummaryCard(
                daysWorked = state.summary.daysWorked,
                totalMinutes = state.summary.totalMinutes,
                span = state.span
            )
        }

        state.failure?.let { AttendanceFailureNotice(it, onRetry = viewModel::refresh) }

        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(top = 32.dp), Alignment.Center) {
                CircularProgressIndicator(color = Green)
            }

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = Dimens.GapLarge)
            ) {
                items(state.days, key = { it.workDate }) { day ->
                    DayCard(day, viewModel::photoUrl, onOpenPhoto = { viewing = it })
                }
            }
        }
    }

    viewing?.let { photo ->
        PhotoViewer(photo = photo, onClose = { viewing = null })
    }
}

/** Week or Month. Filled black when active, as the design draws it. */
@Composable
private fun SpanSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Dimens.RadiusTile)
    Box(
        modifier = modifier
            .height(Dimens.SegmentHeight)
            .background(if (selected) MaterialTheme.colorScheme.onSurface else Surface, shape)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else BorderDefault,
                shape = shape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) Surface else TextSecondary
        )
    }
}

/** Days worked on the left, hours on the right. */
@Composable
private fun SummaryCard(daysWorked: Int, totalMinutes: Int, span: HistorySpan) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // "1 days worked" reads as broken English to anyone,
                    // in any language. Plurals, not concatenation.
                    text = pluralStringResource(
                        R.plurals.history_days_worked,
                        daysWorked,
                        daysWorked
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    lineHeight = 21.sp
                )
                Text(
                    text = stringResource(
                        if (span == HistorySpan.WEEK) R.string.history_span_week
                        else R.string.history_span_month
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = TotalHours.format(totalMinutes),
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Green,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.history_total),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun DayCard(
    day: HistoryDay,
    photoUrl: suspend (String?) -> String?,
    onOpenPhoto: (OpenPhoto) -> Unit
) {
    val record = day.record
    val heading = day.date.format(DayHeading)

    if (record == null) {
        // A filled block, not an outlined card: there is nothing in it,
        // and the design says so by making it look like a gap in the list
        // rather than a card with the contents missing.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Vacant, RoundedCornerShape(Dimens.RadiusCard))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = heading,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextMuted
            )
            Text(
                text = stringResource(R.string.history_no_record),
                style = MaterialTheme.typography.bodyMedium,
                color = TextDisabled
            )
        }
        return
    }

    val open = record.status == AttendanceStatus.WORKING

    // Resolved once per card, as it composes -- so a month of history
    // costs only the days the worker actually scrolled past, and the
    // thumbnail and the "View photos" strip cannot mint two different
    // links for the same file.
    val inUrl by produceState<String?>(initialValue = null, record.timeInPhotoPath) {
        value = photoUrl(record.timeInPhotoPath)
    }
    val outUrl by produceState<String?>(initialValue = null, record.timeOutPhotoPath) {
        value = photoUrl(record.timeOutPhotoPath)
    }

    AppCard(
        padding = 15.dp,
        // The open day wears a green edge: it is the one still in play,
        // and it is almost always today.
        borderColor = if (open) GreenBorder else BorderDefault,
        borderWidth = if (open) 1.5.dp else 1.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.labelMedium
                )
                DayStatusPill(record.status, record.totalMinutes)
            }

            val legIn = PhotoLeg(
                url = inUrl,
                label = stringResource(R.string.history_in),
                time = record.timeInAt?.atZone(AttendanceZone)?.format(ClockTime),
                project = record.timeInProjectName
            )
            val legOut = PhotoLeg(
                url = outUrl,
                label = stringResource(R.string.history_out),
                time = record.timeOutAt?.atZone(AttendanceZone)?.format(ClockTime),
                // Deliberately NOT falling back to the Time In project: a
                // leg that has not happened must not name a place. Showing
                // one reads as "timed out at ABC", which is a claim about
                // a record that does not exist.
                project = if (record.timeOutAt == null) null else record.timeOutProjectName
            )

            LegRow(
                icon = Icons.Filled.SouthWest,
                accent = Green,
                leg = legIn,
                onOpen = { onOpenPhoto(OpenPhoto(heading, legIn, legOut, showingOut = false)) }
            )
            LegRow(
                icon = Icons.Filled.NorthEast,
                accent = Brown,
                leg = legOut,
                onOpen = { onOpenPhoto(OpenPhoto(heading, legIn, legOut, showingOut = true)) }
            )

            // Opens on the morning photo when there is one -- the viewer
            // is where a worker moves between the two legs, so the strip
            // only has to get them in.
            if (legIn.url != null || legOut.url != null) {
                val startOnOut = legIn.url == null
                ViewPhotosStrip(
                    onClick = {
                        onOpenPhoto(OpenPhoto(heading, legIn, legOut, showingOut = startOnOut))
                    }
                )
            }
        }
    }
}

/**
 * The pill opposite the date.
 *
 * A finished day shows its hours, because that is what a worker came to
 * check. An open one shows "Working", because it has no total yet and
 * inventing one from the phone clock would put a figure on screen the
 * server has never agreed to.
 */
@Composable
private fun DayStatusPill(status: AttendanceStatus, totalMinutes: Int?) {
    when (status) {
        AttendanceStatus.COMPLETE -> StatusPill(
            text = TotalHours.format(totalMinutes),
            foreground = Green,
            background = GreenTint
        )
        AttendanceStatus.WORKING -> StatusPill(
            text = stringResource(R.string.status_working),
            foreground = Brown,
            background = BrownTint
        )
        else -> StatusPill(
            text = stringResource(R.string.status_abandoned),
            foreground = TextMuted,
            background = Hairline
        )
    }
}

/** One leg of a day: arrow, label, rule, time, and the photo if there is one. */
@Composable
private fun LegRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    leg: PhotoLeg,
    onOpen: () -> Unit
) {
    val label = leg.label
    val time = leg.time
    val happened = time != null

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (happened) accent else TextFaint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (happened) TextSecondary else TextDisabled
        )
        Box(Modifier.weight(1f).height(1.dp).background(Hairline))

        if (happened) {
            Text(
                text = time.orEmpty(),
                fontFamily = MonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1
            )
        } else {
            Text(
                text = stringResource(R.string.history_not_yet),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = TextDisabled
            )
        }

        LegThumb(
            url = leg.url,
            contentDescription = stringResource(R.string.history_open_photo, label, time ?: ""),
            onClick = onOpen
        )
    }
}

/**
 * The 36dp square at the end of a leg.
 *
 * With no photo -- offline, or a leg that has not happened -- it stays an
 * outlined camera glyph: an empty grey square would read as a photo that
 * failed to load, and there is a real difference between "none was taken"
 * and "we could not fetch it".
 */
@Composable
private fun LegThumb(
    url: String?,
    contentDescription: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.RadiusThumb)
    val open = url

    if (open == null) {
        Box(
            modifier = Modifier
                .size(Dimens.Thumb)
                .border(1.dp, Hairline, shape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = null,
                tint = TextFaint,
                modifier = Modifier.size(17.dp)
            )
        }
        return
    }

    AsyncImage(
        model = open,
        // Says what tapping does, not just what the image is.
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(Dimens.Thumb)
            .clip(shape)
            .border(1.dp, BorderDefault, shape)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun ViewPhotosStrip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(Canvas, RoundedCornerShape(Dimens.RadiusSmall))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Image,
            contentDescription = null,
            tint = TextLabel,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = stringResource(R.string.history_view_photos),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = TextSecondary
        )
    }
}

/** One leg of a day, as the viewer and the row both need it. */
internal data class PhotoLeg(
    val url: String?,
    val label: String,
    val time: String?,
    val project: String?
)

/**
 * A day the worker has opened, and which of its two photos is showing.
 *
 * The viewer carries BOTH legs rather than one photo, because the design
 * puts a Time In / Time Out switch at the foot of it -- and the question
 * a worker opens this to answer is usually about the pair ("did the
 * afternoon one save too?"), not about the single image they tapped.
 */
internal data class OpenPhoto(
    val day: String,
    val timeIn: PhotoLeg,
    val timeOut: PhotoLeg,
    val showingOut: Boolean
) {
    val showing: PhotoLeg get() = if (showingOut) timeOut else timeIn
}

/**
 * One attendance photo, full screen.
 *
 * ContentScale.Fit, not Crop: the caption burned along the bottom of the
 * image is the whole reason a worker opens this -- project, date and
 * time, in the photo itself. Cropping to fill would cut off the one part
 * that proves anything.
 */
@Composable
private fun PhotoViewer(photo: OpenPhoto, onClose: () -> Unit) {
    // Which leg is on screen lives HERE, not in the caller: switching
    // between the two is a move inside the viewer, and pushing it back up
    // to the list would re-open the dialog on every tap.
    var showingOut by remember(photo) { mutableStateOf(photo.showingOut) }
    val leg = if (showingOut) photo.timeOut else photo.timeIn

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // NO tap-to-dismiss on the backdrop. Two reasons, and the second
        // is the one that bit: a worker taps the photo to look closer at
        // it, so closing on that tap is the opposite of what they meant --
        // and the dialog was catching the RELEASE of the very tap that
        // opened it, so it shut again before anything was drawn. Close is
        // the X, or system back.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ViewerBackdrop)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 52.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(Dimens.IconButton)
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.25f),
                            RoundedCornerShape(Dimens.RadiusSmall)
                        )
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = photo.day,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(leg.label, leg.time).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                StatusPill(
                    text = stringResource(R.string.viewer_proof),
                    foreground = Color.White,
                    background = Color.White.copy(alpha = 0.14f),
                    icon = Icons.Filled.Verified
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(Dimens.RadiusPanel))
                    .background(ViewerFrame)
            ) {
                AsyncImage(
                    model = leg.url,
                    contentDescription = photo.day,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .padding(start = 14.dp, end = 14.dp, top = 26.dp, bottom = 12.dp)
                ) {
                    leg.project?.let {
                        Text(
                            text = it,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = listOfNotNull(photo.day, leg.time).joinToString(" · "),
                        fontFamily = MonoFamily,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LegSwitch(
                    icon = Icons.Filled.SouthWest,
                    label = photo.timeIn.label,
                    accent = Green,
                    active = !showingOut,
                    available = photo.timeIn.url != null,
                    onClick = { showingOut = false },
                    modifier = Modifier.weight(1f)
                )
                LegSwitch(
                    icon = Icons.Filled.NorthEast,
                    // A leg with no photo says so on the button rather
                    // than looking tappable and doing nothing.
                    label = if (photo.timeOut.url != null) {
                        photo.timeOut.label
                    } else {
                        stringResource(R.string.history_no_photo_yet)
                    },
                    accent = Brown,
                    active = showingOut,
                    available = photo.timeOut.url != null,
                    onClick = { showingOut = true },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** One half of the viewer footer: white when showing, outlined when not. */
@Composable
private fun LegSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    active: Boolean,
    available: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Dimens.RadiusField)
    val ink = when {
        active -> accent
        available -> Color.White.copy(alpha = 0.8f)
        else -> Color.White.copy(alpha = 0.35f)
    }

    Row(
        modifier = modifier
            .height(48.dp)
            .background(if (active) Color.White else Color.Transparent, shape)
            .border(
                1.dp,
                if (active) Color.White else Color.White.copy(alpha = 0.25f),
                shape
            )
            .clickable(enabled = available && !active, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(17.dp))
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = ink,
            maxLines = 1
        )
    }
}
