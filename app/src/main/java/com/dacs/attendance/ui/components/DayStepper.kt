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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
 * A VERTICAL list, as the design draws it, not three columns across.
 * Reading top to bottom is reading the day in the order it happens, and
 * it leaves each step room for a full label instead of a wrapped one.
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
    Column(modifier = modifier.fillMaxWidth()) {
        Step(1, "Time In", timeInCaption, timeIn, Green, railBelow = true)
        Step(2, "Working", workingCaption, working, Green, railBelow = true)
        // Only Time Out wears the padlock, as the design draws it. A lock
        // on "Working" would say the worker is barred from working, when
        // all it means is that they have not timed in yet.
        Step(3, "Time Out", timeOutCaption, timeOut, Brown, railBelow = false, locks = true)
    }
}

/** The line joining one badge to the next. */
private val RailHeight = 20.dp

@Composable
private fun Step(
    number: Int,
    label: String,
    caption: String,
    state: StepState,
    activeColour: Color,
    railBelow: Boolean,
    modifier: Modifier = Modifier,
    /** Whether THIS step shows a padlock when it is not yet available. */
    locks: Boolean = false
) {
    val locked = state == StepState.Locked
    val colour = if (locked) TextDisabled else activeColour

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(if (locked) Hairline else colour, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (locked && locks) {
                    // A padlock rather than a grey "3". The number says
                    // "third"; the lock says "you cannot do this yet",
                    // which is the thing the worker needs to know.
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = TextDisabled,
                        modifier = Modifier.size(15.dp)
                    )
                } else {
                    Text(
                        text = number.toString(),
                        fontFamily = MonoFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (locked) TextDisabled else Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (railBelow) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(RailHeight)
                        .background(if (state == StepState.Done) colour else Hairline)
                )
            }
        }

        Column(
            modifier = Modifier
                .padding(start = 14.dp)
                // Aligns the label with the badge beside it rather than
                // with the top of the row.
                .padding(top = 3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (locked) TextDisabled else colour
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
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
