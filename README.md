# FocusGuard — Android App Blocker

FocusGuard lets you pick apps on your Android phone and block access to them:

- **General block** — choose between **Block-list** (only checked apps are blocked all day) and **Allow-list** (checked apps are allowed; all other eligible user-installed apps are blocked all day). Turn General block off to pause that mode.
- **Schedules** — choose either **Block-list** (only checked apps are blocked) or **Allow-list** (checked apps are allowed while other eligible user-installed apps are blocked), active only during the chosen time window (e.g. 22:00–06:00, overnight supported) and weekdays (Mon–Sun; select none for "every day").
- **Saved app lists** — name and save a selection once, then link the same list to General Block and any schedules. Editing apps in a linked rule updates the shared list everywhere; unlinking or deleting a list preserves each rule's current app selection.
- Android/system-integrated apps are hidden from the picker and excluded from blocking, including preinstalled apps that have been updated.
- A master switch pauses/resumes everything at once.

When a blocked app opens during a blocked time, the app is instantly closed and you're returned to the home screen, with a short "Blocked" card overlay (if "Display over other apps" permission is granted).

## How blocking works on Android

Android has no public "block this app" API. FocusGuard uses the standard approach: an
**AccessibilityService** watches window-change events. When the app that comes to the foreground is
blocked by your rules at that moment, the service performs a global "Home" action and shows the
overlay card. Everything runs locally — no internet permission, no data collection.

## Building it

The project has been verified with a successful `assembleDebug` build using JDK 17, Gradle 8.9, and Android SDK 35. The generated debug APK is at `app/build/outputs/apk/debug/app-debug.apk` after building.

1. Open the `AppBlocker` folder in **Android Studio** (Hedgehog or newer recommended; let Gradle sync finish).
2. Run it on a device or emulator: **Run ▸ app**.
   - Requires **Android 8.0+ (API 26)**.

Command line build (with Android SDK platform 35 and JDK 17 available):
```
gradle :app:assembleDebug
```

For a step-by-step phone verification plan, follow [ON_DEVICE_TEST_CHECKLIST.md](ON_DEVICE_TEST_CHECKLIST.md). Rules with an overnight window use the selected day as the **start day** (for example, Monday 22:00–Tuesday 06:00 is selected by choosing Monday). End times are exclusive; a 22:00–06:00 rule stops at 06:00.

## Setting it up on your phone

1. Open FocusGuard and go to **Apps** (bottom tab) to choose the General block behavior and apps.
2. Leave **Allow selected apps** OFF to block only checked apps, or turn it ON to allow checked apps and block other user apps. Turn **General block** ON on Home to enforce that mode — or leave it OFF and use schedules only.
3. On the **Schedules** tab, tap **+** to create a rule:
   - choose Block-list or Allow-list, then pick apps or link a saved list (you can create one from the current selection),
   - set "All day" or start/end times (overnight windows like 22:00–06:00 work),
   - tap the Mon–Sun chips to choose days (none selected = every day).
4. Tap **Enable in Settings** on the Home tab and enable **FocusGuard App Blocker** in Android's Accessibility settings. Without this step nothing is enforced.

## Tips for reliable blocking

- **Exclude FocusGuard from battery optimization** (Settings → Apps → FocusGuard → Battery → Unrestricted). Aggressive OEM battery managers (Samsung, Xiaomi, etc.) can otherwise stop the service.
- To also block FocusGuard from being uninstalled/killed by a child, Android's device admin or a parental-control profile is a separate concern not covered here.

## Notes & limitations

- `QUERY_ALL_PACKAGES` is required to list eligible installed apps. All system-integrated apps are filtered out, including updated preinstalled apps. Personal/sideloaded use is fine; publishing on Google Play would need a policy exception or a different discovery approach.
- No app is pinned unbreakable — a determined user can always disable the accessibility service. That is inherent to non-parental app blockers.
- iOS equivalent would rely on Apple's Screen Time APIs (`FamilyControls`, `DeviceActivity`), which require a special Apple entitlement; that is a separate project.

## Project layout

```
app/src/main/java/com/focusguard/blocker/
  MainActivity.kt                  # tab scaffold: Home / Schedules / Apps
  BlockConfig.kt                   # Schedule + BlockConfig model, DayOfWeek logic, DataStore repo
  BlockAccessibilityService.kt     # the blocking engine (window events → home + overlay)
  ui/
    HomeScreen.kt                  # master switch, general block, engine status
    SchedulesScreen.kt             # schedule list + editor (apps, days, time windows)
    GeneralAppsScreen.kt           # searchable installed-app picker for the general block
    Common.kt                      # shared helpers: app loading, time/day formatting, dialogs
```
