package com.focusguard.blocker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.blocker.BlockConfig
import com.focusguard.blocker.BlockRepo
import com.focusguard.blocker.SavedAppList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun GeneralAppsScreen(
    config: BlockConfig,
    repo: BlockRepo,
    scope: CoroutineScope,
) {
    var query by remember { mutableStateOf("") }
    var showSavedLists by remember { mutableStateOf(false) }
    val loaded = rememberInstalledApps()
    val selectedListName = config.savedAppLists.firstOrNull { it.id == config.generalSavedListId }?.name

    Column(Modifier.fillMaxSize()) {
        Card(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("General block", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (config.generalEnabled && config.generalAllowSelected)
                            "ON – checked apps are allowed; other user apps are blocked all the time."
                        else if (config.generalEnabled)
                            "ON – checked apps are blocked all the time."
                        else
                            "OFF – general mode is paused; schedules still apply.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = config.generalEnabled,
                    onCheckedChange = { v -> scope.launch { repo.update { it.copy(generalEnabled = v) } } }
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            label = { Text("Search apps") },
            singleLine = true
        )

        Text(
            if (config.generalAllowSelected)
                "Allow-list mode: checked apps are exempt from General block. Other eligible user apps are blocked."
            else
                "Block-list mode: checked apps are blocked by General block.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Allow selected apps", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${if (config.generalAllowSelected) "Allow-list" else "Block-list"} mode",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = config.generalAllowSelected,
                onCheckedChange = { v ->
                    scope.launch { repo.update { it.copy(generalAllowSelected = v) } }
                }
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    selectedListName?.let { "Shared list: $it" } ?: "Custom app selection",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Save a named list to reuse it in schedules.", style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.OutlinedButton(onClick = { showSavedLists = true }) {
                Text("Saved lists")
            }
        }

        if (loaded == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val q = query.trim().lowercase()
            val filtered =
                if (q.isEmpty()) loaded
                else loaded.filter { it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q) }

            if (filtered.isEmpty()) {
                Text("No eligible user apps found", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filtered, key = { it.pkg }) { e ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(e.label, style = MaterialTheme.typography.bodyLarge)
                                Text(e.pkg, style = MaterialTheme.typography.bodySmall)
                            }
                            Checkbox(
                                checked = e.pkg in config.resolvedGeneralApps(),
                                onCheckedChange = { v ->
                                    scope.launch {
                                        repo.update { c ->
                                            val apps = c.resolvedGeneralApps()
                                            c.updateGeneralApps(
                                                if (v) apps + e.pkg else apps - e.pkg
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSavedLists) {
        SavedAppListsDialog(
            lists = config.savedAppLists,
            selectedListId = config.generalSavedListId,
            currentApps = config.resolvedGeneralApps(),
            onUseList = { list ->
                scope.launch { repo.update { it.linkGeneralAppList(list?.id) } }
            },
            onSaveCurrentAs = { name ->
                val list = SavedAppList(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    apps = config.resolvedGeneralApps(),
                )
                scope.launch {
                    repo.update { c ->
                        c.upsertSavedAppList(list).copy(generalApps = list.apps, generalSavedListId = list.id)
                    }
                }
            },
            onDelete = { id ->
                scope.launch { repo.update { it.deleteSavedAppList(id) } }
            },
            onDismiss = { showSavedLists = false },
        )
    }
}
