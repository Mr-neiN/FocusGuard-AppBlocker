package com.focusguard.blocker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

val Context.blockDataStore by preferencesDataStore(name = "focusguard_config")

/**
 * One time-based blocking rule.
 *
 * @param days java.time.DayOfWeek values (1 = Monday ... 7 = Sunday).
 *             An empty set means "applies on every day of the week".
 * @param startMinutes / endMinutes minutes from midnight; windows may cross midnight.
 * @param allDay if true, the schedule blocks for the entire selected days.
 */
@Serializable
data class Schedule(
    val id: Long,
    val name: String = "",
    val apps: List<String> = emptyList(),
    val days: Set<Int> = emptySet(),
    val startMinutes: Int = 22 * 60,
    val endMinutes: Int = 23 * 60,
    val allDay: Boolean = false,
    /** False = block selected apps; true = allow selected apps and block other eligible apps while active. */
    val allowSelected: Boolean = false,
    val enabled: Boolean = true,
    /** ID of a shared app list; [apps] remains a snapshot for old data and unlinking. */
    val savedListId: String? = null,
)

@Serializable
data class SavedAppList(
    val id: String,
    val name: String,
    val apps: List<String> = emptyList(),
)

/** Everything the blocker needs, persisted as JSON in DataStore. */
@Serializable
data class BlockConfig(
    val masterEnabled: Boolean = true,
    val generalEnabled: Boolean = false,
    val generalApps: List<String> = emptyList(),
    /** False = block selected apps; true = allow selected apps and block other eligible apps. */
    val generalAllowSelected: Boolean = false,
    val schedules: List<Schedule> = emptyList(),
    val savedAppLists: List<SavedAppList> = emptyList(),
    /** ID of the shared list used for General Block; [generalApps] is a fallback snapshot. */
    val generalSavedListId: String? = null,
) {
    /** Decide whether [packageName] may open right now. */
    fun isAppBlocked(
        packageName: String,
        now: LocalDateTime,
        isEligibleUserApp: Boolean = true,
    ): Boolean {
        if (!masterEnabled || !isEligibleUserApp) return false
        if (generalEnabled) {
            val apps = resolvedGeneralApps()
            if (generalAllowSelected && packageName !in apps) return true
            if (!generalAllowSelected && packageName in apps) return true
        }
        return schedules.any { schedule ->
            val apps = resolvedApps(schedule)
            schedule.enabled &&
                (schedule.allowSelected || apps.isNotEmpty()) &&
                schedule.contains(now) &&
                if (schedule.allowSelected) packageName !in apps else packageName in apps
        }
    }

    fun resolvedGeneralApps(): List<String> =
        savedAppLists.firstOrNull { it.id == generalSavedListId }?.apps ?: generalApps

    fun resolvedApps(schedule: Schedule): List<String> =
        savedAppLists.firstOrNull { it.id == schedule.savedListId }?.apps ?: schedule.apps

    fun linkGeneralAppList(listId: String?): BlockConfig {
        val list = savedAppLists.firstOrNull { it.id == listId }
        return if (listId == null) copy(generalApps = resolvedGeneralApps(), generalSavedListId = null)
        else list?.let { copy(generalApps = it.apps, generalSavedListId = it.id) } ?: this
    }

    fun updateGeneralApps(apps: List<String>): BlockConfig {
        val selected = apps.distinct()
        val linkedList = savedAppLists.firstOrNull { it.id == generalSavedListId }
        return if (linkedList == null) copy(generalApps = selected, generalSavedListId = null)
        else upsertSavedAppList(linkedList.copy(apps = selected))
    }

    fun linkScheduleAppList(scheduleId: Long, listId: String?): BlockConfig {
        val list = savedAppLists.firstOrNull { it.id == listId }
        return copy(schedules = schedules.map { schedule ->
            if (schedule.id != scheduleId) schedule
            else if (listId == null) schedule.copy(apps = resolvedApps(schedule), savedListId = null)
            else list?.let { schedule.copy(apps = it.apps, savedListId = it.id) } ?: schedule
        })
    }

    fun updateScheduleApps(scheduleId: Long, apps: List<String>): BlockConfig {
        val selected = apps.distinct()
        val schedule = schedules.firstOrNull { it.id == scheduleId } ?: return this
        val linkedList = savedAppLists.firstOrNull { it.id == schedule.savedListId }
        if (linkedList != null) return upsertSavedAppList(linkedList.copy(apps = selected))
        return copy(schedules = schedules.map {
            if (it.id == scheduleId) it.copy(apps = selected, savedListId = null) else it
        })
    }

    fun upsertSavedAppList(list: SavedAppList): BlockConfig {
        val normalized = list.copy(name = list.name.trim(), apps = list.apps.distinct())
        require(normalized.name.isNotEmpty()) { "Saved list name must not be blank" }
        require(savedAppLists.none { it.id != normalized.id && it.name.equals(normalized.name, ignoreCase = true) }) {
            "A saved list with this name already exists"
        }
        val updatedSchedules = schedules.map { schedule ->
            if (schedule.savedListId == normalized.id) schedule.copy(apps = normalized.apps) else schedule
        }
        return copy(
            savedAppLists = savedAppLists.filterNot { it.id == normalized.id } + normalized,
            generalApps = if (generalSavedListId == normalized.id) normalized.apps else generalApps,
            schedules = updatedSchedules,
        )
    }

    fun deleteSavedAppList(listId: String): BlockConfig {
        val list = savedAppLists.firstOrNull { it.id == listId } ?: return this
        return copy(
            savedAppLists = savedAppLists.filterNot { it.id == listId },
            generalApps = if (generalSavedListId == listId) list.apps else generalApps,
            generalSavedListId = if (generalSavedListId == listId) null else generalSavedListId,
            schedules = schedules.map { schedule ->
                if (schedule.savedListId == listId) schedule.copy(apps = list.apps, savedListId = null) else schedule
            },
        )
    }
}

/**
 * Whether this schedule is active at [now]. For overnight windows, the selected day
 * belongs to the window's start; the after-midnight portion therefore uses yesterday's day.
 * Time windows are half-open ([start, end)); equal start/end times match nothing unless allDay.
 */
fun Schedule.contains(now: LocalDateTime): Boolean {
    val currentDay = now.dayOfWeek.value
    val minutes = now.hour * 60 + now.minute
    if (allDay) return days.isEmpty() || currentDay in days

    return when {
        startMinutes < endMinutes ->
            (days.isEmpty() || currentDay in days) && minutes >= startMinutes && minutes < endMinutes
        startMinutes > endMinutes -> when {
            minutes >= startMinutes -> days.isEmpty() || currentDay in days
            minutes < endMinutes -> {
                val startDay = if (currentDay == 1) 7 else currentDay - 1
                days.isEmpty() || startDay in days
            }
            else -> false
        }
        else -> false
    }
}

/** System-integrated apps are excluded, even when their installed package was updated. */
fun isEligibleUserApp(isSystemApp: Boolean): Boolean = !isSystemApp

/** Shared store helpers used by both the UI and the accessibility service. */
object BlockStore {
    val KEY = stringPreferencesKey("focusguard_config")
    val json = Json { ignoreUnknownKeys = true }

    fun decode(raw: String?): BlockConfig =
        raw?.let { runCatching { json.decodeFromString(BlockConfig.serializer(), it) }.getOrNull() }
            ?: BlockConfig()

    suspend fun current(store: DataStore<Preferences>): BlockConfig =
        decode(store.data.first()[KEY])
}

/** Small repository wrapper around DataStore. */
class BlockRepo(private val context: Context) {
    val configFlow: Flow<BlockConfig> = context.blockDataStore.data.map { prefs ->
        BlockStore.decode(prefs[BlockStore.KEY])
    }

    suspend fun current(): BlockConfig = BlockStore.current(context.blockDataStore)

    suspend fun update(transform: (BlockConfig) -> BlockConfig) {
        context.blockDataStore.edit { prefs ->
            val current = BlockStore.decode(prefs[BlockStore.KEY])
            prefs[BlockStore.KEY] = BlockStore.json.encodeToString(BlockConfig.serializer(), transform(current))
        }
    }
}
