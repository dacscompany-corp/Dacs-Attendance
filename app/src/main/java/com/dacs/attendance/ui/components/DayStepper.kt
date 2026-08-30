package com.dacs.attendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Brown
import com.dacs.attendance.ui.theme.Canvas
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.GreenTint
import com.dacs.attendance.ui.theme.Hairline
import com.dacs.attendance.ui.theme.MonoFamily
import com.dacs.attendance.ui.theme.TextDisabled
import com.dacs.attendance.ui.theme.TextMuted

/** One of the three states in the day's 1-2-3 stepper. */
enum class StepState { Done, Now, Locked }

/** The badge diameter, and therefore where the rail behind them sits. */
private val BadgeSize = 40.dp
private val RailInset = 19.dp

/**
 * The day at a glance: Time In -> Working -> Time Out.
 *
 * Three columns with a rail running behind the badges, as the design
 * draws it. The rail spans badge-centre to badge-centre -- weights of
 * 1:4:1, because each column is a third and its centre sits at a sixth.
 *
 * Colour does the work here, as everywhere in this app: green means
 * done or do-it-now, grey means locked. A worker reads the colour before
 * they read the label, so these must not be restyled into one palette.
 */
@Composable
fun DayStepper(
    timeIn: StepState,
    working: StepState,
    timeOut: StepState,
    timeInCaption: String,
    workingCaption: String,
    timeOutCaption: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        // Behind the badges, level with their centres.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = RailInset)
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .weight(4f)
                    .height(3.dp)
                    .background(Hairline)
            )
            Spacer(Modifier.weight(1f))
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Step(1, "Time In", timeInCaption, timeIn, Green, Modifier.weight(1f))
            Step(2, "Working", workingCaption, working, Green, Modifier.weight(1f))
            // Only Time Out wears the padlock, as the design draws it. A
            // lock on "Working" would say the worker is barred from
            // working, when all it means is they have not timed in yet.
            Step(3, "Time Out", timeOutCaption, timeOut, Brown, Modifier.weight(1f), locks = true)
        }
    }
}

@Composable
private fun Step(
    number: Int,
    label: String,
    caption: String,
    state: StepState,
    activeColour: Color,
    modifier: Modifier = Modifier,
    /** Whether THIS step shows a padlock while it is not yet available. */
    locks: Boolean = false
) {
    val locked = state == StepState.Locked
    val colour = if (locked) TextDisabled else activeColour

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(BadgeSize)
                .then(
                    if (locked) {
                        Modifier
                            .background(Canvas, CircleShape)
                            .border(2.dp, BorderDefault, CircleShape)
                    } else {
                        // The tinted halo the design puts around the live
                        // step. Drawn OUTSIDE the badge's bounds so the
                        // three columns still line up on the rail.
                        Modifier
                            .drawBehind {
                                drawCircle(
                                    color = GreenTint,
                                    radius = size.minDimension / 2f + 5.dp.toPx()
                                )
                            }
                            .background(colour, CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (locked && locks) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = TextDisabled,
                    modifier = Modifier.size(17.dp)
                )
            } else {
                Text(
                    text = number.toString(),
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = if (locked) TextDisabled else Color.White
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.5.sp,
                lineHeight = 16.sp,
                color = colour,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Text(
                text = caption,
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
                // The live step's caption is a lighter tint of its own
                // colour; a locked one is simply grey.
                color = if (locked) TextDisabled else colour.copy(alpha = 0.8f),
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** "Step 2 of 4" across the top of the flow screens. */
@Composable
fun StepProgressBar(
    step: Int,
    total: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)
    ) {
        Text(
            text = "STEP $step OF $total",
            fontFamily = MonoFamily,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(total) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(
                            color = if (index < step) accent else Hairline,
                            shape = RoundedCornerShape(3.dp)
                        )
                )
            }
        }
    }
}
