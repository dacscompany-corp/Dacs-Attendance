package com.dacs.attendance.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dacs.attendance.R
import com.dacs.attendance.domain.WorkerProfile
import com.dacs.attendance.ui.components.FailureNotice
import com.dacs.attendance.ui.components.LabeledField
import com.dacs.attendance.ui.components.PrimaryActionButton
import com.dacs.attendance.ui.theme.AttendanceTheme
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.TextMuted
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * Screen 01. Email and password, nothing else -- there is no sign-up and
 * no password reset here on purpose: only the admin creates accounts, and
 * the footer says so in Tagalog because that is the question this screen
 * gets asked.
 */
@Composable
fun LoginScreen(
    onSignedIn: (WorkerProfile) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.signedIn) {
        state.signedIn?.let(onSignedIn)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
    ) {
        Spacer(Modifier.padding(top = 24.dp))

        Image(
            painter = painterResource(R.drawable.dacs_logo),
            contentDescription = null,   // decorative: the title says it
            modifier = Modifier.size(72.dp)
        )

        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Spacer(Modifier.padding(top = Dimens.GapSmall))

        LabeledField(
            label = stringResource(R.string.label_email),
            tagalogHint = stringResource(R.string.label_email_tl),
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            placeholder = "juan@example.com",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            enabled = !state.submitting
        )

        LabeledField(
            label = stringResource(R.string.label_password),
            tagalogHint = stringResource(R.string.label_password_tl),
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            isPassword = true,
            passwordVisible = state.passwordVisible,
            onTogglePasswordVisible = viewModel::onTogglePasswordVisible,
            enabled = !state.submitting
        )

        state.failure?.let { FailureNotice(it) }

        Spacer(Modifier.padding(top = Dimens.GapSmall))

        PrimaryActionButton(
            english = stringResource(R.string.action_sign_in),
            tagalog = stringResource(R.string.action_sign_in_tl),
            onClick = viewModel::onSubmit,
            enabled = state.canSubmit,
            loading = state.submitting
        )

        Text(
            text = stringResource(R.string.login_no_account),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LoginScreenPreview() {
    AttendanceTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
            horizontalAlignment = Alignment.Start
        ) {
            Text("Attendance", style = MaterialTheme.typography.headlineMedium)
            LabeledField(
                label = "Email",
                tagalogHint = "Ang email na binigay ng admin",
                value = "juan@example.com",
                onValueChange = {}
            )
            PrimaryActionButton(english = "SIGN IN", tagalog = "Mag-log in", onClick = {})
        }
    }
}
