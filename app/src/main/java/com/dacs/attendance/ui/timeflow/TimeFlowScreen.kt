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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dacs.attendance.R
import com.dacs.attendance.domain.AttendanceProject
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.TotalHours
import com.dacs.attendance.domain.isChipSelected
import com.dacs.attendance.domain.toggleDescriptionChip
import com.dacs.attendance.ui.components.AttendanceFailureNotice
import com.dacs.attendance.ui.components.PrimaryActionButton
import com.dacs.attendance.ui.components.StepProgressBar
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Brown
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Field
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.MonoFamily
import com.dacs.attendance.ui.theme.TextMuted
import com.dacs.attendance.ui.theme.TextSecondary

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
    ) {
        if (state.step != FlowStep.Confirmed) {
            StepProgressBar(step = state.stepNumber, total = 4, accent = accent)
        }

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

            FlowStep.Confirmed -> ConfirmedStep(
                record = state.saved,
                direction = direction,
                accent = accent,
                onDone = onFinished,
                modifier = Modifier.weight(1f)
            )
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
        StepHeading(
            english = stringResource(R.string.flow_pick_project),
            tagalog = stringResource(R.string.flow_pick_project_tl)
        )

        failure?.let { AttendanceFailureNotice(it, onRetry = onRetry) }

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
        Text(
            text = project.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
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
        StepHeading(
            english = stringResource(R.string.flow_check_photo),
            tagalog = stringResource(R.string.flow_check_photo_tl)
        )

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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
    ) {
        StepHeading(
            english = stringResource(R.string.flow_describe),
            tagalog = stringResource(R.string.flow_describe_tl)
        )

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

        Spacer(Modifier.height(Dimens.GapSmall))

        PrimaryActionButton(
            english = stringResource(
                if (direction == TimeDirection.IN) R.string.action_submit_in
                else R.string.action_submit_out
            ),
            tagalog = stringResource(R.string.action_submit_tl),
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
            text = stringResource(R.string.flow_chips_label),
            style = MaterialTheme.typography.labelMedium,
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
    accent: Color,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(accent, RoundedCornerShape(Dimens.RadiusHero))
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(
                if (direction == TimeDirection.IN) R.string.flow_done_in
                else R.string.flow_done_out
            ),
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        // Repeats what was saved, so the worker leaves knowing what the
        // record says rather than trusting that it worked.
        record?.timeInProjectName?.let { project ->
            Text(
                text = project,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
        }
        if (direction == TimeDirection.OUT) {
            Text(
                text = TotalHours.format(record?.totalMinutes),
                fontFamily = MonoFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White
            )
        }

        Spacer(Modifier.height(Dimens.GapMedium))

        PrimaryActionButton(
            english = stringResource(R.string.action_done),
            tagalog = stringResource(R.string.action_done_tl),
            onClick = onDone,
            container = Color.White.copy(alpha = 0.18f)
        )
    }
}

@Composable
internal fun StepHeading(english: String, tagalog: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = english, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = tagalog,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}
