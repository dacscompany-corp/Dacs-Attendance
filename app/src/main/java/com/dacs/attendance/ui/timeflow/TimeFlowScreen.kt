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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.dacs.attendance.R
import com.dacs.attendance.domain.AttendanceProject
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.AttendanceZone
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.TotalHours
import com.dacs.attendance.domain.isChipSelected
import com.dacs.attendance.domain.toggleDescriptionChip
import com.dacs.attendance.ui.components.AttendanceFailureNotice
import com.dacs.attendance.ui.components.PrimaryActionButton
import com.dacs.attendance.ui.components.FlowHeader
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Brown
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Field
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.MonoFamily
import com.dacs.attendance.ui.theme.Surface
import com.dacs.attendance.ui.theme.TextPrimary
import com.dacs.attendance.ui.theme.TextMuted
import com.dacs.attendance.ui.theme.TextSecondary

private val ConfirmTime = DateTimeFormatter.ofPattern("h:mm a")
private val ConfirmDate = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * The question each step asks. Kept here rather than inside the steps so
 * the one shared header can render it beside the back arrow and the
 * "STEP n / 4" counter, as the design draws them.
 */
private fun FlowStep.headings(): Pair<Int, Int> = when (this) {
    FlowStep.PickProject -> R.string.flow_pick_project to R.string.flow_pick_project_tl
    FlowStep.TakePhoto -> R.string.flow_take_photo to R.string.flow_take_photo_tl
    FlowStep.CheckPhoto -> R.string.flow_check_photo to R.string.flow_check_photo_tl
    FlowStep.Describe -> R.string.flow_describe to R.string.flow_describe_tl
    FlowStep.Confirmed -> R.string.flow_done_in to R.string.flow_done_in_tl
}

/**
 * Screens 04-08, as ONE flow parameterised by direction.
 *
 * The design says Time Out is "the same five, in brown". So colour and
 * copy come from [TimeDirection] and the steps themselves are shared --
 * writing Time Out separately is how the two halves drift apart, and a
 * drift here means a worker's evening does not match their morning.
 */
@Composable
fun TimeFlowScreen(
    direction: TimeDirection,
    onFinished: () -> Unit,
    onCancelled: () -> Unit,
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
        if (state.step == FlowStep.PickProject) onCancelled() else viewModel.onBack()
    }

    // Full-bleed, outside the padded column: the confirmation is a whole
    // green screen in the design, not a card sitting on a white one.
    if (state.step == FlowStep.Confirmed) {
        ConfirmedStep(
            record = state.saved,
            direction = direction,
            description = state.description,
            accent = accent,
            onDone = onFinished,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // White, not the dashboard's canvas: the flow is a sequence of
            // sheets in the design, and the cards on them need to read as
            // raised off something.
            .background(Surface)
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
    ) {
        val (english, tagalog) = state.step.headings()
        FlowHeader(
            step = state.stepNumber,
            total = 4,
            english = stringResource(english),
            tagalog = stringResource(tagalog),
            accent = accent,
            // The same decision the system back button makes, because a
            // worker who taps one and then the other must not get two
            // different behaviours out of the same screen.
            onBack = {
                if (state.step == FlowStep.PickProject) onCancelled() else viewModel.onBack()
            }
        )

        when (state.step) {
            FlowStep.PickProject -> PickProjectStep(
                projects = state.projects,
                loading = state.loadingProjects,
                selectedId = state.selectedProjectId,
                accent = accent,
                onSelect = viewModel::onProjectSelected,
                onConfirm = viewModel::onProjectConfirmed,
                onRetry = viewModel::onRetryProjects,
                failure = state.failure,
                modifier = Modifier.weight(1f)
            )

            FlowStep.TakePhoto -> CameraCapture(
                onPhotoTaken = viewModel::onPhotoTaken,
                modifier = Modifier.weight(1f),
                // The preview caption is the burned-in one, and it names
                // the project the worker picked one screen ago.
                projectName = state.selectedProject?.name,
                accent = accent
            )

            FlowStep.CheckPhoto -> CheckPhotoStep(
                photoPath = state.photo?.file?.absolutePath,
                accent = accent,
                onRetake = viewModel::onRetakePhoto,
                onAccept = viewModel::onPhotoAccepted,
                modifier = Modifier.weight(1f)
            )

            FlowStep.Describe -> DescribeStep(
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

            FlowStep.Confirmed -> Unit  // handled above, full-bleed
        }
    }
}

// ── 04 · Select project ─────────────────────────────────────────────

@Composable
internal fun PickProjectStep(
    projects: List<AttendanceProject>,
    loading: Boolean,
    selectedId: Long?,
    accent: Color,
    onSelect: (Long) -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    failure: com.dacs.attendance.domain.AttendanceFailure?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
    ) {
        failure?.let { AttendanceFailureNotice(it, onRetry = onRetry) }

        Text(
            text = stringResource(R.string.flow_active_projects).uppercase(Locale.getDefault()),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.em,
            color = TextMuted
        )

        when {
            loading -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                CircularProgressIndicator(color = accent)
            }

            projects.isEmpty() && failure == null -> Text(
                // A real state, not an error: the admin has not added any
                // projects yet, and no amount of retrying fixes that.
                text = stringResource(R.string.flow_no_projects),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.weight(1f)
            )

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)
            ) {
                items(projects, key = { it.id }) { project ->
                    ProjectRow(
                        project = project,
                        selected = project.id == selectedId,
                        accent = accent,
                        onClick = { onSelect(project.id) }
                    )
                }
            }
        }

        Text(
            // Shown ALWAYS, not only when the list is empty: a worker
            // whose site is missing from four listed projects needs to
            // know whose job it is to add it.
            text = stringResource(R.string.flow_missing_project),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )

        PrimaryActionButton(
            english = stringResource(R.string.action_continue),
            tagalog = stringResource(R.string.action_continue_tl),
            onClick = onConfirm,
            container = accent,
            enabled = selectedId != null
        )
    }
}

@Composable
private fun ProjectRow(
    project: AttendanceProject,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.ProjectRow)
            .background(
                // Tinted from the accent, not a fixed green: a Time Out
                // is brown all the way through, and a green row inside a
                // brown flow reads as the wrong screen.
                color = if (selected) accent.copy(alpha = 0.12f) else Field,
                shape = RoundedCornerShape(Dimens.RadiusMedium)
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else BorderDefault,
                shape = RoundedCornerShape(Dimens.RadiusMedium)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .then(
                        if (selected) {
                            Modifier.background(accent, CircleShape)
                        } else {
                            Modifier.border(2.dp, BorderDefault, CircleShape)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Text(
                text = project.name,
                fontSize = 19.sp,
                lineHeight = 24.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (selected) accent else TextPrimary
            )
        }
    }
}

// ── 06 · Check photo ────────────────────────────────────────────────

@Composable
internal fun CheckPhotoStep(
    photoPath: String?,
    accent: Color,
    onRetake: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
    ) {
        // Coil, not a hand-rolled BitmapFactory decode: the file carries
        // EXIF rotation and half the phones in the field would show a
        // sideways selfie without it.
        AsyncImage(
            model = photoPath,
            contentDescription = stringResource(R.string.flow_check_photo),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Field, RoundedCornerShape(Dimens.RadiusLarge))
        )

        PrimaryActionButton(
            english = stringResource(R.string.action_use_photo),
            tagalog = stringResource(R.string.action_use_photo_tl),
            onClick = onAccept,
            container = accent
        )
        TextButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.action_retake),
                color = TextSecondary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── 07 · Description ────────────────────────────────────────────────

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
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
      ) {
        // What the worker is about to submit, restated on the last screen
        // before SUBMIT. Four steps in, "which project did I pick?" is a
        // real question, and the answer is on the record forever.
        projectName?.let { CapturedContextRow(it, photoPath, accent) }

        Column(verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.flow_describe_label),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    // Kept beside the label rather than under the box: a
                    // worker deciding whether to skip this needs to know
                    // it is optional BEFORE they start typing.
                    text = stringResource(R.string.flow_describe_optional),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextMuted
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                enabled = !submitting,
                placeholder = { Text(stringResource(R.string.flow_describe_hint), color = TextMuted) },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 120.dp),
                shape = RoundedCornerShape(Dimens.RadiusMedium),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Field,
                    unfocusedContainerColor = Field,
                    focusedBorderColor = accent,
                    unfocusedBorderColor = BorderDefault
                )
            )
        }

        DescriptionChips(
            description = description,
            accent = accent,
            enabled = !submitting,
            onDescriptionChange = onDescriptionChange
        )

        failure?.let { AttendanceFailureNotice(it) }
      }

      // Pinned to the foot of the sheet, not floated up under the chips:
      // the design gives SUBMIT margin-top:auto, and on a screen the
      // worker scrolls, the action must stay where their thumb is.
      Spacer(Modifier.height(Dimens.GapMedium))

      PrimaryActionButton(
          english = stringResource(
              if (direction == TimeDirection.IN) R.string.action_submit_in
              else R.string.action_submit_out
          ),
          tagalog = stringResource(
              if (direction == TimeDirection.IN) R.string.action_submit_in_tl
              else R.string.action_submit_out_tl
          ),
          onClick = onSubmit,
          container = accent,
          loading = submitting
      )
    }
}

/** Project + "photo taken", so step 4 shows what steps 1-3 produced. */
@Composable
private fun CapturedContextRow(projectName: String, photoPath: String?, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Field, RoundedCornerShape(Dimens.RadiusSmall))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = photoPath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .background(BorderDefault, RoundedCornerShape(9.dp))
                .clip(RoundedCornerShape(9.dp))
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = projectName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
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
 * "Or tap a common one".
 *
 * The reason this exists is physical: step 4 is done standing up,
 * one-handed, outdoors, often with gloves on. A chip is one tap where
 * the keyboard is twenty, and the toggling rules live in
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

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
        Text(
            text = stringResource(R.string.flow_chips_label).uppercase(Locale.getDefault()),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.em,
            color = TextMuted
        )
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
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            // 48dp, not the design's 44: this is the smallest target in
            // the app and it is tapped with a work glove on.
            .defaultMinSize(minHeight = 48.dp)
            .background(
                // Tinted from the accent rather than a fixed green, so
                // Time Out stays brown throughout as the design says.
                color = if (selected) accent.copy(alpha = 0.12f) else Color.Transparent,
                shape = shape
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else BorderDefault,
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) accent else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ── 08 · Confirmation ───────────────────────────────────────────────

@Composable
internal fun ConfirmedStep(
    record: AttendanceRecord?,
    direction: TimeDirection,
    description: String,
    accent: Color,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recordedAt = if (direction == TimeDirection.IN) record?.timeInAt else record?.timeOutAt
    val project = if (direction == TimeDirection.IN) {
        record?.timeInProjectName
    } else {
        record?.timeOutProjectName ?: record?.timeInProjectName
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(accent)
            .padding(horizontal = 26.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier
                    .size(126.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(
                        if (direction == TimeDirection.IN) R.string.flow_done_in
                        else R.string.flow_done_out
                    ),
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(
                        if (direction == TimeDirection.IN) R.string.flow_done_in_tl
                        else R.string.flow_done_out_tl
                    ),
                    fontSize = 19.sp,
                    color = Color.White.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center
                )
            }

            // The receipt. A worker walks away from here and cannot open
            // the record again until it reaches the server, so every fact
            // just filed under their name is repeated once, in full.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ReceiptRow(stringResource(R.string.confirm_project), project ?: "—")
                ReceiptDivider()
                ReceiptRow(
                    label = stringResource(
                        if (direction == TimeDirection.IN) R.string.confirm_time_in
                        else R.string.confirm_time_out
                    ),
                    value = recordedAt?.atZone(AttendanceZone)?.format(ConfirmTime) ?: "—",
                    mono = true
                )
                ReceiptDivider()
                ReceiptRow(
                    label = stringResource(R.string.confirm_date),
                    value = recordedAt?.atZone(AttendanceZone)?.format(ConfirmDate) ?: "—"
                )
                if (direction == TimeDirection.OUT) {
                    ReceiptDivider()
                    ReceiptRow(
                        label = stringResource(R.string.confirm_total),
                        value = TotalHours.format(record?.totalMinutes),
                        mono = true
                    )
                }
                description.trim().takeIf { it.isNotEmpty() }?.let { note ->
                    ReceiptDivider()
                    ReceiptRow(stringResource(R.string.confirm_note), note)
                }
            }

            Text(
                text = stringResource(
                    if (direction == TimeDirection.IN) R.string.confirm_next_in
                    else R.string.confirm_next_out
                ),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(Dimens.GapMedium))

        // White on green, not a translucent panel: this is the only way
        // off the screen and it should look like it.
        PrimaryActionButton(
            english = stringResource(R.string.action_back_home),
            tagalog = stringResource(R.string.action_back_home_tl),
            onClick = onDone,
            container = Color.White,
            content = accent
        )
    }
}

/** One "label ....... value" line of the confirmation receipt. */
@Composable
private fun ReceiptRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.75f)
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            fontFamily = if (mono) MonoFamily else null,
            fontSize = if (mono) 24.sp else 17.sp,
            lineHeight = if (mono) 28.sp else 22.sp,
            fontWeight = if (mono) FontWeight.Medium else FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ReceiptDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.18f))
    )
}

