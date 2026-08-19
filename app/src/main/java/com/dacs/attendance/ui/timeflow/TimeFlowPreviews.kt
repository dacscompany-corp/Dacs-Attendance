package com.dacs.attendance.ui.timeflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.dacs.attendance.domain.AttendanceFailure
import com.dacs.attendance.domain.AttendanceProject
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.ui.components.StepProgressBar
import com.dacs.attendance.ui.theme.AttendanceTheme
import com.dacs.attendance.ui.theme.Brown
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import java.time.Instant

/**
 * The four-step flow, every step, both directions, with no device.
 *
 * The camera step is absent on purpose -- a preview cannot open a
 * camera, and pretending otherwise with a placeholder image would show
 * you a screen that does not exist. It is the one step that needs real
 * hardware.
 */
private val previewProjects = listOf(
    AttendanceProject(1, "ABC Building Project"),
    AttendanceProject(2, "Commercial Project"),
    AttendanceProject(3, "Residential Project")
)

@Composable
private fun FlowFrame(step: Int, accent: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    AttendanceTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
        ) {
            StepProgressBar(step = step, total = 4, accent = accent)
            content()
        }
    }
}

@Preview(name = "04 · Pick project (IN)", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PickProjectPreview() = FlowFrame(1, Green) {
    PickProjectStep(
        projects = previewProjects,
        loading = false,
        selectedId = 1,
        accent = Green,
        onSelect = {},
        onConfirm = {},
        onRetry = {},
        failure = null
    )
}

@Preview(name = "04b · No projects yet", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PickProjectEmptyPreview() = FlowFrame(1, Green) {
    PickProjectStep(
        projects = emptyList(),
        loading = false,
        selectedId = null,
        accent = Green,
        onSelect = {},
        onConfirm = {},
        onRetry = {},
        failure = null
    )
}

@Preview(name = "06 · Check photo", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CheckPhotoPreview() = FlowFrame(3, Green) {
    // No file: the frame renders empty, which is exactly what a failed
    // decode would look like on a device.
    CheckPhotoStep(photoPath = null, accent = Green, onRetake = {}, onAccept = {})
}

@Preview(name = "07 · Description", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DescribePreview() = FlowFrame(4, Green) {
    DescribeStep(
        description = "",
        direction = TimeDirection.IN,
        accent = Green,
        submitting = false,
        failure = null,
        onDescriptionChange = {},
        onSubmit = {}
    )
}

@Preview(name = "07b · Refused: already timed in", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DescribeRefusedPreview() = FlowFrame(4, Green) {
    DescribeStep(
        description = "Gate 2, may delivery",
        direction = TimeDirection.IN,
        accent = Green,
        submitting = false,
        failure = AttendanceFailure.AlreadyTimedIn,
        onDescriptionChange = {},
        onSubmit = {}
    )
}

@Preview(name = "08 · Confirmed (IN)", showBackground = true, widthDp = 390, heightDp = 844)
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
        accent = Green,
        onDone = {},
        modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding)
    )
}

@Preview(name = "08b · Confirmed (OUT, brown)", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ConfirmedOutPreview() = AttendanceTheme {
    ConfirmedStep(
        record = AttendanceRecord(
            id = "p", workDate = "2026-08-19", status = AttendanceStatus.COMPLETE,
            timeInAt = Instant.parse("2026-08-18T23:45:00Z"),
            timeOutAt = Instant.parse("2026-08-19T09:30:00Z"),
            timeInProjectName = "ABC Building Project", timeOutProjectName = "ABC Building Project",
            totalMinutes = 585
        ),
        direction = TimeDirection.OUT,
        accent = Brown,
        onDone = {},
        modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding)
    )
}
