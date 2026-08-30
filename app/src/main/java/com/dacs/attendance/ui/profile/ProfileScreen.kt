package com.dacs.attendance.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dacs.attendance.R
import com.dacs.attendance.domain.AttendanceTerms
import com.dacs.attendance.domain.AttendanceZone
import com.dacs.attendance.domain.PasswordChangeFailure
import com.dacs.attendance.domain.WorkerProfile
import com.dacs.attendance.ui.components.LabeledField
import com.dacs.attendance.ui.components.PrimaryActionButton
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Danger
import com.dacs.attendance.ui.theme.DangerBorder
import com.dacs.attendance.ui.theme.DangerTint
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.GreenTint
import com.dacs.attendance.ui.theme.Hairline
import com.dacs.attendance.ui.theme.MonoFamily
import com.dacs.attendance.ui.theme.TextDisabled
import com.dacs.attendance.ui.theme.TextMuted
import com.dacs.attendance.ui.theme.TextSecondary
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Screen 11 — "My profile".
 *
 * Everything here is READ-ONLY except logging out. A worker cannot edit
 * their own name, position or worker number: those are snapshotted onto
 * every attendance record, and letting the subject of a record rewrite
 * the identity on it would undermine the evidence. Corrections go
 * through the admin, in Users → Navigator.
 */
@Composable
fun ProfileScreen(
    worker: WorkerProfile,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(worker.id) { viewModel.load(worker.id) }

    ProfileContent(
        worker = worker,
        state = state,
        onSignOut = onSignOut,
        onChangePassword = viewModel::changePassword,
        onPasswordDialogClosed = viewModel::resetPasswordChange,
        modifier = modifier
    )
}

@Composable
internal fun ProfileContent(
    worker: WorkerProfile,
    state: ProfileUiState,
    onSignOut: () -> Unit,
    onChangePassword: (String, String) -> Unit,
    onPasswordDialogClosed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTerms by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .background(GreenTint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = worker.initials,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Green
                )
            }
            Column {
                Text(
                    text = worker.displayName ?: worker.firstName,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = worker.position ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }

        if ((worker.status ?: "active") == "active") {
            Text(
                text = stringResource(R.string.profile_active),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Green,
                modifier = Modifier
                    .background(GreenTint, RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderDefault, RoundedCornerShape(Dimens.RadiusLarge))
        ) {
            InfoRow(stringResource(R.string.label_email), worker.email ?: "—")
            Divider()
            InfoRow(stringResource(R.string.profile_position), worker.position ?: "—")
            Divider()
            InfoRow(stringResource(R.string.profile_worker_id), worker.workerIdLabel, mono = true)
        }

        RowButton(
            title = stringResource(R.string.profile_change_password),
            subtitle = stringResource(R.string.profile_change_password_tl),
            icon = Icons.Filled.Key,
            onClick = { showPassword = true }
        )

        // The Terms are readable after acceptance, on purpose: a worker
        // who agreed to something should be able to go back and read it
        // without asking anyone.
        RowButton(
            title = stringResource(R.string.terms_title),
            subtitle = state.acceptedAt
                ?.let { stringResource(R.string.profile_terms_accepted, acceptedDate(it)) }
                // Not yet known -- offline on a phone that has never
                // fetched it. The row still opens the Terms; it just does
                // not claim a date it cannot stand behind.
                ?: stringResource(R.string.profile_terms_sub),
            icon = Icons.Filled.Description,
            onClick = { showTerms = true }
        )

        PrimaryActionButton(
            english = stringResource(R.string.action_log_out),
            tagalog = stringResource(R.string.action_log_out_tl),
            onClick = onSignOut,
            container = Danger
        )
    }

    if (showTerms) {
        TermsReader(onClose = { showTerms = false })
    }

    if (showPassword) {
        ChangePasswordDialog(
            state = state,
            onSubmit = onChangePassword,
            onClose = {
                showPassword = false
                onPasswordDialogClosed()
            }
        )
    }
}

/**
 * Changing your own password, without calling the office.
 *
 * Both boxes are on one screen rather than a two-step wizard: a worker
 * who mistypes the second one has to see the first to fix it, and this
 * is a 6" screen in daylight.
 */
@Composable
private fun ChangePasswordDialog(
    state: ProfileUiState,
    onSubmit: (String, String) -> Unit,
    onClose: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(Dimens.RadiusLarge),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(Dimens.GapMedium)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
            ) {
                Text(
                    text = stringResource(R.string.profile_change_password),
                    style = MaterialTheme.typography.headlineSmall
                )

                if (state.passwordChanged) {
                    // Confirmed, then closed by the worker rather than
                    // automatically: a dialog that vanishes on success
                    // leaves them unsure whether it took.
                    Text(
                        text = stringResource(R.string.password_changed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Green
                    )
                    PrimaryActionButton(
                        english = stringResource(R.string.action_close),
                        tagalog = stringResource(R.string.action_close_tl),
                        onClick = onClose
                    )
                    return@Column
                }

                LabeledField(
                    label = stringResource(R.string.label_new_password),
                    tagalogHint = stringResource(R.string.label_new_password_tl),
                    value = password,
                    onValueChange = { password = it },
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    // ONE toggle for both boxes: they are meant to hold
                    // the same text, so revealing one and masking the
                    // other helps nobody.
                    passwordVisible = visible,
                    onTogglePasswordVisible = { visible = !visible },
                    enabled = !state.changingPassword
                )

                LabeledField(
                    label = stringResource(R.string.label_confirm_password),
                    tagalogHint = stringResource(R.string.label_confirm_password_tl),
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    isPassword = true,
                    passwordVisible = visible,
                    onTogglePasswordVisible = { visible = !visible },
                    enabled = !state.changingPassword
                )

                Text(
                    text = stringResource(R.string.password_rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )

                state.passwordFailure?.let { PasswordFailureNotice(it) }

                PrimaryActionButton(
                    english = stringResource(R.string.action_save_password),
                    tagalog = stringResource(R.string.action_save_password_tl),
                    onClick = { onSubmit(password, confirmation) },
                    loading = state.changingPassword
                )
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.action_close),
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** Bilingual, like every other refusal the worker can be shown. */
@Composable
private fun PasswordFailureNotice(failure: PasswordChangeFailure) {
    val (english, tagalog) = when (failure) {
        PasswordChangeFailure.TooShort ->
            R.string.error_password_short to R.string.error_password_short_tl

        PasswordChangeFailure.Mismatch ->
            R.string.error_password_mismatch to R.string.error_password_mismatch_tl

        PasswordChangeFailure.Reused ->
            R.string.error_password_reused to R.string.error_password_reused_tl

        PasswordChangeFailure.SessionExpired ->
            R.string.att_session_expired to R.string.att_session_expired_tl

        PasswordChangeFailure.NoConnection ->
            R.string.error_no_connection to R.string.error_no_connection_tl

        PasswordChangeFailure.Unexpected ->
            R.string.error_server to R.string.error_server_tl
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DangerTint, RoundedCornerShape(Dimens.RadiusMedium))
            .border(1.dp, DangerBorder, RoundedCornerShape(Dimens.RadiusMedium))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(english),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Danger
        )
        Text(
            text = stringResource(tagalog),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

/** "3 Aug 2026", in Manila -- the zone every date in this app is in. */
private val AcceptedDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun acceptedDate(at: Instant): String =
    at.atZone(AttendanceZone).format(AcceptedDateFormat)

@Composable
private fun TermsReader(onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(Dimens.RadiusLarge),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(Dimens.GapMedium)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)
            ) {
                Text(
                    text = AttendanceTerms.TITLE,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    // The version is shown because acceptance is recorded
                    // against it: a worker can check WHICH wording they
                    // agreed to, not just that they agreed.
                    text = AttendanceTerms.VERSION,
                    fontFamily = MonoFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                AttendanceTerms.clauses.forEachIndexed { index, clause ->
                    Text(
                        text = "${index + 1}. ${clause.heading} ${clause.english}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = clause.tagalog,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                PrimaryActionButton(
                    english = stringResource(R.string.action_close),
                    tagalog = stringResource(R.string.action_close_tl),
                    onClick = onClose
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.SecondaryRow)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (mono) MonoFamily else null
        )
    }
}

@Composable
private fun Divider() {
    // height(), not size(): size() constrains the WIDTH to 1dp as well,
    // which draws a one-pixel dot instead of a rule.
    Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
}

@Composable
private fun RowButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderDefault, RoundedCornerShape(Dimens.RadiusLarge))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = Dimens.SecondaryRow)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            // The title beside it already says what this is; a screen
            // reader repeating it would only slow the row down.
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(22.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextDisabled,
            modifier = Modifier.size(22.dp)
        )
    }
}

