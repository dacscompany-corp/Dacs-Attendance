package com.dacs.attendance.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dacs.attendance.MainActivity
import com.dacs.attendance.R
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.WidgetState
import com.dacs.attendance.ui.EXTRA_START_FLOW
import com.dacs.attendance.ui.theme.Brown
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.Surface
import com.dacs.attendance.ui.theme.TextDisabled
import com.dacs.attendance.ui.theme.TextMuted
import com.dacs.attendance.ui.theme.TextPrimary
import dagger.hilt.android.EntryPointAccessors
import java.time.Instant
import java.util.Date
import java.util.TimeZone

/**
 * Bumped by [GlanceWidgetRefresher]. A widget whose session is still
 * running does not re-run [DacsTimeWidget.provideGlance] on update, only
 * recomposes -- so the re-read is keyed on this, or it would never happen.
 */
internal val RefreshTick = longPreferencesKey("refresh_tick")

/**
 * The home-screen widget: today's status, and the one button Home would
 * show. It opens the same four-step flow; it records nothing itself.
 */
class DacsTimeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val loader = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .widgetStateLoader()
        // Read before the first frame, so the widget never flashes an
        // empty state that could be mistaken for "Not timed in".
        val initial = loader.load()

        provideContent {
            val tick = currentState(RefreshTick) ?: 0L
            val state by produceState(initial, tick) { value = loader.load() }
            WidgetContent(context, state)
        }
    }
}

@Composable
private fun WidgetContent(context: Context, state: WidgetState) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.surface)
            .cornerRadius(16.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionStartActivity(openAppIntent(context))),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = statusLine(context, state),
                style = TextStyle(
                    color = WidgetColors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 2
            )
            if (state.notSentYet) {
                Text(
                    text = context.getString(R.string.widget_not_sent),
                    style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp)
                )
            }
        }

        val action = state.action
        when {
            action != null -> {
                Spacer(GlanceModifier.width(10.dp))
                Button(
                    text = context.getString(
                        if (action == TimeDirection.IN) R.string.action_time_in else R.string.action_time_out
                    ),
                    onClick = actionStartActivity(startFlowIntent(context, action)),
                    // Green arrives, brown leaves -- the same meaning the
                    // colours carry everywhere else in the app.
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (action == TimeDirection.IN) WidgetColors.timeIn else WidgetColors.timeOut,
                        contentColor = WidgetColors.onAction
                    )
                )
            }

            state is WidgetState.SignedOut -> {
                Spacer(GlanceModifier.width(10.dp))
                Button(
                    text = context.getString(R.string.widget_action_open),
                    onClick = actionStartActivity(openAppIntent(context)),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = WidgetColors.timeIn,
                        contentColor = WidgetColors.onAction
                    )
                )
            }
        }
    }
}

private fun statusLine(context: Context, state: WidgetState): String = when (state) {
    WidgetState.SignedOut -> context.getString(R.string.widget_signed_out)
    is WidgetState.NotTimedIn -> context.getString(R.string.widget_not_timed_in)
    is WidgetState.Working ->
        state.timeInAt?.let { context.getString(R.string.widget_timed_in, clock(context, it)) }
            ?: context.getString(R.string.widget_timed_in_no_time)
    is WidgetState.Complete ->
        if (state.timeInAt != null && state.timeOutAt != null) {
            context.getString(
                R.string.widget_done,
                clock(context, state.timeInAt),
                clock(context, state.timeOutAt)
            )
        } else {
            context.getString(R.string.widget_done_no_times)
        }
    is WidgetState.Abandoned -> context.getString(R.string.widget_abandoned)
    is WidgetState.Unknown -> context.getString(R.string.widget_unknown)
}

/**
 * Manila time, in the phone's own 12- or 24-hour style. Manila because
 * the work day is Manila's whatever zone the phone is set to -- the same
 * rule as WorkDate.
 */
private fun clock(context: Context, instant: Instant): String {
    val format = DateFormat.getTimeFormat(context)
    format.timeZone = TimeZone.getTimeZone("Asia/Manila")
    return format.format(Date(instant.toEpochMilli()))
}

internal fun openAppIntent(context: Context): Intent = Intent(context, MainActivity::class.java)

/**
 * A distinct ACTION per direction, not just a distinct extra: two intents
 * that differ only in extras are the same PendingIntent to Android, and
 * the Time Out button would be handed the Time In one.
 */
internal fun startFlowIntent(context: Context, direction: TimeDirection): Intent =
    Intent(context, MainActivity::class.java)
        .setAction("com.dacs.attendance.action.START_FLOW_${direction.name}")
        .putExtra(EXTRA_START_FLOW, direction.name)

/** The app's palette, with a dark variant that follows the system theme. */
private object WidgetColors {
    val surface = ColorProvider(day = Surface, night = Color(0xFF1E201E))
    val text = ColorProvider(day = TextPrimary, night = Color(0xFFF0F0EB))
    val muted = ColorProvider(day = TextMuted, night = TextDisabled)
    val timeIn = ColorProvider(day = Green, night = Green)
    val timeOut = ColorProvider(day = Brown, night = Brown)
    val onAction = ColorProvider(day = Color.White, night = Color.White)
}
