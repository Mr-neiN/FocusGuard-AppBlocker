package com.focusguard.blocker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.focusguard.blocker.ui.GeneralAppsScreen
import com.focusguard.blocker.ui.HomeScreen
import com.focusguard.blocker.ui.SchedulesScreen
import kotlinx.coroutines.CoroutineScope

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember(context) { BlockRepo(context.applicationContext) }
    val config by repo.configFlow.collectAsState(initial = BlockConfig())
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("FocusGuard") }) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        label = { Text("Home") },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        label = { Text("Schedules") },
                        icon = { Icon(Icons.Filled.DateRange, contentDescription = null) }
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        label = { Text("Apps") },
                        icon = { Icon(Icons.Filled.Apps, contentDescription = null) }
                    )
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (tab) {
                    0 -> HomeScreen(
                        config = config,
                        repo = repo,
                        scope = scope,
                        onOpenApps = { tab = 2 },
                        onOpenSchedules = { tab = 1 }
                    )
                    1 -> SchedulesScreen(config = config, repo = repo, scope = scope)
                    else -> GeneralAppsScreen(config = config, repo = repo, scope = scope)
                }
            }
        )
    }
}
