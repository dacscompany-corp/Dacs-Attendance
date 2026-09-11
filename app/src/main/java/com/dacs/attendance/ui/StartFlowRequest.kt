package com.dacs.attendance.ui

import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.ui.dashboard.DashboardUiState

/** The intent extra the home-screen widget sets: "IN" or "OUT". */
const val EXTRA_START_FLOW = "com.dacs.attendance.extra.START_FLOW"

/** Exact names only. Anything else is treated as no request, never as a guess. */
fun startFlowFromExtra(raw: String?): TimeDirection? =
    TimeDirection.entries.firstOrNull { it.name == raw }

/** What to do with a Time In / Time Out asked for from outside the app. */
sealed interface StartFlowDecision {
    data class Open(val direction: TimeDirection) : StartFlowDecision

    /** Home already shows the true state; the widget was behind. */
    data object StayOnHome : StartFlowDecision

    /** Not now, and not later either: forget it. */
    data object Drop : StartFlowDecision

    /** Not enough known yet to decide. */
    data object Wait : StartFlowDecision
}

/**
 * A widget tap, resolved against what the app knows NOW.
 *
 * The widget can be behind the truth -- a Time In made moments ago, an
 * admin correction -- so it never gets to open the flow on its own say-so.
 * Home's [DashboardUiState.nextAction] is the same decision the big button
 * on Home makes, and the flow opens only when the two agree. Otherwise the
 * worker lands on Home, which is already showing the right thing, instead
 * of taking a photo the server will refuse.
 *
 * [home] is null wherever Home has not been read yet.
 */
fun resolveStartFlow(
    request: TimeDirection,
    appState: AppState,
    flowOpen: Boolean,
    home: DashboardUiState?
): StartFlowDecision = when {
    appState is AppState.Loading -> StartFlowDecision.Wait
    // Login, Terms, or the offline gate. Dropped rather than held: after
    // signing in the worker should arrive on Home, not in a camera.
    appState !is AppState.SignedIn -> StartFlowDecision.Drop
    // The capture in progress is the worker's. A tap on the home screen
    // must not throw away a photo they already took.
    flowOpen -> StartFlowDecision.Drop
    home == null || home.loading -> StartFlowDecision.Wait
    home.nextAction == request -> StartFlowDecision.Open(request)
    else -> StartFlowDecision.StayOnHome
}
