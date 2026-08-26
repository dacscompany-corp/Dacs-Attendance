package com.dacs.attendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dacs.attendance.R
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.GreenTint
import com.dacs.attendance.ui.theme.Hairline
import com.dacs.attendance.ui.theme.TextMuted

/** The three places a signed-in worker can be. */
enum class WorkerTab { HOME, HISTORY, PROFILE }

/**
 * The bottom bar from the design.
 *
 * Three destinations and no more. Every extra tab is a thing a worker
 * has to rule out while standing in the sun deciding where to tap, and
 * the app has exactly one job.
 */
@Composable
fun WorkerBottomNav(
    selected: WorkerTab,
    onSelect: (WorkerTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(1.dp).background(Hairline)) {}
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Tab(WorkerTab.HOME, selected, Icons.Filled.Home, R.string.nav_home, onSelect)
            Tab(WorkerTab.HISTORY, selected, Icons.Filled.History, R.string.nav_history, onSelect)
            Tab(WorkerTab.PROFILE, selected, Icons.Filled.Person, R.string.nav_profile, onSelect)
        }
    }
}

@Composable
private fun Tab(
    tab: WorkerTab,
    selected: WorkerTab,
    icon: ImageVector,
    labelRes: Int,
    onSelect: (WorkerTab) -> Unit
) {
    val active = tab == selected
    val label = stringResource(labelRes)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(
                color = if (active) GreenTint else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onSelect(tab) }
            // Generous, like every other target in this app: gloved hands.
            .padding(horizontal = 22.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) Green else TextMuted,
            modifier = Modifier.size(23.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) Green else TextMuted
        )
    }
}
