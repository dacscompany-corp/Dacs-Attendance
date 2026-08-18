package com.dacs.attendance.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dacs.attendance.ui.theme.TextMuted

/**
 * An English label with its Tagalog line underneath. BOTH ARE ALWAYS
 * SHOWN -- there is no toggle.
 *
 * The reason is not decorative. A language switch means a worker can
 * land on a screen in a language they do not read, with no obvious way
 * back. Showing both costs a line of vertical space and removes that
 * failure entirely.
 *
 * This is also why the Tagalog strings live in the default strings.xml
 * and not in values-fil/: they are part of the label, not a translation
 * of it.
 */
@Composable
fun BilingualText(
    english: String,
    tagalog: String,
    modifier: Modifier = Modifier,
    englishColor: Color = MaterialTheme.colorScheme.onSurface,
    tagalogColor: Color = TextMuted,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = horizontalAlignment
    ) {
        Text(
            text = english,
            style = MaterialTheme.typography.bodyLarge,
            color = englishColor
        )
        Text(
            text = tagalog,
            style = MaterialTheme.typography.bodySmall,
            color = tagalogColor
        )
    }
}
