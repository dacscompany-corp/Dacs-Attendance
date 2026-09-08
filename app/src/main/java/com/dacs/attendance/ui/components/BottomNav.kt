package com.dacs.attendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dacs.attendance.R
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.Surface
import com.dacs.attendance.ui.theme.TextDisabled
import com.dacs.attendance.ui.theme.TextMeta

/** The three places a signed-in worker can be. */
enum class WorkerTab { HOME, HISTORY, PROFILE }

/**
 * The bottom bar from the design.
 *
 * Three destinations and no more. Every extra tab is a thing a worker
 * has to rule out while standing in the sun deciding where to tap, and
 * the app has exactly one job.
 *
 * v2 dropped v1's tinted pill behind the active tab: colour and weight
 * on the icon and label already carry it, and the pill made a bar with
 * three items look like a bar with one selected control.
 */
@Composable
fun WorkerBottomNav(
    selected: WorkerTab,
    onSelect: (WorkerTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().background(Surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderDefault))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp)
        ) {
            Tab(
                tab = WorkerTab.HOME,
                selected = selected,
                icon = Icons.Filled.Home,
                labelRes = R.string.nav_home,
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )
            Tab(
                tab = WorkerTab.HISTORY,
                selected = selected,
                icon = Icons.Filled.History,
                labelRes = R.string.nav_history,
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )
            Tab(
                tab = WorkerTab.PROFILE,
                selected = selected,
                // The only tab whose glyph changes: outline when you are
                // elsewhere, filled when you are looking at yourself.
                icon = Icons.Filled.Person,
                inactiveIcon = Icons.Outlined.PersonOutline,
                labelRes = R.string.nav_profile,
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun Tab(
    tab: WorkerTab,
    selected: WorkerTab,
    icon: ImageVector,
    labelRes: Int,
    onSelect: (WorkerTab) -> Unit,
    modifier: Modifier = Modifier,
    inactiveIcon: ImageVector = icon
) {
    val active = tab == selected
    val label = stringResource(labelRes)

    Column(
        modifier = modifier
            .clickable { onSelect(tab) }
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = if (active) icon else inactiveIcon,
            contentDescription = label,
            tint = if (active) Green else TextDisabled,
            modifier = Modifier.size(23.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) Green else TextMeta
        )
    }
}
