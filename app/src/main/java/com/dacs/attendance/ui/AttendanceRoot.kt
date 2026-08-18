package com.dacs.attendance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dacs.attendance.R
import com.dacs.attendance.domain.LoginFailure
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.WorkerProfile
import com.dacs.attendance.ui.components.BilingualText
import com.dacs.attendance.ui.components.FailureNotice
import com.dacs.attendance.ui.components.PrimaryActionButton
import com.dacs.attendance.ui.dashboard.DashboardScreen
import com.dacs.attendance.ui.login.LoginScreen
import com.dacs.attendance.ui.terms.TermsScreen
import com.dacs.attendance.ui.timeflow.TimeFlowScreen
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.TextMuted

/**
 * The gate, as a state machine rather than a navigation graph.
 *
 * Login and Terms are not places a worker can navigate BETWEEN -- there
 * is no back from Terms to login, and no forward past Terms without
 * accepting. Modelling that as routes would mean guarding every edge.
 * The four-step Time In/Out flow in B3 is genuine navigation and gets a
 * real NavHost; this is not that.
 */
@Composable
fun AttendanceRoot(
    modifier: Modifier = Modifier,
    viewModel: RootViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        AppState.Loading -> LoadingScreen(modifier)

        AppState.SignedOut -> LoginScreen(
            onSignedIn = viewModel::onSignedIn,
            modifier = modifier
        )

        is AppState.NeedsTerms -> TermsScreen(
            worker = current.worker,
            onAccepted = { viewModel.onTermsAccepted(current.worker) },
            modifier = modifier
        )

        is AppState.GateUnavailable -> GateUnavailableScreen(
            onRetry = { viewModel.onRetryGate(current.worker) },
            onSignOut = viewModel::onSignOut,
            modifier = modifier
        )

        is AppState.SignedIn -> SignedInArea(
            worker = current.worker,
            modifier = modifier
        )
    }
}

@Composable
private fun LoadingScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Green)
    }
}

/**
 * Signed in, offline, and this device has no record of an acceptance. We
 * will not show the Terms here: accepting offline cannot be written to
 * the audit log, so it would be a button that lies.
 */
@Composable
private fun GateUnavailableScreen(
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.terms_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        FailureNotice(LoginFailure.NoConnection)
        PrimaryActionButton(
            english = stringResource(R.string.action_retry),
            tagalog = stringResource(R.string.action_retry_tl),
            onClick = onRetry
        )
        PrimaryActionButton(
            english = stringResource(R.string.action_log_out),
            tagalog = stringResource(R.string.action_log_out_tl),
            onClick = onSignOut,
            container = TextMuted
        )
    }
}

/**
 * The signed-in half of the app: the dashboard, and the four-step flow
 * launched from it.
 *
 * Held as state rather than a nav graph for the same reason the gate is:
 * the flow is modal. A worker in the middle of recording a Time In has
 * one way forward and one way back, and there is no third place to
 * navigate to. B4's history and profile tabs are genuine navigation and
 * will bring a NavHost with them.
 */
@Composable
private fun SignedInArea(
    worker: WorkerProfile,
    modifier: Modifier = Modifier
) {
    var flow by rememberSaveable { mutableStateOf<TimeDirection?>(null) }
    // Bumped after a submission so the dashboard re-reads today's record
    // instead of showing the state from before the worker timed in.
    var reloadKey by rememberSaveable { mutableStateOf(0) }

    when (val direction = flow) {
        null -> key(reloadKey) {
            DashboardScreen(
                worker = worker,
                onStartFlow = { flow = it },
                modifier = modifier
            )
        }

        else -> TimeFlowScreen(
            direction = direction,
            onFinished = {
                flow = null
                reloadKey++
            },
            onCancelled = { flow = null },
            modifier = modifier
        )
    }
}
