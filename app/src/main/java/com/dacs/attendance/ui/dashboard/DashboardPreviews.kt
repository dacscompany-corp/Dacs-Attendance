package com.dacs.attendance.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.dacs.attendance.domain.AttendanceFailure
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.WorkerProfile
import com.dacs.attendance.domain.weekStrip
import com.dacs.attendance.ui.theme.AttendanceTheme
import java.time.Instant

/**
 * Every dashboard state, side by side, with no device and no network.
 *
 * Open this file in Android Studio and use the Split or Design pane.
 * These four are the whole state space of Home -- if the status card or
 * the hero is wrong anywhere, it is wrong here first.
 */
private val previewWorker = WorkerProfile(
    id = "preview",
    email = "juan@dacsbuilding.com",
    displayName = "Juan dela Cruz",
    position = "Mason",
    workerNo = 42,
    role = "worker",
    status = "active"
)

private fun previewRecord(
    status: AttendanceStatus,
    timeOut: Instant? = null,
    totalMinutes: Int? = null
) = AttendanceRecord(
    id = "preview-record",
    workDate = "2026-08-19",
    status = status,
    timeInAt = Instant.parse("2026-08-18T23:45:00Z"), // 07:45 Manila
    timeOutAt = timeOut,
    timeInProjectName = "ABC Building Project",
    timeOutProjectName = null,
    totalMinutes = totalMinutes
)

/** Mon and Tue worked, today is Wednesday. */
private val previewWeek = weekStrip(
    records = listOf(
        previewRecord(AttendanceStatus.COMPLETE).copy(workDate = "2026-08-17"),
        previewRecord(AttendanceStatus.COMPLETE).copy(workDate = "2026-08-18")
    ),
    today = java.time.LocalDate.parse("2026-08-19")
)

@Preview(name = "01 · Nothing recorded yet", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DashboardIdlePreview() {
    AttendanceTheme {
        DashboardContent(
            worker = previewWorker,
            state = DashboardUiState(loading = false, record = null, week = previewWeek),
            onStartFlow = {},
            onSeeHistory = {},
            onRetry = {}
        )
    }
}

@Preview(name = "02 · Working", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DashboardWorkingPreview() {
    AttendanceTheme {
        DashboardContent(
            worker = previewWorker,
            state = DashboardUiState(
                loading = false,
                record = previewRecord(AttendanceStatus.WORKING),
                totalHoursLabel = "3h 47m",
                totalMinutes = 227,
                week = previewWeek
            ),
            onStartFlow = {},
            onSeeHistory = {},
            onRetry = {}
        )
    }
}

@Preview(name = "03 · Day complete", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DashboardCompletePreview() {
    AttendanceTheme {
        DashboardContent(
            worker = previewWorker,
            state = DashboardUiState(
                loading = false,
                record = previewRecord(
                    AttendanceStatus.COMPLETE,
                    timeOut = Instant.parse("2026-08-19T09:30:00Z"),
                    totalMinutes = 585
                ),
                totalHoursLabel = "9h 45m",
                totalMinutes = 585,
                week = previewWeek
            ),
            onStartFlow = {},
            onSeeHistory = {},
            onRetry = {}
        )
    }
}

@Preview(name = "04 · No signal", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DashboardOfflinePreview() {
    AttendanceTheme {
        DashboardContent(
            worker = previewWorker,
            state = DashboardUiState(
                loading = false,
                record = null,
                failure = AttendanceFailure.NoConnection,
                week = previewWeek
            ),
            onStartFlow = {},
            onSeeHistory = {},
            onRetry = {}
        )
    }
}
