package com.dacs.attendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dacs.attendance.R
import com.dacs.attendance.domain.AttendanceFailure
import com.dacs.attendance.ui.theme.Danger
import com.dacs.attendance.ui.theme.DangerBorder
import com.dacs.attendance.ui.theme.DangerTint
import com.dacs.attendance.ui.theme.Dimens

/**
 * Why a Time In or Time Out was refused, in both languages.
 *
 * Retry is offered only where retrying can actually help. "You already
 * timed in today" will never succeed on a second attempt, and a button
 * that cannot work is worse than no button.
 */
@Composable
fun AttendanceFailureNotice(
    failure: AttendanceFailure,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    val (english, tagalog) = failure.copy()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DangerTint, RoundedCornerShape(Dimens.RadiusField))
            .border(1.dp, DangerBorder, RoundedCornerShape(Dimens.RadiusField))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = english,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Danger
        )
        Text(
            text = tagalog,
            style = MaterialTheme.typography.bodySmall,
            color = Danger
        )
        if (onRetry != null && failure.worthRetrying) {
            TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.action_retry),
                    color = Danger,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Retrying only makes sense when the refusal was about the connection or
 * the moment, not about the state of the day.
 */
private val AttendanceFailure.worthRetrying: Boolean
    get() = when (this) {
        AttendanceFailure.NoConnection,
        AttendanceFailure.Unexpected,
        AttendanceFailure.DeviceClockWrong,
        // A fix can improve where a worker stands still: walking a few
        // metres into the open is exactly the fix for both of these.
        AttendanceFailure.OutsideRadius,
        AttendanceFailure.ProjectGeofenceUnavailable -> true
        // Deliberately NOT LocationPermissionDenied: retrying without
        // changing the setting fails identically, and offering the button
        // teaches the worker the app is broken rather than that the
        // permission is off.
        else -> false
    }

@Composable
private fun AttendanceFailure.copy(): Pair<String, String> = when (this) {
    AttendanceFailure.AlreadyTimedIn ->
        stringResource(R.string.att_already_timed_in) to
            stringResource(R.string.att_already_timed_in_tl)
    AttendanceFailure.NotTimedIn ->
        stringResource(R.string.att_not_timed_in) to
            stringResource(R.string.att_not_timed_in_tl)
    AttendanceFailure.AlreadyComplete ->
        stringResource(R.string.att_already_complete) to
            stringResource(R.string.att_already_complete_tl)
    AttendanceFailure.TimeOutBeforeTimeIn ->
        stringResource(R.string.att_timeout_before_timein) to
            stringResource(R.string.att_timeout_before_timein_tl)
    AttendanceFailure.DeviceClockWrong ->
        stringResource(R.string.att_clock_wrong) to
            stringResource(R.string.att_clock_wrong_tl)
    AttendanceFailure.ShiftTooLong ->
        stringResource(R.string.att_shift_too_long) to
            stringResource(R.string.att_shift_too_long_tl)
    AttendanceFailure.ProjectUnavailable ->
        stringResource(R.string.att_project_unavailable) to
            stringResource(R.string.att_project_unavailable_tl)
    AttendanceFailure.NoOwnerAssigned ->
        stringResource(R.string.att_no_owner) to
            stringResource(R.string.att_no_owner_tl)
    AttendanceFailure.AccountInactive ->
        stringResource(R.string.error_account_inactive) to
            stringResource(R.string.error_account_inactive_tl)
    AttendanceFailure.NotAWorker ->
        stringResource(R.string.error_not_a_worker) to
            stringResource(R.string.error_not_a_worker_tl)
    AttendanceFailure.OutsideRadius ->
        stringResource(R.string.att_outside_radius) to
            stringResource(R.string.att_outside_radius_tl)
    AttendanceFailure.MockLocation ->
        stringResource(R.string.att_mock_location) to
            stringResource(R.string.att_mock_location_tl)
    AttendanceFailure.LocationPermissionDenied ->
        stringResource(R.string.att_location_denied) to
            stringResource(R.string.att_location_denied_tl)
    AttendanceFailure.ProjectGeofenceUnavailable ->
        stringResource(R.string.att_geofence_missing) to
            stringResource(R.string.att_geofence_missing_tl)
    AttendanceFailure.SessionExpired ->
        stringResource(R.string.att_session_expired) to
            stringResource(R.string.att_session_expired_tl)
    AttendanceFailure.NoConnection ->
        stringResource(R.string.error_no_connection) to
            stringResource(R.string.error_no_connection_tl)
    AttendanceFailure.Unexpected ->
        stringResource(R.string.error_server) to
            stringResource(R.string.error_server_tl)
}
