package com.focusguard.blocker.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.focusguard.blocker.BlockAccessibilityService
import com.focusguard.blocker.BlockConfig
import com.focusguard.blocker.BlockRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    config: BlockConfig,
    repo: BlockRepo,
    scope: CoroutineScope,
    onOpenApps: () -> Unit,
    onOpenSchedules: () -> Unit,
) {
    val context = LocalContext.current
    var serviceRunning by remember {
        mutableStateOf(BlockAccessibilityService.isAccessibilityEnabled(context))
    }
    LaunchedEffect(Unit) {
        while (true) {
            serviceRunning = BlockAccessibilityService.isAccessibilityEnabled(context)
            delay(1500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Master switch
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Blocking enabled", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Master switch – pause or resume every rule at once.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = config.masterEnabled,
                    onCheckedChange = { v -> scope.launch { repo.update { it.copy(masterEnabled = v) } } }
                )
            }
        }

        // General block
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("General block", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (config.generalAllowSelected)
                                "Allowed apps are exempt; other user apps are blocked all day, every day."
                            else
                                "Selected apps are blocked at ALL times – all day, every day.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = config.generalEnabled,
                        onCheckedChange = { v -> scope.launch { repo.update { it.copy(generalEnabled = v) } } }
                    )
                }
                Text(
                    "${config.resolvedGeneralApps().size} app(s) ${if (config.generalAllowSelected) "allowed" else "selected to block"}" +
                        (config.savedAppLists.firstOrNull { it.id == config.generalSavedListId }?.let { " • List: ${it.name}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onOpenApps) { Text("Choose apps") }
                Text(
                    "For time-limited windows instead, create rules on the Schedules tab.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Blocking engine status
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (serviceRunning) "Blocking engine: active" else "Blocking engine: NOT enabled",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "FocusGuard needs Accessibility access to notice when a blocked app opens and send you back to the home screen. It works fully on your device and does not collect or send any data.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (!serviceRunning) {
                    Button(
                        onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    ) { Text("Enable in Settings") }
                }
            }
        }

        // Schedule summary
        val activeSchedules = config.schedules.count { it.enabled }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Schedules", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (activeSchedules == 0)
                        "No schedules yet. Create one to block apps during certain hours or on certain days."
                    else "$activeSchedules active schedule(s).",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onOpenSchedules) { Text("Manage schedules") }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "How blocking works",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "When you open a blocked app, FocusGuard immediately returns you to the home screen and shows a short 'Blocked' card.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
