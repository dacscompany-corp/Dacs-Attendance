package com.dacs.attendance

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.dacs.attendance.ui.AttendanceRoot
import com.dacs.attendance.ui.EXTRA_START_FLOW
import com.dacs.attendance.ui.RootViewModel
import com.dacs.attendance.ui.startFlowFromExtra
import com.dacs.attendance.ui.theme.AttendanceTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val root: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a FRESH start. A recreated activity is handed its
        // original intent again -- after a process death, extra and all --
        // and reading it then would reopen a flow the worker already
        // finished or walked away from.
        if (savedInstanceState == null) takeStartFlowRequest(intent)
        setContent {
            AttendanceTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    AttendanceRoot(modifier = Modifier.padding(inner), viewModel = root)
                }
            }
        }
    }

    /** A widget tap while the app is already running (launchMode singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeStartFlowRequest(intent)
    }

    private fun takeStartFlowRequest(intent: Intent?) {
        val direction = startFlowFromExtra(intent?.getStringExtra(EXTRA_START_FLOW)) ?: return
        // Removed as it is read, so nothing that looks at this intent later
        // can act on the same tap twice.
        intent?.removeExtra(EXTRA_START_FLOW)
        root.requestStartFlow(direction)
    }
}
