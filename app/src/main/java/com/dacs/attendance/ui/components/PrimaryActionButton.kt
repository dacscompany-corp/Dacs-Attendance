package com.dacs.attendance.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.Inert
import com.dacs.attendance.ui.theme.Surface
import com.dacs.attendance.ui.theme.TextDisabled
import com.dacs.attendance.ui.theme.TextSecondary

/**
 * The one action on a screen. One shouted line, and an arrow when there
 * is somewhere to go next.
 *
 * v2 dropped the Tagalog second line and with it the 76dp slab: this is
 * 58dp, which is still half again Material's minimum and still a target
 * a gloved hand hits without aiming. The colour is a parameter because
 * Time Out is the same button in brown, and building it twice is how the
 * two halves drift.
 *
 * DISABLED IS NOT GREYED-OUT ACCENT. The design fills it flat [Inert]
 * with [TextDisabled] ink -- the absence of the accent is what says "not
 * yet", and a translucent green would still read as a green button.
 */
@Composable
fun PrimaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Green,
    /** Ink on the button. Inverted on the confirmation, which is accent on white. */
    content: Color = Color.White,
    /** Sits after the label -- the design's arrow_forward on SIGN IN and SUBMIT. */
    trailingIcon: ImageVector? = null,
    /** Sits before it -- the tick on YES, USE THIS PHOTO. */
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = Dimens.ActionHeight
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = height),
        shape = RoundedCornerShape(Dimens.RadiusButton),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = Inert,
            disabledContentColor = TextDisabled
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 6.dp,
            pressedElevation = 2.dp,
            disabledElevation = 0.dp
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = content,
                strokeWidth = 3.dp
            )
            return@Button
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            leadingIcon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(9.dp))
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            trailingIcon?.let {
                Spacer(Modifier.width(9.dp))
                Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * The second of a pair -- "Retake photo" under "YES, USE THIS PHOTO".
 *
 * Outlined and sentence case, so the two never read as equal choices.
 * Retake is what you tap when the first answer is no, and the design
 * shows it as the quieter of the two on purpose.
 */
@Composable
fun SecondaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = Dimens.SecondaryHeight
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = height),
        shape = RoundedCornerShape(Dimens.RadiusButton),
        border = BorderStroke(1.5.dp, BorderDefault),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Surface,
            contentColor = TextSecondary
        )
    ) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}
