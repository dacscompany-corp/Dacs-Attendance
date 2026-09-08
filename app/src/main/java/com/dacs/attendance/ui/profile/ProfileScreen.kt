package com.dacs.attendance.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dacs.attendance.R
import com.dacs.attendance.domain.AttendanceTerms
import com.dacs.attendance.domain.AttendanceZone
import com.dacs.attendance.domain.PasswordChangeFailure
import com.dacs.attendance.domain.WorkerProfile
import com.dacs.attendance.ui.components.AppCard
import com.dacs.attendance.ui.components.CardDivider
import com.dacs.attendance.ui.components.LabeledField
import com.dacs.attendance.ui.components.PrimaryActionButton
import com.dacs.attendance.ui.components.StatusPill
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Canvas
import com.dacs.attendance.ui.theme.Danger
import com.dacs.attendance.ui.theme.DangerBorder
import com.dacs.attendance.ui.theme.DangerTint
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.GreenTint
import com.dacs.attendance.ui.theme.MonoFamily
import com.dacs.attendance.ui.theme.Surface
import com.dacs.attendance.ui.theme.TextFaint
import com.dacs.attendance.ui.theme.TextMuted
import com.dacs.attendance.ui.theme.TextSecondary
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "My profile".
 *
 * Everything here is READ-ONLY except the password and logging out. A
 * worker cannot edit their own name, position or worker number: those are
 * snapshotted onto every attendance record, and letting the subject of a
 * record rewrite the identity on it would undermine the evidence.
 * Corrections go through the admin.
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
            .background(Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.ScreenPadding)
            .padding(top = Dimens.GapSmall, bottom = Dimens.GapLarge),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Centred, as the design draws it: this screen answers "who am I
        // signed in as", and a centred portrait says that faster than a
        // row of details reading left to right.
        AppCard(radius = Dimens.RadiusPanel, padding = 20.dp) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .background(Green, RoundedCornerShape(Dimens.RadiusPanel)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = worker.initials,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = Color.White
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = worker.displayName ?: worker.firstName,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = worker.positionAndId,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
                if ((worker.status ?: "active") == "active") {
                    StatusPill(
                        text = stringResource(R.string.profile_active),
                        foreground = Green,
                        background = GreenTint,
                        icon = Icons.Filled.Verified
                    )
                }
            }
        }

        AppCard(padding = 0.dp) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                InfoRow(
                    label = stringResource(R.string.label_email),
                    value = worker.email ?: stringResource(R.string.value_none)
                )
                CardDivider()
                InfoRow(
                    label = stringResource(R.string.profile_worker_id),
                    value = worker.workerIdLabel,
                    mono = true
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RowButton(
                title = stringResource(R.string.profile_change_password),
                subtitle = stringResource(R.string.profile_change_password_sub),
                icon = Icons.Filled.Key,
                // Green, unlike the Terms row's grey: this is the only
                // thing on the screen a worker can actually change.
                iconTint = Green,
                onClick = { showPassword = true }
            )

            // The Terms are readable after acceptance, on purpose: a
            // worker who agreed to something should be able to go back and
            // read it without asking anyone.
            RowButton(
                title = stringResource(R.string.terms_title),
                subtitle = state.acceptedAt
                    ?.let { stringResource(R.string.profile_terms_accepted, acceptedDate(it)) }
                    // Not yet known -- offline on a phone that has never
                    // fetched it. The row still opens the Terms; it just
                    // does not claim a date it cannot stand behind.
                    ?: stringResource(R.string.profile_terms_sub),
                icon = Icons.Filled.Description,
                iconTint = TextMuted,
                onClick = { showTerms = true }
            )

            LogOutRow(onSignOut)
        }
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
 * The exit.
 *
 * Not a filled red slab. Logging out is not the primary action here and
 * should not read as one -- it is the way out, sitting at the foot of the
 * screen where the design puts it, in red so it is unmistakable when
 * wanted and easy to skip past when not.
 */
@Composable
private fun LogOutRow(onSignOut: () -> Unit) {
    val shape = RoundedCornerShape(Dimens.RadiusRow)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, shape)
            .border(1.dp, DangerBorder, shape)
            .clickable(onClick = onSignOut)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = null,
            tint = Danger,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = stringResource(R.string.action_log_out),
            style = MaterialTheme.typography.labelMedium,
            color = Danger
        )
    }
}

/**
 * Changing your own password, without calling the office.
 *
 * Both boxes are on one screen rather than a two-step wizard: a worker
 * who mistypes the second one has to see the first to fix it, and this is
 * a 6" screen in daylight.
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
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(Dimens.RadiusPanel),
            color = Surface
        ) {
            Column(
                modifier = Modifier
                    .padding(Dimens.ScreenPadding)
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
                        label = stringResource(R.string.action_close).uppercase(Locale.ENGLISH),
                        onClick = onClose
                    )
                    return@Column
                }

                LabeledField(
                    label = stringResource(R.string.label_new_password),
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
                    label = stringResource(R.string.action_save_password),
                    onClick = { onSubmit(password, confirmation) },
                    loading = state.changingPassword
                )
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.action_close),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
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
            .background(DangerTint, RoundedCornerShape(Dimens.RadiusField))
            .border(1.dp, DangerBorder, RoundedCornerShape(Dimens.RadiusField))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = stringResource(english),
            style = MaterialTheme.typography.labelMedium,
            color = Danger
        )
        Text(
            text = stringResource(tagalog),
            style = MaterialTheme.typography.bodySmall,
            color = Danger
        )
    }
}

/** "3 Aug 2026", in Manila -- the zone every date in this app is in. */
private val AcceptedDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

private fun acceptedDate(at: Instant): String =
    at.atZone(AttendanceZone).format(AcceptedDateFormat)

@Composable
private fun TermsReader(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(Dimens.RadiusPanel),
            color = Surface
        ) {
            Column(
                modifier = Modifier
                    .padding(Dimens.ScreenPadding)
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
                }
                Spacer(Modifier.size(Dimens.GapSmall))
                PrimaryActionButton(
                    label = stringResource(R.string.action_close).uppercase(Locale.ENGLISH),
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
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            fontFamily = if (mono) MonoFamily else null,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun RowButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.RadiusRow)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, shape)
            .border(1.dp, BorderDefault, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            // The title beside it already says what this is; a screen
            // reader repeating it would only slow the row down.
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(21.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextFaint,
            modifier = Modifier.size(20.dp)
        )
    }
}
