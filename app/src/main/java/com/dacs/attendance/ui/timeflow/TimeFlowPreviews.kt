package com.dacs.attendance.ui.timeflow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dacs.attendance.domain.AttendanceFailure
import com.dacs.attendance.domain.AttendanceProject
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.ProjectSystem
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.ui.components.FlowHeader
import com.dacs.attendance.ui.components.FlowTitle
import com.dacs.attendance.ui.theme.AttendanceTheme
import com.dacs.attendance.ui.theme.Brown
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.Surface
import java.time.Instant

/**
 * The four-step flow, every step, both directions, with no device.
 *
 * The camera step is absent on purpose -- a preview cannot open a camera,
 * and pretending otherwise with a placeholder image would show you a
 * screen that does not exist. It is the one step that needs real hardware.
 */
// One from each system, because the picker now merges both and the two
// must be indistinguishable to the worker -- they are just places to stand.
private val previewProjects = listOf(
    AttendanceProject(ProjectSystem.PC, "0f1c1e6e-0000-4000-8000-000000000001", "ABC Building Project"),
    AttendanceProject(ProjectSystem.PM, "0f1c1e6e-0000-4000-8000-000000000002", "Commercial Project"),
    AttendanceProject(ProjectSystem.PC, "0f1c1e6e-0000-4000-8000-000000000003", "Residential Project")
)

/** The white sheet plus the header a step actually runs under. */
@Composable
private fun FlowFrame(
    step: Int,
    accent: Color,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    AttendanceTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface)
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(top = 6.dp)
        ) {
            if (step == 1) {
                FlowHeader(step = 1, total = 4, accent = accent, onBack = {}, modeLabel = "TIME IN")
                FlowTitle(title, subtitle, Modifier.padding(top = 13.dp))
            } else {
                FlowHeader(
                    step = step,
                    total = 4,
                    accent = accent,
                    onBack = {},
                    inlineTitle = title,
                    inlineSubtitle = subtitle
                )
            }
            content()
        }
    }
}

@Preview(name = "1 · Pick project (IN)", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PickProjectPreview() =
    FlowFrame(1, Green, "Which project today?", "Pick the site you are working on.") {
        PickProjectStep(
            projects = previewProjects,
            loading = false,
            selectedKey = previewProjects.first().key,
            accent = Green,
            onSelect = {},
            onConfirm = {},
            onRetry = {},
            failure = null
        )
    }

@Preview(name = "1b · No projects yet", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PickProjectEmptyPreview() =
    FlowFrame(1, Green, "Which project today?", "Pick the site you are working on.") {
        PickProjectStep(
            projects = emptyList(),
            loading = false,
            selectedKey = null,
            accent = Green,
            onSelect = {},
            onConfirm = {},
            onRetry = {},
            failure = null
        )
    }

@Preview(name = "3 · Check photo", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CheckPhotoPreview() =
    FlowFrame(3, Green, "Is this photo clear?", "Check it before you submit") {
        // No file: the frame renders empty, which is exactly what a failed
        // decode would look like on a device.
        CheckPhotoStep(
            photoPath = null,
            capturedAt = Instant.parse("2026-08-18T23:45:00Z"),
            projectName = "ABC Building Project",
            accent = Green,
            onRetake = {},
            onAccept = {}
        )
    }

@Preview(name = "4 · Note", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DescribePreview() =
    FlowFrame(4, Green, "What are you working on?", "Optional — you can skip this") {
        DescribeStep(
            description = "",
            direction = TimeDirection.IN,
            accent = Green,
            submitting = false,
            failure = null,
            projectName = "ABC Building Project",
            // No file, so the thumbnail renders as the empty placeholder --
            // what a failed decode looks like on a device.
            photoPath = null,
            onDescriptionChange = {},
            onSubmit = {}
        )
    }

@Preview(name = "4b · Note with a chip tapped", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DescribeChipPreview() =
    FlowFrame(4, Green, "What are you working on?", "Optional — you can skip this") {
        DescribeStep(
            description = "Masonry",
            direction = TimeDirection.IN,
            accent = Green,
            submitting = false,
            failure = null,
            projectName = "ABC Building Project",
            photoPath = null,
            onDescriptionChange = {},
            onSubmit = {}
        )
    }

@Preview(name = "4c · Refused: already timed in", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DescribeRefusedPreview() =
    FlowFrame(4, Green, "What are you working on?", "Optional — you can skip this") {
        DescribeStep(
            description = "Gate 2, delivery came",
            direction = TimeDirection.IN,
            accent = Green,
            submitting = false,
            failure = AttendanceFailure.AlreadyTimedIn,
            projectName = "ABC Building Project",
            photoPath = null,
            onDescriptionChange = {},
            onSubmit = {}
        )
    }

@Preview(name = "5 · Confirmed (IN)", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ConfirmedInPreview() = AttendanceTheme {
    ConfirmedStep(
        record = AttendanceRecord(
            id = "p", workDate = "2026-08-19", status = AttendanceStatus.WORKING,
            timeInAt = Instant.parse("2026-08-18T23:45:00Z"), timeOutAt = null,
            timeInProjectName = "ABC Building Project", timeOutProjectName = null,
            totalMinutes = null
        ),
        direction = TimeDirection.IN,
        description = "Block A",
        accent = Green,
        onDone = {},
        modifier = Modifier.fillMaxSize()
    )
}

@Preview(name = "5b · Confirmed (OUT, brown)", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ConfirmedOutPreview() = AttendanceTheme {
    ConfirmedStep(
        record = AttendanceRecord(
            id = "p", workDate = "2026-08-19", status = AttendanceStatus.COMPLETE,
            timeInAt = Instant.parse("2026-08-18T23:45:00Z"),
            timeOutAt = Instant.parse("2026-08-19T09:30:00Z"),
            timeInProjectName = "ABC Building Project",
            timeOutProjectName = "ABC Building Project",
            totalMinutes = 585
        ),
        direction = TimeDirection.OUT,
        description = "",
        accent = Brown,
        onDone = {},
        modifier = Modifier.fillMaxSize()
    )
}
