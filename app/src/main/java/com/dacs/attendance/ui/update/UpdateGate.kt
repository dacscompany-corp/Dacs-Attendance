package com.dacs.attendance.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Checks for a required update on every resume and shows the dialog
 * when there is one. Placed once, in MainActivity, above everything --
 * so login, Terms, Home and the Time In flow are all behind it.
 *
 * ON_RESUME rather than once at launch: the app is left open in a
 * pocket for days, and resume is also how the worker comes back from
 * the "Install unknown apps" switch and from the installer's Cancel.
 */
@Composable
fun UpdateGate(viewModel: AppUpdateViewModel = hiltViewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.check() }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val shown = state as? UpdateState.Shown ?: return

    UpdateRequiredDialog(
        state = shown,
        onUpdateNow = viewModel::onUpdateNow,
        onAllowInstalls = viewModel::onAllowInstalls,
        onInstall = viewModel::onInstall,
        onRetry = viewModel::onRetry
    )
}
