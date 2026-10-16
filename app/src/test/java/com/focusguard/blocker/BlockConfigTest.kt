package com.focusguard.blocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime

class BlockConfigTest {
    private fun dateFor(dayOfWeek: Int, hour: Int, minute: Int = 0): LocalDateTime {
        val monday = LocalDateTime.of(2026, 10, 12, hour, minute) // Monday
        return monday.plusDays((dayOfWeek - 1).toLong())
    }

    private val app = "com.example.social"

    @Test
    fun generalBlockAppliesAllDayUntilMasterDisabled() {
        val config = BlockConfig(generalEnabled = true, generalApps = listOf(app))
        assertTrue(config.isAppBlocked(app, dateFor(3, 12)))
        assertFalse(config.copy(masterEnabled = false).isAppBlocked(app, dateFor(3, 12)))
        assertFalse(config.isAppBlocked("com.example.other", dateFor(3, 12)))
    }

    @Test
    fun allowSelectedGeneralModeBlocksOtherEligibleUserApps() {
        val config = BlockConfig(
            generalEnabled = true,
            generalApps = listOf(app),
            generalAllowSelected = true,
        )
        assertFalse(config.isAppBlocked(app, dateFor(3, 12)))
        assertTrue(config.isAppBlocked("com.example.other", dateFor(3, 12)))
        assertFalse(config.isAppBlocked("com.android.systemui", dateFor(3, 12), isEligibleUserApp = false))
        assertFalse(config.copy(masterEnabled = false).isAppBlocked("com.example.other", dateFor(3, 12)))
    }

    @Test
    fun systemAppsAreIneligibleButUserAppsAreEligible() {
        assertFalse(isEligibleUserApp(isSystemApp = true))
        assertTrue(isEligibleUserApp(isSystemApp = false))
    }

    @Test
    fun aSpecificScheduleStillBlocksAnAllowedApp() {
        val schedule = Schedule(
            id = 8,
            apps = listOf(app),
            days = emptySet(),
            startMinutes = 9 * 60,
            endMinutes = 10 * 60,
        )
        val config = BlockConfig(
            generalEnabled = true,
            generalApps = listOf(app),
            generalAllowSelected = true,
            schedules = listOf(schedule),
        )
        assertFalse(config.isAppBlocked(app, dateFor(3, 8, 59)))
        assertTrue(config.isAppBlocked(app, dateFor(3, 9)))
        assertFalse(config.isAppBlocked(app, dateFor(3, 10)))
    }

    @Test
    fun scheduleAllowListBlocksOtherAppsOnlyDuringItsWindow() {
        val other = "com.example.other"
        val schedule = Schedule(
            id = 9,
            apps = listOf(app),
            days = setOf(3),
            startMinutes = 9 * 60,
            endMinutes = 10 * 60,
            allowSelected = true,
        )
        val config = BlockConfig(schedules = listOf(schedule))
        assertFalse(config.isAppBlocked(app, dateFor(3, 9)))
        assertTrue(config.isAppBlocked(other, dateFor(3, 9)))
        assertFalse(config.isAppBlocked(other, dateFor(3, 10)))
        assertFalse(config.isAppBlocked(other, dateFor(2, 9)))
        assertFalse(config.isAppBlocked(other, dateFor(3, 9), isEligibleUserApp = false))
    }

    @Test
    fun scheduleModesCombineByBlockingUnion() {
        val other = "com.example.other"
        val config = BlockConfig(
            schedules = listOf(
                Schedule(id = 10, apps = listOf(app), startMinutes = 9 * 60, endMinutes = 10 * 60),
                Schedule(
                    id = 11,
                    apps = listOf(app),
                    startMinutes = 9 * 60,
                    endMinutes = 10 * 60,
                    allowSelected = true,
                ),
            )
        )
        assertTrue(config.isAppBlocked(app, dateFor(3, 9)))
        assertTrue(config.isAppBlocked(other, dateFor(3, 9)))
    }

    @Test
    fun sharedAppListUpdatesGeneralAndScheduleReferences() {
        val other = "com.example.other"
        val list = SavedAppList(id = "social", name = "Social apps", apps = listOf(app))
        val schedule = Schedule(
            id = 12,
            apps = listOf(app),
            startMinutes = 9 * 60,
            endMinutes = 10 * 60,
            savedListId = list.id,
        )
        val linked = BlockConfig(
            generalEnabled = true,
            generalSavedListId = list.id,
            schedules = listOf(schedule),
            savedAppLists = listOf(list),
        )
        assertTrue(linked.isAppBlocked(app, dateFor(3, 12)))
        assertFalse(linked.isAppBlocked(other, dateFor(3, 12)))

        val updated = linked.updateGeneralApps(listOf(other))
        assertEquals(listOf(other), updated.resolvedGeneralApps())
        assertEquals(listOf(other), updated.resolvedApps(updated.schedules.single()))
        assertFalse(updated.isAppBlocked(app, dateFor(3, 12)))
        assertTrue(updated.isAppBlocked(other, dateFor(3, 12)))

        val scheduleEdited = linked.updateScheduleApps(schedule.id, listOf(other))
        assertEquals(listOf(other), scheduleEdited.resolvedGeneralApps())
        assertEquals(listOf(other), scheduleEdited.resolvedApps(scheduleEdited.schedules.single()))
    }

    @Test
    fun unlinkAndDeleteSavedListPreserveAppSelections() {
        val list = SavedAppList(id = "shared", name = "Shared", apps = listOf(app))
        val schedule = Schedule(id = 13, apps = listOf(app), savedListId = list.id)
        val config = BlockConfig(
            generalApps = listOf(app),
            generalSavedListId = list.id,
            schedules = listOf(schedule),
            savedAppLists = listOf(list),
        )
        val unlinked = config.linkGeneralAppList(null).linkScheduleAppList(schedule.id, null)
        assertNull(unlinked.generalSavedListId)
        assertNull(unlinked.schedules.single().savedListId)
        assertEquals(listOf(app), unlinked.generalApps)
        assertEquals(listOf(app), unlinked.schedules.single().apps)

        val deleted = config.deleteSavedAppList(list.id)
        assertTrue(deleted.savedAppLists.isEmpty())
        assertNull(deleted.generalSavedListId)
        assertNull(deleted.schedules.single().savedListId)
        assertEquals(listOf(app), deleted.generalApps)
        assertEquals(listOf(app), deleted.schedules.single().apps)
    }

    @Test
    fun linkedGeneralSelectionCanBeSwitchedToAnotherSavedList() {
        val first = SavedAppList(id = "first", name = "First", apps = listOf(app))
        val second = SavedAppList(id = "second", name = "Second", apps = listOf("com.example.other"))
        val config = BlockConfig(
            generalApps = listOf(app),
            generalSavedListId = first.id,
            savedAppLists = listOf(first, second),
        )
        val switched = config.linkGeneralAppList(second.id)
        assertEquals(second.id, switched.generalSavedListId)
        assertEquals(second.apps, switched.resolvedGeneralApps())
    }

    @Test
    fun savedListRejectsDuplicateNamesAndEmptyAppCollections() {
        val existing = SavedAppList(id = "existing", name = "Social", apps = listOf(app))
        val config = BlockConfig(savedAppLists = listOf(existing))
        assertThrows(IllegalArgumentException::class.java) {
            config.upsertSavedAppList(SavedAppList(id = "duplicate", name = " social ", apps = listOf(app)))
        }
        assertEquals(
            emptyList<String>(),
            config.upsertSavedAppList(SavedAppList(id = "empty", name = "Empty", apps = emptyList()))
                .savedAppLists.last().apps,
        )
        val allowEmpty = BlockConfig(
            schedules = listOf(
                Schedule(
                    id = 15,
                    allowSelected = true,
                    startMinutes = 9 * 60,
                    endMinutes = 10 * 60,
                    savedListId = "empty",
                )
            ),
            savedAppLists = listOf(SavedAppList(id = "empty", name = "Empty", apps = emptyList())),
        )
        assertTrue(allowEmpty.isAppBlocked("com.example.any", dateFor(3, 9)))
    }

    @Test
    fun legacyConfigWithoutSavedListIdsKeepsInlineAppLists() {
        val config = BlockStore.decode("""{"generalApps":["$app"],"schedules":[{"id":14,"apps":["$app"]}]}""")
        assertEquals(listOf(app), config.resolvedGeneralApps())
        assertEquals(listOf(app), config.resolvedApps(config.schedules.single()))
        assertTrue(config.isAppBlocked(app, dateFor(3, 22)))
    }

    @Test
    fun emptyDaysMeansEveryDayForNormalTimeWindow() {
        val schedule = Schedule(id = 1, apps = listOf(app), startMinutes = 9 * 60, endMinutes = 10 * 60)
        val config = BlockConfig(schedules = listOf(schedule))
        assertTrue(config.isAppBlocked(app, dateFor(1, 9)))
        assertTrue(config.isAppBlocked(app, dateFor(7, 9, 59)))
        assertFalse(config.isAppBlocked(app, dateFor(3, 10))) // end is exclusive
        assertFalse(config.isAppBlocked(app, dateFor(3, 8, 59)))
    }

    @Test
    fun selectedWeekdaysApplyToNormalWindowOnly() {
        val schedule = Schedule(
            id = 2,
            apps = listOf(app),
            days = setOf(1, 3, 5),
            startMinutes = 14 * 60,
            endMinutes = 15 * 60,
        )
        val config = BlockConfig(schedules = listOf(schedule))
        assertTrue(config.isAppBlocked(app, dateFor(1, 14)))
        assertTrue(config.isAppBlocked(app, dateFor(5, 14, 59)))
        assertFalse(config.isAppBlocked(app, dateFor(2, 14)))
    }

    @Test
    fun overnightWindowUsesStartDayForAfterMidnightPortion() {
        val schedule = Schedule(
            id = 3,
            apps = listOf(app),
            days = setOf(1), // Monday night into Tuesday morning
            startMinutes = 22 * 60,
            endMinutes = 6 * 60,
        )
        val config = BlockConfig(schedules = listOf(schedule))
        assertTrue(config.isAppBlocked(app, dateFor(1, 22)))
        assertTrue(config.isAppBlocked(app, dateFor(1, 23, 59)))
        assertTrue(config.isAppBlocked(app, dateFor(2, 0)))
        assertTrue(config.isAppBlocked(app, dateFor(2, 5, 59)))
        assertFalse(config.isAppBlocked(app, dateFor(2, 6)))
        assertFalse(config.isAppBlocked(app, dateFor(3, 1))) // Tuesday was not selected
    }

    @Test
    fun overnightWindowWrapsFromSundayIntoMonday() {
        val schedule = Schedule(
            id = 4,
            apps = listOf(app),
            days = setOf(7),
            startMinutes = 23 * 60,
            endMinutes = 5 * 60,
        )
        val config = BlockConfig(schedules = listOf(schedule))
        assertTrue(config.isAppBlocked(app, dateFor(7, 23)))
        assertTrue(config.isAppBlocked(app, dateFor(1, 4, 59)))
        assertFalse(config.isAppBlocked(app, dateFor(1, 5)))
        assertFalse(config.isAppBlocked(app, dateFor(1, 23))) // Monday not selected
    }

    @Test
    fun allDayRespectsSelectedDayAndEqualTimesAreEmpty() {
        val allDay = Schedule(id = 5, apps = listOf(app), days = setOf(6), allDay = true)
        val equalTimes = Schedule(id = 6, apps = listOf(app), days = setOf(6), startMinutes = 600, endMinutes = 600)
        assertTrue(BlockConfig(schedules = listOf(allDay)).isAppBlocked(app, dateFor(6, 23, 59)))
        assertFalse(BlockConfig(schedules = listOf(allDay)).isAppBlocked(app, dateFor(5, 12)))
        assertFalse(BlockConfig(schedules = listOf(equalTimes)).isAppBlocked(app, dateFor(6, 10)))
    }

    @Test
    fun disabledScheduleAndUnselectedAppDoNotBlock() {
        val schedule = Schedule(id = 7, apps = listOf(app), days = emptySet(), enabled = false)
        val config = BlockConfig(schedules = listOf(schedule))
        assertFalse(config.isAppBlocked(app, dateFor(3, 22)))
        assertFalse(config.isAppBlocked("com.example.other", dateFor(3, 22)))
    }
}
