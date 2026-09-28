package com.dacs.attendance.ui.update

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dacs.attendance.R
import com.dacs.attendance.domain.UpdateFailure
import com.dacs.attendance.ui.components.PrimaryActionButton
import com.dacs.attendance.ui.theme.Brown
import com.dacs.attendance.ui.theme.BrownLight
import com.dacs.attendance.ui.theme.Danger
import com.dacs.attendance.ui.theme.DangerBorder
import com.dacs.attendance.ui.theme.DangerTint
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.GreenBorder
import com.dacs.attendance.ui.theme.GreenDeep
import com.dacs.attendance.ui.theme.GreenSubtle
import com.dacs.attendance.ui.theme.GreenTint
import com.dacs.attendance.ui.theme.Inert
import com.dacs.attendance.ui.theme.Surface
import com.dacs.attendance.ui.theme.TextMeta
import com.dacs.attendance.ui.theme.TextPrimary
import com.dacs.attendance.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.sin

private val FlameOuter = Color(0xFFF08A24)
private val FlameInner = Color(0xFFFFD166)
private val Nozzle = Color(0xFF4A4A46)

/**
 * The required-update dialog. There is no close button, Back does
 * nothing, and a tap outside does nothing: every published release is
 * required, and the server is already refusing this build's attendance.
 *
 * A Dialog rather than an overlay in the tree because it is its own
 * window: it sits above every screen, the Time In flow's camera
 * included, and nothing underneath can take a touch while it is up.
 */
@Composable
fun UpdateRequiredDialog(
    state: UpdateState.Shown,
    onUpdateNow: () -> Unit,
    onAllowInstalls: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* required: not dismissable */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SheetPadding),
            contentAlignment = Alignment.Center
        ) {
            UpdateCard(state, onUpdateNow, onAllowInstalls, onInstall, onRetry)
        }
    }
}

@Composable
internal fun UpdateCard(
    state: UpdateState.Shown,
    onUpdateNow: () -> Unit,
    onAllowInstalls: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit
) {
    val release = state.release

    Column(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusHero))
            .background(Surface)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The title sits ABOVE the picture, not over it: on a 360dp phone
        // it wraps to two lines, and laid over the canvas that second line
        // landed on the rocket's nose. One gradient behind both keeps it
        // reading as a single header.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(GreenDeep, Green))),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.update_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 26.dp)
            )
            RocketIllustration(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }

        Column(
            modifier = Modifier.padding(
                start = Dimens.SheetPadding,
                end = Dimens.SheetPadding,
                bottom = Dimens.SheetPadding
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
        ) {
            Text(
                text = stringResource(R.string.update_body),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            release.releaseNotes?.let { notes ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GreenSubtle, RoundedCornerShape(Dimens.RadiusField))
                        .border(1.dp, GreenBorder, RoundedCornerShape(Dimens.RadiusField))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.update_whats_new),
                        style = MaterialTheme.typography.labelSmall,
                        color = Green
                    )
                    Text(notes, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
            }

            when (state) {
                is UpdateState.NeedsPermission -> PermissionStep()
                is UpdateState.Failed -> UpdateFailureNotice(state.reason)
                is UpdateState.ReadyToInstall -> Text(
                    text = stringResource(R.string.update_install_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                is UpdateState.Downloading -> LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.ProgressHeight)
                        .clip(RoundedCornerShape(Dimens.RadiusPill)),
                    color = Green,
                    trackColor = Inert,
                    drawStopIndicator = {}
                )
                is UpdateState.Available -> Unit
            }

            when (state) {
                is UpdateState.Available -> PrimaryActionButton(
                    label = stringResource(R.string.update_action_now),
                    onClick = onUpdateNow
                )
                is UpdateState.NeedsPermission -> PrimaryActionButton(
                    label = stringResource(R.string.update_action_allow),
                    onClick = onAllowInstalls
                )
                is UpdateState.Downloading -> PrimaryActionButton(
                    label = stringResource(
                        R.string.update_action_downloading,
                        (state.progress * 100).toInt()
                    ),
                    onClick = {},
                    enabled = false
                )
                is UpdateState.ReadyToInstall -> PrimaryActionButton(
                    label = stringResource(R.string.update_action_install),
                    onClick = onInstall
                )
                is UpdateState.Failed -> PrimaryActionButton(
                    label = stringResource(R.string.update_action_retry),
                    onClick = onRetry
                )
            }

            Text(
                text = stringResource(R.string.update_version, release.versionName),
                style = MaterialTheme.typography.labelSmall,
                color = TextMeta
            )
        }
    }
}

@Composable
private fun PermissionStep() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GreenTint, RoundedCornerShape(Dimens.RadiusField))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = stringResource(R.string.update_permission_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = GreenDeep
        )
        Text(
            text = stringResource(R.string.update_permission_body),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

/** Bilingual, like every other refusal in the app -- see strings.xml. */
@Composable
private fun UpdateFailureNotice(reason: UpdateFailure) {
    val (english, tagalog) = when (reason) {
        UpdateFailure.DownloadFailed ->
            stringResource(R.string.update_error_download) to
                stringResource(R.string.update_error_download_tl)
        UpdateFailure.FileDamaged ->
            stringResource(R.string.update_error_damaged) to
                stringResource(R.string.update_error_damaged_tl)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DangerTint, RoundedCornerShape(Dimens.RadiusField))
            .border(1.dp, DangerBorder, RoundedCornerShape(Dimens.RadiusField))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(english, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Danger)
        Text(tagalog, style = MaterialTheme.typography.bodySmall, color = Danger)
    }
}

// ── The illustration ────────────────────────────────────────────────
//
// Drawn, not a Lottie file: motion without a new dependency or a single
// extra kilobyte of APK, on phones where both matter. One slow phase
// drives the bob, the clouds and the stars; one fast one drives the
// flame. Both stop when the phone's animations are switched off.

/** Star positions as fractions of the header, and a phase so they twinkle out of step. */
private val Stars = listOf(
    Triple(0.10f, 0.42f, 0.0f), Triple(0.22f, 0.66f, 0.3f), Triple(0.84f, 0.40f, 0.6f),
    Triple(0.90f, 0.62f, 0.15f), Triple(0.30f, 0.34f, 0.8f), Triple(0.72f, 0.72f, 0.45f),
    Triple(0.15f, 0.20f, 0.55f), Triple(0.80f, 0.18f, 0.9f)
)

@Composable
private fun RocketIllustration(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }

    val transition = rememberInfiniteTransition(label = "rocket")
    val slow by if (animate) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
            label = "phase"
        )
    } else {
        remember { mutableFloatStateOf(0.25f) }
    }
    val flicker by if (animate) {
        transition.animateFloat(
            initialValue = 0.82f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(140, easing = LinearEasing), RepeatMode.Reverse),
            label = "flame"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    // Transparent: the gradient belongs to the header around it.
    Canvas(modifier) {
        drawStars(slow)

        // Two full bobs per cycle, ±6dp.
        val bob = sin(slow * 4f * PI.toFloat()) * 6.dp.toPx()
        translate(top = bob) { drawRocket(flicker) }

        // Back layer slower and fainter than the front: parallax on the cheap.
        // Baselines set so the rocket RISES OUT of them: body clear, fin
        // tips in the back layer, the flame going down into the front one.
        drawClouds(phase = slow, baseline = size.height - 28.dp.toPx(), scale = 0.8f, alpha = 0.55f, direction = -1f)
        drawClouds(phase = slow * 2f, baseline = size.height - 2.dp.toPx(), scale = 1f, alpha = 1f, direction = 1f)
    }
}

private fun DrawScope.drawStars(phase: Float) {
    Stars.forEach { (fx, fy, offset) ->
        val twinkle = 0.35f + 0.65f * ((sin((phase * 3f + offset) * 2f * PI.toFloat()) + 1f) / 2f)
        drawCircle(
            color = Color.White.copy(alpha = twinkle),
            radius = 2.2.dp.toPx(),
            center = Offset(size.width * fx, size.height * fy)
        )
    }
}

private fun DrawScope.drawRocket(flicker: Float) {
    val w = 58.dp.toPx()
    val h = 104.dp.toPx()
    val cx = size.width / 2f
    val top = 16.dp.toPx()
    val bottom = top + h

    // Flame first, so the nozzle sits over its root.
    val flameLen = 46.dp.toPx() * flicker
    val flameTop = bottom + 4.dp.toPx()
    fun flame(half: Float, length: Float) = Path().apply {
        moveTo(cx - half, flameTop)
        cubicTo(cx - half, flameTop + length * 0.5f, cx, flameTop + length * 0.8f, cx, flameTop + length)
        cubicTo(cx, flameTop + length * 0.8f, cx + half, flameTop + length * 0.5f, cx + half, flameTop)
        close()
    }
    drawPath(flame(w * 0.24f, flameLen), FlameOuter)
    drawPath(flame(w * 0.13f, flameLen * 0.66f), FlameInner)

    // Fins, behind the body.
    fun fin(sign: Float) = Path().apply {
        moveTo(cx + sign * w * 0.36f, top + h * 0.58f)
        lineTo(cx + sign * w * 0.86f, top + h * 0.98f)
        lineTo(cx + sign * w * 0.80f, top + h * 1.10f)
        lineTo(cx + sign * w * 0.30f, top + h * 0.94f)
        close()
    }
    drawPath(fin(-1f), Brown)
    drawPath(fin(1f), Brown)

    // Nozzle.
    drawRect(
        color = Nozzle,
        topLeft = Offset(cx - w * 0.2f, bottom - 2.dp.toPx()),
        size = Size(w * 0.4f, 8.dp.toPx())
    )

    val body = Path().apply {
        moveTo(cx, top)
        cubicTo(cx + w * 0.62f, top + h * 0.24f, cx + w * 0.52f, top + h * 0.74f, cx + w * 0.36f, bottom)
        lineTo(cx - w * 0.36f, bottom)
        cubicTo(cx - w * 0.52f, top + h * 0.74f, cx - w * 0.62f, top + h * 0.24f, cx, top)
        close()
    }
    drawPath(body, Color.White)
    // The nose is the body's own tip, painted -- so it can never drift off the outline.
    clipRect(bottom = top + h * 0.24f) { drawPath(body, BrownLight) }

    // Centre fin, edge-on.
    drawRect(
        color = Brown,
        topLeft = Offset(cx - 2.5.dp.toPx(), top + h * 0.70f),
        size = Size(5.dp.toPx(), h * 0.34f)
    )

    // Porthole.
    val window = Offset(cx, top + h * 0.44f)
    drawCircle(GreenTint, radius = w * 0.19f, center = window)
    drawCircle(Green, radius = w * 0.19f, center = window, style = Stroke(width = 4.dp.toPx()))
    drawCircle(Color.White.copy(alpha = 0.8f), radius = w * 0.05f, center = window + Offset(-w * 0.06f, -w * 0.06f))
}

/**
 * A row of puffs that scrolls sideways and wraps. [phase] is 0..1 per
 * pass, so the row moves exactly one period and the seam never shows.
 */
private fun DrawScope.drawClouds(phase: Float, baseline: Float, scale: Float, alpha: Float, direction: Float) {
    val period = 150.dp.toPx() * scale
    val shift = ((phase % 1f) * period) * direction
    val color = Color.White.copy(alpha = alpha)
    val puffs = listOf(0f to 34f, 0.28f to 46f, 0.58f to 38f, 0.82f to 50f)

    var start = -period + shift % period
    while (start < size.width + period) {
        puffs.forEach { (fx, r) ->
            drawCircle(color, radius = r.dp.toPx() * scale, center = Offset(start + fx * period, baseline))
        }
        start += period
    }
    drawRect(color, topLeft = Offset(0f, baseline), size = Size(size.width, size.height - baseline))
}
