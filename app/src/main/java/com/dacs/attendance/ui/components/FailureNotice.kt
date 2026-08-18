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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dacs.attendance.R
import com.dacs.attendance.domain.LoginFailure
import com.dacs.attendance.ui.theme.Danger
import com.dacs.attendance.ui.theme.DangerBorder
import com.dacs.attendance.ui.theme.DangerTint
import com.dacs.attendance.ui.theme.Dimens

/**
 * What went wrong, in both languages, in words a worker can act on.
 *
 * Every branch maps to a sentence that tells them what to DO -- try
 * again, or call the office. A raw server string never appears here.
 */
@Composable
fun FailureNotice(failure: LoginFailure, modifier: Modifier = Modifier) {
    val (english, tagalog) = failure.copy()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DangerTint, RoundedCornerShape(Dimens.RadiusMedium))
            .border(1.dp, DangerBorder, RoundedCornerShape(Dimens.RadiusMedium))
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
    }
}

@Composable
private fun LoginFailure.copy(): Pair<String, String> = when (this) {
    LoginFailure.MissingFields ->
        stringResource(R.string.error_missing_fields) to
            stringResource(R.string.error_missing_fields_tl)
    LoginFailure.WrongCredentials ->
        stringResource(R.string.error_wrong_credentials) to
            stringResource(R.string.error_wrong_credentials_tl)
    LoginFailure.AccountInactive ->
        stringResource(R.string.error_account_inactive) to
            stringResource(R.string.error_account_inactive_tl)
    LoginFailure.NotAWorker ->
        stringResource(R.string.error_not_a_worker) to
            stringResource(R.string.error_not_a_worker_tl)
    LoginFailure.TooManyAttempts ->
        stringResource(R.string.error_too_many_attempts) to
            stringResource(R.string.error_too_many_attempts_tl)
    LoginFailure.NoConnection ->
        stringResource(R.string.error_no_connection) to
            stringResource(R.string.error_no_connection_tl)
    LoginFailure.ServerProblem ->
        stringResource(R.string.error_server) to
            stringResource(R.string.error_server_tl)
}
