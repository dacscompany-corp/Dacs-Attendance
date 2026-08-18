package com.dacs.attendance.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dacs.attendance.ui.theme.BodyFamily
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green

/**
 * The one action on a screen. English shouted, Tagalog underneath.
 *
 * [Dimens.ActionHeight] is 76dp and that is not negotiable -- see
 * Dimens.kt. The colour is a parameter because Time Out is the same
 * button in brown, and building it twice is how the two halves drift.
 */
@Composable
fun PrimaryActionButton(
    english: String,
    tagalog: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Green,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.ActionHeight),
        shape = RoundedCornerShape(Dimens.RadiusLarge),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = Color.White
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = english,
                    fontFamily = BodyFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
                Text(
                    text = tagalog,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f)
                )
            }
        }
    }
}
