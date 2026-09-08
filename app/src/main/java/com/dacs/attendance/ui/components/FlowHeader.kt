package com.dacs.attendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dacs.attendance.R
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Hairline
import com.dacs.attendance.ui.theme.TextMeta
import com.dacs.attendance.ui.theme.TextMuted
import com.dacs.attendance.ui.theme.TextSecondary

/**
 * The top of every flow screen: back, where you are, and how far in.
 *
 * v2 draws it two ways and this is both. Step 1 carries the TIME IN /
 * TIME OUT chip -- the direction is the only thing that matters before a
 * project is picked -- and puts its question underneath at full size.
 * Steps 2-4 already know the direction, so the question moves up beside
 * the back button and the accent survives only in the progress bar.
 *
 * The counter reads "1/4", not "STEP 1 / 4": it is reference information,
 * and at 12sp mono in the corner it is read as a position rather than as
 * a sentence.
 */
@Composable
fun FlowHeader(
    step: Int,
    total: Int,
    accent: Color,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    modeLabel: String? = null,
    modeIcon: ImageVector? = null,
    inlineTitle: String? = null,
    inlineSubtitle: String? = null,
    /** The camera step, which is white-on-near-black. */
    dark: Boolean = false
) {
    val backTint = if (dark) Color.White else TextSecondary
    val backBorder = if (dark) Color.White.copy(alpha = 0.25f) else BorderDefault
    val counterTint = if (dark) Color.White.copy(alpha = 0.6f) else TextMeta

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.IconButton)
                    .border(1.dp, backBorder, RoundedCornerShape(Dimens.RadiusSmall))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = backTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            when {
                modeLabel != null -> Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    modeIcon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = modeLabel,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        letterSpacing = 0.06.em,
                        color = accent
                    )
                }

                else -> Column(modifier = Modifier.weight(1f)) {
                    inlineTitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    inlineSubtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dark) Color.White.copy(alpha = 0.6f) else TextMuted
                        )
                    }
                }
            }

            Text(
                text = "$step/$total",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.sp,
                color = counterTint
            )
        }

        StepProgressBar(
            step = step,
            total = total,
            // White on the camera step: the accent would disappear into
            // the near-black behind it, and the bar is the only thing on
            // that screen still saying how far in the worker is.
            fill = if (dark) Color.White else accent,
            track = if (dark) Color.White.copy(alpha = 0.2f) else Hairline
        )
    }
}

/**
 * One continuous bar, filled to step/total.
 *
 * v1 drew four separate segments. v2 draws a single track, which is what
 * lets the same component carry 25% and 100% without the gaps between
 * segments reading as unfinished work at the end.
 */
@Composable
fun StepProgressBar(
    step: Int,
    total: Int,
    fill: Color,
    modifier: Modifier = Modifier,
    track: Color = Hairline
) {
    val shape = RoundedCornerShape(3.dp)
    val fraction = (step.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.ProgressHeight)
            .background(track, shape)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(fill, shape)
        )
    }
}

/**
 * A flow step's question, at full size under the bar. Step 1 only --
 * every later step has already asked one and shows its question inline.
 */
@Composable
fun FlowTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted
        )
    }
}

/**
 * The mono strap above a group: "THIS WEEK", "TAP ONE", "ACTIVE PROJECTS".
 *
 * Mono and letter-spaced so it reads as a divider with a name on it
 * rather than as a heading competing with the content underneath.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextMeta
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
    )
}
