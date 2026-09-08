package com.dacs.attendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Hairline
import com.dacs.attendance.ui.theme.Surface

/**
 * The white card everything on a tab screen sits in.
 *
 * One hairline border and no shadow: v2 separates surfaces by edge, not
 * by elevation, and a drop shadow under every card on an off-white
 * ground turns the screen muddy in daylight -- which is the only light
 * this app is ever read in.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    background: Color = Surface,
    borderColor: Color = BorderDefault,
    borderWidth: Dp = 1.dp,
    radius: Dp = Dimens.RadiusCard,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background, shape)
            .border(borderWidth, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content
    )
}

/** The rule inside a card. Never between cards -- the gap does that. */
@Composable
fun CardDivider(modifier: Modifier = Modifier, color: Color = Hairline) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/**
 * A small filled capsule: "Saved", "Working", "8h 34m", "Active account".
 *
 * Colour is always passed in, never derived here. These labels are how a
 * worker reads status at a glance, and the green/brown/grey meaning
 * belongs to the caller that knows what the status actually is.
 */
@Composable
fun StatusPill(
    text: String,
    foreground: Color,
    background: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .background(background, RoundedCornerShape(Dimens.RadiusPill))
            .padding(horizontal = if (icon == null) 11.dp else 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = foreground, modifier = Modifier.size(16.dp))
        }
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = foreground
        )
    }
}

/**
 * The rounded square an icon sits in at the head of a row -- the project
 * picker, the terms clauses, the "timed in" tick.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.IconTile,
    radius: Dp = Dimens.RadiusTile,
    iconSize: Dp = 20.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(background, RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}
