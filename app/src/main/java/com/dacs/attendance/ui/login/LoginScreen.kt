package com.dacs.attendance.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.dacs.attendance.ui.theme.Surface
import com.dacs.attendance.ui.theme.TextDisabled
import com.dacs.attendance.ui.theme.TextMuted

/**
 * Login. Email and password, nothing else.
 *
 * There is no sign-up and no password reset here on purpose: only the
 * admin creates accounts, and the footer says so -- with a phone icon,
 * because "call the office" is the actual next step and the design makes
 * that a picture rather than a sentence to parse.
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
            .background(Surface)
            .imePadding()
            .padding(
                start = Dimens.SheetPadding,
                end = Dimens.SheetPadding,
                bottom = 28.dp
            )
    ) {
        // Scrolls; the action below does not. On a short phone with the
        // keyboard up, what gives way is the brand mark at the top, never
        // the button the worker is reaching for.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(22.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.dacs_logo),
                    contentDescription = null,   // decorative: the title says it
                    modifier = Modifier.size(46.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.login_brand),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(R.string.login_brand_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = stringResource(R.string.login_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextMuted
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LabeledField(
                    label = stringResource(R.string.label_email),
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    placeholder = "juan@dacsbuilding.com",
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    enabled = !state.submitting
                )

                LabeledField(
                    label = stringResource(R.string.label_password),
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    isPassword = true,
                    passwordVisible = state.passwordVisible,
                    onTogglePasswordVisible = viewModel::onTogglePasswordVisible,
                    enabled = !state.submitting
                )
            }

            state.failure?.let { FailureNotice(it) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)) {
            PrimaryActionButton(
                label = stringResource(R.string.action_sign_in),
                onClick = viewModel::onSubmit,
                trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
                enabled = state.canSubmit,
                loading = state.submitting
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = null,
                    tint = TextDisabled,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = stringResource(R.string.login_no_account),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LoginScreenPreview() {
    AttendanceTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface)
                .padding(Dimens.SheetPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
            LabeledField(label = "Email", value = "juan@dacsbuilding.com", onValueChange = {})
            LabeledField(
                label = "Password",
                value = "hunter22",
                onValueChange = {},
                isPassword = true,
                onTogglePasswordVisible = {}
            )
            PrimaryActionButton(
                label = "SIGN IN",
                onClick = {},
                trailingIcon = Icons.AutoMirrored.Filled.ArrowForward
            )
        }
    }
}
