package com.dacs.attendance.ui.timeflow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dacs.attendance.R
import com.dacs.attendance.domain.AttendanceProject
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.AttendanceZone
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.TotalHours
import com.dacs.attendance.domain.isChipSelected
import com.dacs.attendance.domain.photoOverlayCaptionLines
import com.dacs.attendance.domain.toggleDescriptionChip
import com.dacs.attendance.ui.components.AttendanceFailureNotice
import com.dacs.attendance.ui.components.CardDivider
import com.dacs.attendance.ui.components.FlowHeader
import com.dacs.attendance.ui.components.FlowTitle
import com.dacs.attendance.ui.components.IconTile
import com.dacs.attendance.ui.components.PrimaryActionButton
import com.dacs.attendance.ui.components.SecondaryActionButton
import com.dacs.attendance.ui.components.SectionLabel
import com.dacs.attendance.ui.components.StatusPill
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Brown
import com.dacs.attendance.ui.theme.Canvas
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.GreenPressed
import com.dacs.attendance.ui.theme.GreenTint
import com.dacs.attendance.ui.theme.Hairline
import com.dacs.attendance.ui.theme.MonoFamily
import com.dacs.attendance.ui.theme.Surface
import com.dacs.attendance.ui.theme.TextDisabled
import com.dacs.attendance.ui.theme.TextMuted
import com.dacs.attendance.ui.theme.TextSecondary
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ConfirmTime = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val ConfirmDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/**
 * Steps 1-4 and the confirmation, as ONE flow parameterised by direction.
 *
 * Time Out is the same four screens in brown, so colour and copy come
 * from [TimeDirection] and the steps themselves are shared -- writing
 * Time Out separately is how the two halves drift apart, and a drift here
 * means a worker's evening does not match their morning.
 */
@Composable
fun TimeFlowScreen(
    direction: TimeDirection,
    onFinished: () -> Unit,
    /** Left the flow. The reason is null unless something explains it. */
    onCancelled: (reason: FlowExit?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimeFlowViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accent = if (direction == TimeDirection.IN) Green else Brown

    // Starts the flow and loads the picker. Keyed on direction so a Time
    // Out started right after a Time In gets a fresh event id rather than
    // reusing the one that already recorded the morning.
    LaunchedEffect(direction) { viewModel.start(direction) }

    BackHandler(enabled = state.step != FlowStep.Confirmed) {
        if (state.step == FlowStep.PickProject) onCancelled(null) else viewModel.onBack()
    }

    val back = {
        if (state.step == FlowStep.PickProject) onCancelled(null) else viewModel.onBack()
    }

    when (state.step) {
        // Full-bleed: the confirmation is a whole accent-coloured screen
        // in the design, not a card sitting on a white one.
        FlowStep.Confirmed -> ConfirmedStep(
            record = state.saved,
            direction = direction,
            description = state.description,
            accent = accent,
            onDone = onFinished,
            modifier = modifier.fillMaxSize()
        )

        // Near-black, edge to edge, with its own header. The camera is the
        // only step that is not a white sheet.
        FlowStep.TakePhoto -> CameraStep(
            projectName = state.selectedProject?.name,
            onPhotoTaken = viewModel::onPhotoTaken,
            onBack = back,
            onGiveUp = { onCancelled(FlowExit.CameraPermission) },
            modifier = modifier.fillMaxSize()
        )

        FlowStep.PickProject -> FlowSheet(modifier) {
            FlowHeader(
                step = 1,
                total = 4,
                accent = accent,
                onBack = back,
                modeLabel = stringResource(
                    if (direction == TimeDirection.IN) R.string.mode_time_in
                    else R.string.mode_time_out
                ),
                modeIcon = if (direction == TimeDirection.IN) {
                    Icons.Filled.SouthWest
                } else {
                    Icons.Filled.NorthEast
                }
            )
            FlowTitle(
                title = stringResource(R.string.flow_pick_project),
                subtitle = stringResource(R.string.flow_pick_project_sub),
                modifier = Modifier.padding(top = 13.dp)
            )
            PickProjectStep(
                projects = state.projects,
                loading = state.loadingProjects,
                selectedKey = state.selectedProjectKey,
                accent = accent,
                onSelect = viewModel::onProjectSelected,
                onConfirm = viewModel::onProjectConfirmed,
                onRetry = viewModel::onRetryProjects,
                failure = state.failure,
                modifier = Modifier.weight(1f)
            )
        }

        FlowStep.CheckPhoto -> FlowSheet(modifier) {
            FlowHeader(
                step = 3,
                total = 4,
                accent = accent,
                onBack = back,
                inlineTitle = stringResource(R.string.flow_check_photo),
                inlineSubtitle = stringResource(R.string.flow_check_photo_sub)
            )
            CheckPhotoStep(
                photoPath = state.photo?.file?.absolutePath,
                capturedAt = state.photo?.capturedAt,
                projectName = state.selectedProject?.name,
                accent = accent,
                onRetake = viewModel::onRetakePhoto,
                onAccept = viewModel::onPhotoAccepted,
                modifier = Modifier.weight(1f)
            )
        }

        FlowStep.Describe -> FlowSheet(modifier) {
            FlowHeader(
                step = 4,
                total = 4,
                accent = accent,
                onBack = back,
                inlineTitle = stringResource(R.string.flow_describe),
                inlineSubtitle = stringResource(R.string.flow_describe_sub)
            )
            DescribeStep(
                description = state.description,
                direction = direction,
                accent = accent,
                submitting = state.submitting,
                failure = state.failure,
                projectName = state.selectedProject?.name,
                photoPath = state.photo?.file?.absolutePath,
                onDescriptionChange = viewModel::onDescriptionChange,
                onSubmit = viewModel::onSubmit,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * The white sheet steps 1, 3 and 4 sit on.
 *
 * White, not the tab screens' canvas: the flow is a sequence of sheets in
 * the design, and the cards on them need to read as raised off something.
 */
@Composable
private fun FlowSheet(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Surface)
            .imePadding()
            .padding(horizontal = Dimens.ScreenPadding)
            .padding(top = 6.dp),
        content = content
    )
}

// ── 1 · Which project today? ────────────────────────────────────────

@Composable
internal fun PickProjectStep(
    projects: List<AttendanceProject>,
    loading: Boolean,
    selectedKey: String?,
    accent: Color,
    onSelect: (String) -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    failure: com.dacs.attendance.domain.AttendanceFailure?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.weight(1f).padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            failure?.let { AttendanceFailureNotice(it, onRetry = onRetry) }

            when {
                loading -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    CircularProgressIndicator(color = accent)
                }

                projects.isEmpty() && failure == null -> Text(
                    // A real state, not an error: the admin has not added
                    // any projects yet, and no amount of retrying fixes it.
                    text = stringResource(R.string.flow_no_projects),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextMuted,
                    modifier = Modifier.weight(1f)
                )

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(projects, key = { it.key }) { project ->
                        ProjectRow(
                            project = project,
                            selected = project.key == selectedKey,
                            accent = accent,
                            onClick = { onSelect(project.key) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = TextDisabled,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    // Shown ALWAYS, not only when the list is empty: a
                    // worker whose site is missing from four listed
                    // projects needs to know whose job it is to add it.
                    text = stringResource(R.string.flow_missing_project),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        Column(modifier = Modifier.padding(top = 14.dp, bottom = Dimens.BottomPadding)) {
            PrimaryActionButton(
                label = stringResource(R.string.action_continue),
                onClick = onConfirm,
                container = accent,
                enabled = selectedKey != null
            )
        }
    }
}

/**
 * One project.
 *
 * The subtitle says only "Active", which is all the app actually knows --
 * the design mocks a location line ("Barangay Talon · active") and there
 * is no such column. Inventing one would put a place name on the screen
 * that no record anywhere agrees with.
 */
@Composable
private fun ProjectRow(
    project: AttendanceProject,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.RadiusRow)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) GreenTint else Surface, shape)
            // Tinted from the accent, not a fixed green: a Time Out is
            // brown all the way through, and a green row inside a brown
            // flow reads as the wrong screen.
            .border(1.5.dp, if (selected) accent else BorderDefault, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(
            icon = Icons.Filled.Apartment,
            tint = if (selected) Color.White else TextMuted,
            background = if (selected) accent else Canvas
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = project.name,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.flow_project_active),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

// ── 3 · Is this photo clear? ────────────────────────────────────────

@Composable
internal fun CheckPhotoStep(
    photoPath: String?,
    capturedAt: java.time.Instant?,
    projectName: String?,
    accent: Color,
    onRetake: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(Dimens.RadiusPhoto))
                .background(Canvas)
        ) {
            // Coil, not a hand-rolled BitmapFactory decode: the file
            // carries EXIF rotation and half the phones in the field
            // would show a sideways selfie without it.
            AsyncImage(
                model = photoPath,
                contentDescription = stringResource(R.string.flow_check_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // EXACTLY what will be burned into the file: project on top,
            // stamp under it, the same two lines the camera previewed one
            // screen ago. "Is this photo clear?" should be asked about the
            // picture as it will be FILED, and the project is half of what
            // the caption claims.
            capturedAt?.let { at ->
                val (name, stamp) = photoOverlayCaptionLines(projectName.orEmpty(), at)
                PhotoCaption(
                    name = name,
                    stamp = stamp,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
        }

        Column(
            modifier = Modifier.padding(top = 16.dp, bottom = Dimens.BottomPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Stacked, not side by side: v2 makes YES the full-width
            // answer and Retake the quieter line under it, because the
            // question above has a right answer most of the time.
            PrimaryActionButton(
                label = stringResource(R.string.action_use_photo),
                onClick = onAccept,
                container = accent,
                leadingIcon = Icons.Filled.Check
            )
            SecondaryActionButton(
                label = stringResource(R.string.action_retake),
                onClick = onRetake,
                icon = Icons.Filled.Refresh
            )
        }
    }
}

/** The scrim and two lines burned along the bottom of a photo. */
@Composable
internal fun PhotoCaption(name: String, stamp: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // A scrim, not a solid bar: the caption has to stay readable
            // over a bright sky and over a dark wall, and the photo behind
            // it is the thing the worker is actually checking.
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                )
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (name.isNotBlank()) {
            Text(
                text = name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = stamp,
            fontFamily = MonoFamily,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.8f),
            maxLines = 1
        )
    }
}

// ── 4 · What are you working on? ────────────────────────────────────

@Composable
internal fun DescribeStep(
    description: String,
    direction: TimeDirection,
    accent: Color,
    submitting: Boolean,
    failure: com.dacs.attendance.domain.AttendanceFailure?,
    projectName: String?,
    photoPath: String?,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // What the worker is about to submit, restated on the last
            // screen before SUBMIT. Four steps in, "which project did I
            // pick?" is a real question, and the answer is on the record
            // forever.
            projectName?.let { CapturedContextRow(it, photoPath, accent) }

            DescriptionChips(
                description = description,
                accent = accent,
                enabled = !submitting,
                onDescriptionChange = onDescriptionChange
            )

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = stringResource(R.string.flow_describe_own),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    enabled = !submitting,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.flow_describe_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextDisabled
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 74.dp),
                    shape = RoundedCornerShape(Dimens.RadiusButton),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Canvas,
                        unfocusedContainerColor = Canvas,
                        disabledContainerColor = Canvas,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = BorderDefault
                    )
                )
            }

            failure?.let { AttendanceFailureNotice(it) }
        }

        Column(modifier = Modifier.padding(top = 14.dp, bottom = Dimens.BottomPadding)) {
            PrimaryActionButton(
                label = stringResource(
                    if (direction == TimeDirection.IN) R.string.action_submit_in
                    else R.string.action_submit_out
                ),
                onClick = onSubmit,
                container = accent,
                trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
                loading = submitting
            )
        }
    }
}

/** Project + "Photo taken", so step 4 shows what steps 1-3 produced. */
@Composable
private fun CapturedContextRow(projectName: String, photoPath: String?, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Canvas, RoundedCornerShape(Dimens.RadiusRow))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = photoPath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(Dimens.RadiusTile))
                .background(Hairline)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = projectName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.flow_photo_taken),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * "TAP ONE".
 *
 * The reason this exists is physical: step 4 is done standing up,
 * one-handed, outdoors, often with gloves on. A chip is one tap where the
 * keyboard is twenty, and the toggling rules live in
 * [toggleDescriptionChip] where they can be tested.
 */
@Composable
private fun DescriptionChips(
    description: String,
    accent: Color,
    enabled: Boolean,
    onDescriptionChange: (String) -> Unit
) {
    val chips = stringArrayResource(R.array.flow_description_chips)
    if (chips.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionLabel(stringResource(R.string.flow_chips_label))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
            verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)
        ) {
            chips.forEach { chip ->
                DescriptionChip(
                    text = chip,
                    selected = isChipSelected(description, chip),
                    accent = accent,
                    enabled = enabled,
                    onClick = { onDescriptionChange(toggleDescriptionChip(description, chip)) }
                )
            }
        }
    }
}

@Composable
private fun DescriptionChip(
    text: String,
    selected: Boolean,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.RadiusPill)
    Box(
        modifier = Modifier
            // 48dp, not the design's 44: this is the smallest target in
            // the app and it is tapped with a work glove on.
            .defaultMinSize(minHeight = 48.dp)
            .background(if (selected) GreenTint else Surface, shape)
            .border(1.5.dp, if (selected) accent else BorderDefault, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (selected) GreenPressed else TextSecondary
        )
    }
}

// ── The confirmation ────────────────────────────────────────────────

@Composable
internal fun ConfirmedStep(
    record: AttendanceRecord?,
    direction: TimeDirection,
    description: String,
    accent: Color,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val out = direction == TimeDirection.OUT
    val recordedAt = if (out) record?.timeOutAt else record?.timeInAt
    val project = if (out) {
        record?.timeOutProjectName ?: record?.timeInProjectName
    } else {
        record?.timeInProjectName
    }
    val none = stringResource(R.string.value_none)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(accent)
            .padding(horizontal = Dimens.ScreenPadding)
            .padding(bottom = Dimens.BottomPadding)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(
                        Color.White.copy(alpha = 0.18f),
                        RoundedCornerShape(32.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(
                        if (out) R.string.done_title_out else R.string.done_title_in
                    ),
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(
                        if (out) R.string.done_sub_out else R.string.done_sub_in
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
            }

            StatusPill(
                text = stringResource(R.string.done_saved),
                foreground = Color.White,
                background = Color.White.copy(alpha = 0.18f),
                icon = Icons.Filled.CloudDone
            )

            // The receipt. A worker walks away from here and cannot open
            // the record again until it reaches the server, so every fact
            // just filed under their name is repeated once, in full.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface, RoundedCornerShape(Dimens.RadiusPanel))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = stringResource(
                            if (out) R.string.confirm_time_out else R.string.confirm_time_in
                        ),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextMuted
                    )
                    Text(
                        text = recordedAt?.atZone(AttendanceZone)?.format(ConfirmTime) ?: none,
                        fontFamily = MonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = accent,
                        maxLines = 1
                    )
                }
                CardDivider()
                ReceiptRow(
                    label = stringResource(R.string.confirm_date),
                    value = recordedAt?.atZone(AttendanceZone)?.format(ConfirmDate) ?: none
                )
                ReceiptRow(stringResource(R.string.confirm_project), project ?: none)
                ReceiptRow(
                    label = stringResource(R.string.confirm_note),
                    value = description.trim().ifEmpty { none }
                )
                if (out) {
                    ReceiptRow(
                        label = stringResource(R.string.confirm_total),
                        value = TotalHours.format(record?.totalMinutes),
                        mono = true
                    )
                }
            }

            Text(
                text = stringResource(
                    if (out) R.string.confirm_next_out else R.string.confirm_next_in
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(Dimens.GapMedium))

        // White on accent, not a translucent panel: this is the only way
        // off the screen and it should look like it.
        PrimaryActionButton(
            label = stringResource(R.string.action_back_home),
            onClick = onDone,
            container = Surface,
            content = accent,
            leadingIcon = Icons.Filled.Home
        )
    }
}

/** One "label ....... value" line of the confirmation receipt. */
@Composable
private fun ReceiptRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = TextMuted
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            fontFamily = if (mono) MonoFamily else null,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.End
        )
    }
}

/** Why a flow ended without recording anything, when there is a why. */
enum class FlowExit { CameraPermission }
