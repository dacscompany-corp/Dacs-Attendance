package com.dacs.attendance.widget

import androidx.annotation.StringRes
import com.dacs.attendance.R
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.WidgetState
import java.time.Instant

/**
 * The card the widget draws, decided from [WidgetState] alone.
 *
 * Pure on purpose: no Context, no resolved strings, no Glance types, so
 * every phrase and colour the home screen can show is settled by a plain
 * JUnit test rather than by reading a screenshot.
 */

/** The card's colour, carrying the app's usual meaning. */
enum class WidgetTone {
    /** Green. Nothing recorded yet -- the day is ahead of you. */
    START,

    /** Deep green. On site, the stamp accepted. */
    WORKING,

    /** Deepest green. The day is closed and correct. */
    COMPLETE,

    /** Brown. Something needs a person: a day closed without a Time Out. */
    ATTENTION,

    /** Grey-green. The widget cannot say, and will not guess. */
    NEUTRAL
}

/** The line above the title. */
sealed interface WidgetPill {
    /** No pill, when there is nothing useful to promise. */
    data object None : WidgetPill

    data class Label(@StringRes val text: Int) : WidgetPill

    /**
     * A live count since [since]. Drawn by a system Chronometer, which is
     * why this carries the instant rather than a formatted string.
     */
    data class Elapsed(val since: Instant) : WidgetPill
}

/** The line under the title. */
sealed interface WidgetSubtitle {
    data class Label(@StringRes val text: Int) : WidgetSubtitle

    /** Today's two stamps, formatted against the phone's clock style. */
    data class DaySpan(val timeIn: Instant, val timeOut: Instant) : WidgetSubtitle
}

data class WidgetCard(
    val tone: WidgetTone,
    val pill: WidgetPill,
    @StringRes val title: Int,
    val subtitle: WidgetSubtitle,
    /** The flow's step list, or null where tapping starts no flow. */
    @StringRes val breadcrumb: Int?,
    /** A stamp is still on the phone; the breadcrumb row says so instead. */
    val queued: Boolean,
    /** Draw a tick rather than an arrow. */
    val done: Boolean,
    /** What the tap starts. Null opens the app instead. */
    val action: TimeDirection?
)

/**
 * The card for [state].
 *
 * Every branch is total: a state with no card is a state the home screen
 * would draw blank, so [WidgetState.Unknown] is the only "we don't know"
 * and it says so out loud.
 */
fun widgetCardFor(state: WidgetState): WidgetCard = when (state) {

    WidgetState.SignedOut -> WidgetCard(
        tone = WidgetTone.START,
        pill = WidgetPill.Label(R.string.widget_pill_sign_in),
        title = R.string.widget_card_sign_in,
        subtitle = WidgetSubtitle.Label(R.string.widget_sub_sign_in),
        breadcrumb = null,
        queued = false,
        done = false,
        action = null
    )

    is WidgetState.NotTimedIn -> WidgetCard(
        tone = WidgetTone.START,
        pill = WidgetPill.Label(R.string.widget_pill_step_one),
        title = R.string.widget_card_time_in,
        subtitle = WidgetSubtitle.Label(R.string.widget_sub_start_day),
        breadcrumb = R.string.widget_flow_in,
        queued = state.notSentYet,
        done = false,
        action = TimeDirection.IN
    )

    is WidgetState.Working -> WidgetCard(
        tone = WidgetTone.WORKING,
        // Without a recorded time in there is nothing to count from, and
        // counting from now would read 00:00:00 in the afternoon.
        pill = state.timeInAt
            ?.let { WidgetPill.Elapsed(it) }
            ?: WidgetPill.Label(R.string.widget_pill_on_site),
        title = R.string.widget_card_time_out,
        subtitle = WidgetSubtitle.Label(R.string.widget_sub_close_day),
        // Three steps out, not four: the project is already known.
        breadcrumb = R.string.widget_flow_out,
        queued = state.notSentYet,
        done = false,
        action = TimeDirection.OUT
    )

    is WidgetState.Complete -> WidgetCard(
        tone = WidgetTone.COMPLETE,
        pill = WidgetPill.Label(R.string.widget_pill_all_done),
        title = R.string.widget_card_complete,
        subtitle = if (state.timeInAt != null && state.timeOutAt != null) {
            WidgetSubtitle.DaySpan(state.timeInAt, state.timeOutAt)
        } else {
            WidgetSubtitle.Label(R.string.widget_done_no_times)
        },
        breadcrumb = null,
        queued = state.notSentYet,
        done = true,
        action = null
    )

    is WidgetState.Abandoned -> WidgetCard(
        tone = WidgetTone.ATTENTION,
        pill = WidgetPill.Label(R.string.widget_pill_no_time_out),
        title = R.string.widget_card_day_closed,
        subtitle = WidgetSubtitle.Label(R.string.widget_sub_sort_out),
        breadcrumb = null,
        queued = state.notSentYet,
        // Not done -- the day is closed, but not correctly.
        done = false,
        action = null
    )

    is WidgetState.Unknown -> WidgetCard(
        tone = WidgetTone.NEUTRAL,
        pill = WidgetPill.None,
        title = R.string.widget_card_open_app,
        subtitle = WidgetSubtitle.Label(R.string.widget_sub_unavailable),
        breadcrumb = null,
        queued = state.notSentYet,
        done = false,
        action = null
    )
}
