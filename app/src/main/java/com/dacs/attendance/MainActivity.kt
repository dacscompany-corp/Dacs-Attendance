package com.dacs.attendance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dacs.attendance.ui.components.BilingualText
import com.dacs.attendance.ui.theme.AttendanceTheme
import com.dacs.attendance.ui.theme.Dimens

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttendanceTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    ScaffoldCheck(Modifier.padding(inner))
                }
            }
        }
    }
}

/**
 * B1 placeholder. Exists to prove the toolchain, theme and typography
 * compile and render; replaced by LoginScreen in B2.
 */
@Composable
private fun ScaffoldCheck(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Attendance",
            style = MaterialTheme.typography.headlineMedium
        )
        BilingualText(
            english = "Scaffold ready",
            tagalog = "Handa na ang scaffold",
            horizontalAlignment = Alignment.CenterHorizontally
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ScaffoldCheckPreview() {
    AttendanceTheme { ScaffoldCheck() }
}
