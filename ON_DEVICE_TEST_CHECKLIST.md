# FocusGuard on-device test checklist

Use a spare Android phone or test apps you are comfortable temporarily blocking. FocusGuard requires Android 8.0+ (API 26). The checks below use short schedules so you can verify behavior immediately.

## 1. Install and grant permissions

- [ ] Install `app/build/outputs/apk/debug/app-debug.apk` (from Android Studio, choose **Run ▸ app**, or install the APK you built).
- [ ] Launch **FocusGuard**. On Home, confirm **Blocking engine: NOT enabled** initially.
- [ ] Tap **Enable in Settings** and enable **FocusGuard App Blocker** under Accessibility services. Confirm Android's warning, then return to FocusGuard; Home should show **Blocking engine: active**.
- [ ] Optional overlay: if Android offers a **Display over other apps** setting for FocusGuard, enable it. The enforcement should still send blocked apps to Home without this permission; only the explanation card is optional.
- [ ] If the service stops after the screen locks or the phone idles, set FocusGuard battery use to **Unrestricted** in Android's app battery settings and repeat the tests.

## 2. Verify the master switch and general block

- [ ] Open the **Apps** tab and select a harmless user-installed test app (for example, a browser or game). Android/system-integrated apps should not appear in this list.
- [ ] Confirm **Allow selected apps** is OFF (Block-list mode). On **Home**, turn **General block** ON. Try the selected app: you should be returned to Home. An overlay may appear if granted.
- [ ] Turn General block OFF, enable **Allow selected apps** (Allow-list mode), and leave the test app checked. Turn General block ON: the checked app should open normally; another eligible user app that is not checked should be sent Home.
- [ ] Turn Allow-list mode OFF again before continuing with schedule checks.
- [ ] Turn **Blocking enabled** (master switch) OFF. Open the same test app: it should now open normally.
- [ ] Turn the master switch back ON, then turn **General block** OFF. The selected app should open normally unless an enabled schedule also blocks it.

## 3. Verify a weekday and time-window schedule

- [ ] Choose a test app that is not in the General block list. In **Schedules**, tap **+**, name the rule `Quick test`, select that app, and leave **All day** OFF.
- [ ] Select today's weekday only. Set Start to one or two minutes before the current time and End to several minutes after the current time. Save and ensure the schedule is enabled.
- [ ] Open the test app. It should return to Home while the current time is within the rule.
- [ ] Edit the schedule to select a different weekday only. Open the app again; it should now open normally today.
- [ ] Select no weekdays (shown as **Every day**) and save. The same time window should apply regardless of weekday.

## 4. Verify all-day and overnight boundaries

- [ ] Edit the schedule, turn **All day** ON, and select today only. The app should be blocked today at any time, and not on an unselected day.
- [ ] Turn **All day** OFF. Set a test overnight window that includes now (for example, 22:00–06:00 when testing after 22:00; to verify next morning, test between 00:00 and 05:59). Select only the evening's start day.
- [ ] During the after-midnight portion, the app should remain blocked because the selected weekday belongs to the window's start day. At the exact End time (06:00 in this example), it should be allowed.
- [ ] Select a different weekday and verify the after-midnight portion is not blocked by the previous, unselected start day.
- [ ] Set Start and End to the same time: a timed rule should block no interval. Use **All day** for a full-day rule.

## 5. Persistence and cleanup

- [ ] Force-stop/reopen FocusGuard or reboot the test phone. Confirm the app selections and schedules remain saved and the Accessibility service is active. Some Android versions/OEMs may require manually re-enabling the service after reinstall/update.
- [ ] Disable or delete the temporary schedule and turn the General block off. Confirm the test app opens normally.
- [ ] Keep the Accessibility service enabled only if you want ongoing enforcement.

## Expected limits

- The service detects foreground window changes and sends the user Home; it is not a system-level lock and a user can disable the Accessibility service.
- The explanation overlay is optional. Blocking depends on Accessibility access, not overlay access.
- Android/OEM power management can stop background services; allowing unrestricted battery use may be needed for reliable testing.
- This checklist covers behavior on a real device. A successful build and unit tests do not prove device-specific Accessibility behavior; record the phone model, Android version, and any failed step when reporting a problem.
