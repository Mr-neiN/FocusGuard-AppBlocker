package com.focusguard.blocker.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusguard.blocker.BlockConfig
import com.focusguard.blocker.BlockRepo
import com.focusguard.blocker.Schedule
import com.focusguard.blocker.SavedAppList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(
    config: BlockConfig,
    repo: BlockRepo,
    scope: CoroutineScope,
) {
    // null = closed; otherwise the schedule being created or edited
    var draft by remember { mutableStateOf<Schedule?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (config.schedules.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No schedules yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap + to create a rule, e.g. 'TikTok 22:00–06:00 on school nights' or 'Block games all Sunday'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(config.schedules, key = { it.id }) { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s.name.ifBlank { "Untitled schedule" },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "${config.resolvedApps(s).size} app(s)" +
                                        (config.savedAppLists.firstOrNull { it.id == s.savedListId }?.let { " • ${it.name}" } ?: "") +
                                        " • " + (if (s.allowSelected) "Allow-list" else "Block-list") + " • " +
                                        (if (s.allDay) "All day" else "${formatTime(s.startMinutes)}–${formatTime(s.endMinutes)}") +
                                        " • ${daysLabel(s.days)}" +
                                        (if (!s.enabled) " • PAUSED" else ""),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = s.enabled,
                                onCheckedChange = { v ->
                                    scope.launch {
                                        repo.update { c ->
                                            c.copy(schedules = c.schedules.map {
                                                if (it.id == s.id) it.copy(enabled = v) else it
                                            })
                                        }
                                    }
                                }
                            )
                            TextButton(onClick = { draft = s }) { Text("Edit") }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { draft = Schedule(id = System.currentTimeMillis()) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add schedule")
        }
    }

    draft?.let { original ->
        ScheduleEditor(
            schedule = original,
            isNew = config.schedules.none { it.id == original.id },
            onDismiss = { draft = null },
            savedLists = config.savedAppLists,
            initialApps = config.resolvedApps(original),
            onSaveList = { list ->
                scope.launch {
                    repo.update { c ->
                        val withList = c.upsertSavedAppList(list)
                        withList.copy(schedules = withList.schedules.map {
                            if (it.id == original.id) it.copy(apps = list.apps, savedListId = list.id) else it
                        })
                    }
                }
            },
            onDeleteList = { listId ->
                scope.launch { repo.update { it.deleteSavedAppList(listId) } }
            },
            onSave = { updated ->
                scope.launch {
                    repo.update { c ->
                        val linkedList = c.savedAppLists.firstOrNull { it.id == updated.savedListId }
                        val withUpdatedList = if (linkedList != null) {
                            c.upsertSavedAppList(linkedList.copy(apps = updated.apps))
                        } else c
                        val scheduleToSave = updated.copy(
                            apps = updated.savedListId?.let { id ->
                                withUpdatedList.savedAppLists.firstOrNull { it.id == id }?.apps
                            } ?: updated.apps
                        )
                        withUpdatedList.copy(
                            schedules = withUpdatedList.schedules.filter { it.id != updated.id } + scheduleToSave
                        )
                    }
                }
                draft = null
            },
            onDelete = { id ->
                scope.launch {
                    repo.update { c -> c.copy(schedules = c.schedules.filter { it.id != id }) }
                }
                draft = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditor(
    schedule: Schedule,
    isNew: Boolean,
    savedLists: List<SavedAppList>,
    initialApps: List<String>,
    onSaveList: (SavedAppList) -> Unit,
    onDeleteList: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var s by remember(schedule.id) { mutableStateOf(schedule.copy(apps = initialApps)) }
    var showApps by remember { mutableStateOf(false) }
    var showSavedLists by remember { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf<String?>(null) } // "start" or "end"

    LaunchedEffect(s.savedListId, savedLists) {
        savedLists.firstOrNull { it.id == s.savedListId }?.let { list ->
            if (s.apps != list.apps) s = s.copy(apps = list.apps)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isNew) "New schedule" else "Edit schedule",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (!isNew) {
                        IconButton(onClick = { onDelete(s.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete schedule")
                        }
                    }
                }

                OutlinedTextField(
                    value = s.name,
                    onValueChange = { s = s.copy(name = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name (optional)") },
                    placeholder = { Text("e.g. Social media at night") },
                    singleLine = true
                )

                // Apps and reusable shared lists
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Apps", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { showApps = true }) { Text("${s.apps.size} selected – choose") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showSavedLists = true }) { Text("Saved lists") }
                    savedLists.firstOrNull { it.id == s.savedListId }?.let {
                        Text("Linked: ${it.name}", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (s.apps.isEmpty()) {
                    Text(
                        "Pick at least one app to be able to save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow selected apps", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (s.allowSelected)
                                "During this schedule, checked apps are allowed and other eligible user apps are blocked."
                            else
                                "During this schedule, only checked apps are blocked.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = s.allowSelected,
                        onCheckedChange = { v -> s = s.copy(allowSelected = v) }
                    )
                }

                // All day + time window
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("All day", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (s.allowSelected)
                                "Apply the allow-list for the whole selected day(s)."
                            else
                                "Block selected apps for the whole selected day(s).",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = s.allDay,
                        onCheckedChange = { v -> s = s.copy(allDay = v) }
                    )
                }

                if (!s.allDay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickingTime = "start" }) {
                            Text("Start ${formatTime(s.startMinutes)}")
                        }
                        OutlinedButton(onClick = { pickingTime = "end" }) {
                            Text("End ${formatTime(s.endMinutes)}")
                        }
                    }
                    Text(
                        "Windows that cross midnight work too, e.g. 22:00–06:00.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Day of week
                Text("Days", style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { i, label ->
                        val value = i + 1
                        FilterChip(
                            selected = value in s.days,
                            onClick = {
                                s = s.copy(
                                    days = if (value in s.days) s.days - value else s.days + value
                                )
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Text(
                    if (s.days.isEmpty()) "No days selected – applies every day of the week."
                    else "Applies on: ${daysLabel(s.days)}.",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = { onSave(s) },
                        enabled = s.apps.isNotEmpty() || s.allowSelected
                    ) { Text("Save") }
                }
            }
        }
    }

    if (showSavedLists) {
        SavedAppListsDialog(
            lists = savedLists,
            selectedListId = s.savedListId,
            currentApps = s.apps,
            onUseList = { list ->
                s = if (list == null) s.copy(savedListId = null)
                else s.copy(savedListId = list.id, apps = list.apps)
            },
            onSaveCurrentAs = { name ->
                val list = SavedAppList(UUID.randomUUID().toString(), name, s.apps)
                onSaveList(list)
                s = s.copy(savedListId = list.id)
            },
            onDelete = { listId ->
                if (s.savedListId == listId) s = s.copy(savedListId = null)
                onDeleteList(listId)
            },
            onDismiss = { showSavedLists = false },
        )
    }

    // Nested app picker
    val apps = rememberInstalledApps()
    val currentSelectedApps = s.apps
    if (showApps) {
        AppPickerDialog(
            apps = apps.orEmpty(),
            selected = currentSelectedApps.toSet(),
            onToggle = { pkg ->
                val updatedApps = if (pkg in currentSelectedApps) currentSelectedApps - pkg
                else currentSelectedApps + pkg
                s = s.copy(apps = updatedApps)
            },
            onDone = { showApps = false }
        )
    }

    // Nested time picker
    pickingTime?.let { which ->
        val initial = if (which == "start") s.startMinutes else s.endMinutes
        TimePickerDialog(
            initialHour = initial / 60,
            initialMinute = initial % 60,
            onDismiss = { pickingTime = null },
            onConfirm = { h, m ->
                val total = h * 60 + m
                s = if (which == "start") s.copy(startMinutes = total) else s.copy(endMinutes = total)
                pickingTime = null
            }
        )
    }
}
