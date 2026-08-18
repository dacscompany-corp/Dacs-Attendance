package com.dacs.attendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dacs.attendance.ui.theme.Brown
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.Hairline
import com.dacs.attendance.ui.theme.MonoFamily
import com.dacs.attendance.ui.theme.TextDisabled
import com.dacs.attendance.ui.theme.TextMuted

/** One of the three states in the day's 1-2-3 stepper. */
enum class StepState { Done, Now, Locked }

/**
 * The day at a glance: Time In -> Working -> Time Out.
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
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
        verticalAlignment = Alignment.Top
    ) {
        Step(1, "Time In", timeInCaption, timeIn, Green, Modifier.weight(1f))
        Step(2, "Working", workingCaption, working, Green, Modifier.weight(1f))
        Step(3, "Time Out", timeOutCaption, timeOut, Brown, Modifier.weight(1f))
    }
}

@Composable
private fun Step(
    number: Int,
    label: String,
    caption: String,
    state: StepState,
    activeColour: Color,
    modifier: Modifier = Modifier
) {
    val colour = when (state) {
        StepState.Done, StepState.Now -> activeColour
        StepState.Locked -> TextDisabled
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(
                    color = if (state == StepState.Locked) Hairline else colour,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                fontFamily = MonoFamily,
                fontWeight = FontWeight.Bold,
                color = if (state == StepState.Locked) TextDisabled else Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (state == StepState.Locked) TextDisabled else colour,
            textAlign = TextAlign.Center
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            textAlign = TextAlign.Center
        )
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
