package com.focusguard.blocker.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.focusguard.blocker.isEligibleUserApp
import com.focusguard.blocker.SavedAppList
import java.util.Locale

data class AppEntry(val pkg: String, val label: String)

fun loadInstalledApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    return pm.getInstalledApplications(0)
        .filter { runCatching { pm.getLaunchIntentForPackage(it.packageName) != null }.getOrDefault(false) }
        // Omit all Android/system-integrated apps, including preinstalled apps updated by the user.
        .filter { isEligibleUserApp(isSystemApp = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0) }
        .filter { it.packageName != context.packageName }
        .map {
            AppEntry(
                pkg = it.packageName,
                label = runCatching { it.loadLabel(pm).toString() }.getOrDefault(it.packageName)
            )
        }
        .sortedBy { it.label.lowercase() }
}

/** Loads the list of launchable apps off the main thread; null while loading. */
@Composable
fun rememberInstalledApps(): List<AppEntry>? {
    val context = androidx.compose.ui.platform.LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) { loadInstalledApps(context) }
    }
    return apps
}

@Composable
fun SavedAppListsDialog(
    lists: List<SavedAppList>,
    selectedListId: String?,
    currentApps: List<String>,
    onUseList: (SavedAppList?) -> Unit,
    onSaveCurrentAs: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newListName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved app lists") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val duplicateName = lists.any {
                    it.name.equals(newListName.trim(), ignoreCase = true)
                }
                if (lists.isEmpty()) {
                    Text("No saved lists yet. Name and save the current selection below.")
                } else {
                    Column(
                        Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        lists.forEach { list ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(list.name, style = MaterialTheme.typography.bodyLarge)
                                    Text("${list.apps.size} app(s)", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = {
                                    onUseList(list)
                                    onDismiss()
                                }) { Text(if (list.id == selectedListId) "Linked" else "Use") }
                                TextButton(onClick = { onDelete(list.id) }) { Text("Delete") }
                            }
                        }
                    }
                }
                if (selectedListId != null) {
                    OutlinedButton(onClick = {
                        onUseList(null)
                        onDismiss()
                    }) { Text("Unlink and keep current apps") }
                }
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name for current apps") },
                    singleLine = true
                )
                TextButton(
                    onClick = {
                        onSaveCurrentAs(newListName.trim())
                        onDismiss()
                    },
                    enabled = newListName.isNotBlank() && !duplicateName
                ) { Text("Save current selection as new list") }
                if (duplicateName) {
                    Text("A list with that name already exists.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (currentApps.isEmpty()) {
                    Text("Saving an empty list is allowed; an empty allow-list blocks all eligible apps while active.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

fun formatTime(minutes: Int): String =
    String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

private val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun daysLabel(days: Set<Int>): String = when {
    days.isEmpty() || days.size == 7 -> "Every day"
    else -> dayLabels.filterIndexed { i, _ -> (i + 1) in days }.joinToString(", ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") } },
        text = { TimePicker(state) },
    )
}
