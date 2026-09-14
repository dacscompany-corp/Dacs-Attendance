package com.dacs.attendance.widget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dacs.attendance.MainActivity
import com.dacs.attendance.R
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.WidgetState
import com.dacs.attendance.ui.EXTRA_START_FLOW
import com.dacs.attendance.ui.theme.BrownDeep
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.GreenPressed
import dagger.hilt.android.EntryPointAccessors
import java.time.Instant
import java.util.Date
import java.util.TimeZone
import kotlin.math.min

/**
 * Bumped by [GlanceWidgetRefresher]. A widget whose session is still
 * running does not re-run [DacsTimeWidget.provideGlance] on update, only
 * recomposes -- so the re-read is keyed on this, or it would never happen.
 */
internal val RefreshTick = longPreferencesKey("refresh_tick")

/** Below this the card has no room for a title block and drops to one row. */
private val FullCardMinHeight = 100.dp

/**
 * Below this the subtitle is dropped so the title can have its height.
 *
 * A short card cannot carry all five rows at a legible size. The subtitle
 * is the one that goes: "Tap to start your day" repeats what the title and
 * the arrow already say, while the pill and the breadcrumb carry facts
 * nothing else on the card does.
 */
private val SubtitleMinHeight = 138.dp

/**
 * The home-screen widget: one solid card, tappable anywhere, that opens
 * the same four-step flow Home would. It records nothing itself.
 */
class DacsTimeWidget : GlanceAppWidget() {

    // Exact, not Responsive: Responsive reports the breakpoint it picked
    // rather than the cell's real size, so type scaled from it would jump
    // between two fixed sizes and look wrong at everything in between.
    override val sizeMode = SizeMode.Exact

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
    val card = widgetCardFor(state)
    val tap = card.action
        ?.let { actionStartActivity(startFlowIntent(context, it)) }
        ?: actionStartActivity(openAppIntent(context))

    val size = LocalSize.current
    if (size.height < FullCardMinHeight) {
        CompactCard(context, card, tap)
    } else {
        FullCard(context, card, tap, typeScale(size), size.height >= SubtitleMinHeight)
    }
}

/** The card's type sizes, in sp, for a cell of this size. */
private data class CardType(
    val title: TextUnit,
    val subtitle: TextUnit,
    val pill: TextUnit,
    val footer: TextUnit,
    val arrow: TextUnit,
    /** The gap under the pill row, tightened on a short cell to buy type. */
    val gap: Dp
)

/**
 * Type scaled to the cell, so a card the worker has dragged twice as tall
 * reads twice as boldly instead of keeping a fixed title adrift in colour.
 *
 * Bounded by width as well as height: Glance text does not shrink to fit,
 * so the longest title the card can show has to survive on one line.
 */
private fun typeScale(size: DpSize): CardType {
    val height = size.height.value
    // Above the design's own 0.18: a small widget has to stay legible at
    // arm's length in sunlight, so type grows faster than the card does.
    // The cap keeps a tall card from turning into a billboard.
    val byHeight = if (height < SubtitleMinHeight.value) height * 0.30f else height * 0.24f
    // Roughly six title characters' worth of headroom inside the padding.
    val byWidth = (size.width.value - 32f) / 6f
    val title = min(byHeight, byWidth).coerceIn(18f, 44f)

    return CardType(
        title = title.sp,
        subtitle = (title * 0.52f).coerceIn(12f, 19f).sp,
        pill = (title * 0.42f).coerceIn(10f, 14f).sp,
        footer = (title * 0.46f).coerceIn(11f, 16f).sp,
        arrow = (title * 0.60f).coerceIn(15f, 26f).sp,
        gap = (height * 0.07f).coerceIn(6f, 14f).dp
    )
}

@Composable
private fun FullCard(
    context: Context,
    card: WidgetCard,
    tap: Action,
    type: CardType,
    showSubtitle: Boolean
) {
    // The footer is the flow breadcrumb, unless a stamp is still waiting
    // on the phone -- then it says so, and the action stays reachable.
    val footer = when {
        card.queued -> context.getString(R.string.widget_pill_queued)
        card.breadcrumb != null -> context.getString(card.breadcrumb)
        else -> null
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.tone(card.tone))
            .cornerRadius(20.dp)
            .clickable(tap)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        // The home screen hands out whatever height the cell has, which is
        // rarely the card's natural one. With a footer the slack goes
        // between the subtitle and the rule, pinning the breadcrumb to the
        // bottom edge as drawn; without one there is nothing to pin, so the
        // block centres rather than stranding empty colour underneath it.
        verticalAlignment = if (footer == null) Alignment.Vertical.CenterVertically
        else Alignment.Vertical.Top
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Pill(context, card.pill, type.pill)
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = if (card.done) "✓" else "→",
                style = TextStyle(color = WidgetColors.onCard, fontSize = type.arrow)
            )
        }

        Spacer(GlanceModifier.height(type.gap))
        Text(
            text = context.getString(card.title),
            style = TextStyle(
                color = WidgetColors.onCard,
                fontSize = type.title,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        if (showSubtitle) {
            Text(
                text = subtitleText(context, card.subtitle),
                style = TextStyle(color = WidgetColors.onCardMuted, fontSize = type.subtitle),
                maxLines = 2
            )
        }

        if (footer != null) {
            // Fixed gap first so the rule never touches the subtitle on a
            // cell with no slack; the weighted one then eats whatever is left.
            Spacer(GlanceModifier.height(type.gap))
            Spacer(GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(WidgetColors.rule))
            Spacer(GlanceModifier.height(9.dp))
            Text(
                text = footer,
                style = TextStyle(
                    color = if (card.queued) WidgetColors.queued else WidgetColors.onCardMuted,
                    fontSize = type.footer,
                    fontWeight = if (card.queued) FontWeight.Bold else FontWeight.Normal
                ),
                maxLines = 1
            )
        }
    }
}

/**
 * The same card with only its top line. The elapsed count takes the
 * second line where there is one, because at this size "how long have I
 * been here" is the only thing the subtitle was going to add.
 */
@Composable
private fun CompactCard(context: Context, card: WidgetCard, tap: Action) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.tone(card.tone))
            .cornerRadius(16.dp)
            .clickable(tap)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = context.getString(card.title),
                style = TextStyle(
                    color = WidgetColors.onCard,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            when {
                card.queued -> Text(
                    text = context.getString(R.string.widget_pill_queued),
                    style = TextStyle(
                        color = WidgetColors.queued,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )

                card.pill is WidgetPill.Elapsed -> Pill(context, card.pill, 11.sp)

                else -> Text(
                    text = subtitleText(context, card.subtitle),
                    style = TextStyle(color = WidgetColors.onCardMuted, fontSize = 11.sp),
                    maxLines = 1
                )
            }
        }
        Text(
            text = if (card.done) "✓" else "→",
            style = TextStyle(color = WidgetColors.onCard, fontSize = 15.sp)
        )
    }
}

@Composable
private fun Pill(context: Context, pill: WidgetPill, fontSize: TextUnit) {
    if (pill is WidgetPill.None) return

    Box(
        modifier = GlanceModifier
            .background(WidgetColors.pill)
            .cornerRadius(12.dp)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        when (pill) {
            is WidgetPill.Label -> Text(
                text = context.getString(pill.text),
                style = TextStyle(
                    color = WidgetColors.onCard,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )

            is WidgetPill.Elapsed ->
                AndroidRemoteViews(elapsedViews(context, pill.since, fontSize.value))

            WidgetPill.None -> Unit
        }
    }
}

/**
 * A Chronometer counting up from [since].
 *
 * Chronometer measures against [SystemClock.elapsedRealtime], which is
 * uptime, while a Time In is a wall-clock instant -- so the base is this
 * moment's elapsed-realtime minus however long ago the stamp was.
 */
private fun elapsedViews(context: Context, since: Instant, fontSp: Float): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_elapsed).apply {
        val agoMillis = System.currentTimeMillis() - since.toEpochMilli()
        setChronometer(
            R.id.widget_elapsed,
            SystemClock.elapsedRealtime() - agoMillis,
            context.getString(R.string.widget_pill_on_site) + " %s",
            true
        )
        // The XML size is only a default; the pill scales with the cell
        // like every other line on the card.
        setTextViewTextSize(R.id.widget_elapsed, TypedValue.COMPLEX_UNIT_SP, fontSp)
    }

private fun subtitleText(context: Context, subtitle: WidgetSubtitle): String = when (subtitle) {
    is WidgetSubtitle.Label -> context.getString(subtitle.text)
    is WidgetSubtitle.DaySpan -> context.getString(
        R.string.widget_sub_day_span,
        clock(context, subtitle.timeIn),
        clock(context, subtitle.timeOut)
    )
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

/**
 * The card's palette.
 *
 * Deliberately identical day and night: the card is a solid colour that
 * has to hold its meaning on any wallpaper, and a "dark variant" of green
 * would only make one state look like two.
 */
private object WidgetColors {
    /** The darkest green, kept for a day that is closed and correct. */
    private val Complete = Color(0xFF0F3A24)

    /** Grey-green: the widget cannot say, and is not pretending to. */
    private val Neutral = Color(0xFF3A4A42)

    /** One colour, both themes. */
    private fun solid(color: Color) = ColorProvider(day = color, night = color)

    val onCard = solid(Color.White)
    val onCardMuted = solid(Color(0xD1FFFFFF))
    val rule = solid(Color(0x38FFFFFF))
    val pill = solid(Color(0x2BFFFFFF))

    /** Light enough to read as gold on a dark card, not as the brown tone. */
    val queued = solid(Color(0xFFE9CE9A))

    fun tone(tone: WidgetTone) = solid(
        when (tone) {
            WidgetTone.START -> Green
            WidgetTone.WORKING -> GreenPressed
            WidgetTone.COMPLETE -> Complete
            WidgetTone.ATTENTION -> BrownDeep
            WidgetTone.NEUTRAL -> Neutral
        }
    )
}
