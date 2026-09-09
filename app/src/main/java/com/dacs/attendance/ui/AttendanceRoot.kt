package com.dacs.attendance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.dacs.attendance.ui.timeflow.FlowExit
import com.dacs.attendance.ui.timeflow.TimeFlowScreen
import com.dacs.attendance.ui.theme.Canvas
import com.dacs.attendance.ui.theme.Danger
import com.dacs.attendance.ui.theme.DangerBorder
import com.dacs.attendance.ui.theme.DangerTint
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

    /** Set when a flow was abandoned for a reason worth telling Home about. */
    var exitNotice by rememberSaveable { mutableStateOf<FlowExit?>(null) }

    val direction = flow
    if (direction != null) {
        // No bottom bar while recording.
        TimeFlowScreen(
            direction = direction,
            onFinished = {
                flow = null
                reloadKey++
            },
            onCancelled = { reason ->
                flow = null
                // Home explains why nothing was recorded. Landing back on
                // an unchanged dashboard, with the day still not timed in
                // and no word about it, is how a worker concludes the app
                // simply lost their Time In.
                exitNotice = reason
            },
            modifier = modifier
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Sits ABOVE the dashboard rather than inside it: the dashboard
        // reports today's record, and this reports why there isn't one.
        // Two different claims, and folding them together would make the
        // dashboard's own failure state mean two things.
        if (tab == WorkerTab.HOME) {
            exitNotice?.let { reason ->
                FlowExitNotice(
                    reason = reason,
                    onDismiss = { exitNotice = null },
                    modifier = Modifier.padding(
                        start = Dimens.ScreenPadding,
                        end = Dimens.ScreenPadding,
                        top = Dimens.ScreenPadding
                    )
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                WorkerTab.HOME -> DashboardScreen(
                    worker = worker,
                    onStartFlow = {
                        flow = it
                        // A new attempt clears the last explanation: it is
                        // about to be either answered or repeated. Done
                        // here, on the event, rather than during the flow's
                        // composition -- writing state while composing is
                        // not something Compose promises to honour, and a
                        // dropped write here means the notice never shows.
                        exitNotice = null
                    },
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

/**
 * Why the last attempt recorded nothing.
 *
 * Deliberately dismissible and deliberately not an error the dashboard
 * owns: the worker did not fail at anything, they declined a permission,
 * and the notice exists so that landing back on an unchanged Home does
 * not read as the app having lost their Time In.
 */
@Composable
private fun FlowExitNotice(
    reason: FlowExit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (english, tagalog) = when (reason) {
        FlowExit.CameraPermission ->
            stringResource(R.string.home_flow_cancelled_camera) to
                stringResource(R.string.home_flow_cancelled_camera_tl)
    }

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
        Text(text = tagalog, style = MaterialTheme.typography.bodySmall, color = Danger)
        TextButton(onClick = onDismiss) {
            Text(
                text = stringResource(R.string.action_dismiss),
                fontWeight = FontWeight.Bold,
                color = Danger
            )
        }
    }
}
