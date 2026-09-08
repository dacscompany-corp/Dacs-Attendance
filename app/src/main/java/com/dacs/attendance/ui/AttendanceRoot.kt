package com.dacs.attendance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.dacs.attendance.ui.components.FailureNotice
import com.dacs.attendance.ui.components.PrimaryActionButton
import com.dacs.attendance.ui.components.SecondaryActionButton
import com.dacs.attendance.ui.components.WorkerBottomNav
import com.dacs.attendance.ui.components.WorkerTab
import com.dacs.attendance.ui.dashboard.DashboardScreen
import com.dacs.attendance.ui.history.HistoryScreen
import com.dacs.attendance.ui.profile.ProfileScreen
import com.dacs.attendance.ui.login.LoginScreen
import com.dacs.attendance.ui.terms.TermsScreen
import com.dacs.attendance.ui.timeflow.TimeFlowScreen
import com.dacs.attendance.ui.theme.Canvas
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green

/**
 * The gate, as a state machine rather than a navigation graph.
 *
 * Login and Terms are not places a worker can navigate BETWEEN -- there
 * is no back from Terms to login, and no forward past Terms without
 * accepting. Modelling that as routes would mean guarding every edge.
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
            onSignOut = viewModel::onSignOut,
            modifier = modifier
        )
    }
}

@Composable
private fun LoadingScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().background(Canvas),
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
            .background(Canvas)
            .padding(Dimens.SheetPadding),
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
            label = stringResource(R.string.action_retry),
            onClick = onRetry
        )
        // Quieter than retry: signing out here loses nothing, but it is
        // not what the worker came to do.
        SecondaryActionButton(
            label = stringResource(R.string.action_log_out),
            onClick = onSignOut
        )
    }
}

/**
 * The signed-in half of the app: the three tabs, and the four-step flow
 * launched from Home.
 *
 * Held as state rather than a nav graph because the flow is MODAL. A
 * worker halfway through a Time In has one way forward and one way back,
 * and offering a tab to wander off to would lose the photo they already
 * took.
 */
@Composable
private fun SignedInArea(
    worker: WorkerProfile,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var flow by rememberSaveable { mutableStateOf<TimeDirection?>(null) }
    var tab by rememberSaveable { mutableStateOf(WorkerTab.HOME) }
    // Bumped after a submission so the dashboard re-reads today's record
    // instead of showing the state from before the worker timed in.
    var reloadKey by rememberSaveable { mutableStateOf(0) }

    val direction = flow
    if (direction != null) {
        // No bottom bar while recording.
        TimeFlowScreen(
            direction = direction,
            onFinished = {
                flow = null
                reloadKey++
            },
            onCancelled = { flow = null },
            modifier = modifier
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                WorkerTab.HOME -> DashboardScreen(
                    worker = worker,
                    onStartFlow = { flow = it },
                    // "See all" above the week strip is the same journey
                    // as tapping History, so it moves the same tab rather
                    // than opening a second copy of the screen.
                    onSeeHistory = { tab = WorkerTab.HISTORY },
                    refreshKey = reloadKey
                )
                WorkerTab.HISTORY -> HistoryScreen()
                WorkerTab.PROFILE -> ProfileScreen(worker = worker, onSignOut = onSignOut)
            }
        }
        WorkerBottomNav(selected = tab, onSelect = { tab = it })
    }
}
