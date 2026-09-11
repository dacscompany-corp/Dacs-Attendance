package com.dacs.attendance.widget

import kotlinx.coroutines.CancellationException

/**
 * Asks the home-screen widget to re-read today.
 *
 * Pushed by the app rather than pulled on a timer: Android lets a widget
 * wake itself at most every 30 minutes, and a widget that still says "Not
 * timed in" half an hour after the worker timed in is worse than none.
 *
 * An interface so the callers can be tested without a widget host.
 */
interface WidgetRefresher {
    suspend fun refresh()
}

/**
 * [WidgetRefresher.refresh], with every failure swallowed.
 *
 * The widget is a convenience riding on top of the queue, the mirror and
 * the session. None of those may ever fail because the widget did.
 * Cancellation still propagates -- swallowing it would break structured
 * concurrency for the caller, not just for the widget.
 */
internal suspend fun WidgetRefresher.refreshQuietly() {
    try {
        refresh()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (ignored: Exception) {
        // A widget that lags until its next refresh is the whole cost.
    }
}
