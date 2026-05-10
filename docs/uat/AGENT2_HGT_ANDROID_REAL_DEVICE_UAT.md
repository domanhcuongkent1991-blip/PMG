# UAT Runbook: HGT Sync And Android Notification

**Owner:** Agent 2  
**Reviewed/Corrected by:** Managerial AI  
**Date:** 2026-05-05  
**Status:** Approved for controlled real UAT after Manager review  
**Director approval:** Real Google Sheet and Android phone UAT is allowed after both runbooks are complete.

---

## 1. Goal

Verify HGT behavior on a real Android phone:

- HGT pull from Google Sheet to app.
- HGT push from app to Google Sheet.
- Local PENDING data is not overwritten.
- HGT reminder notification appears in foreground/background/app-closed scenarios.
- Evidence is captured before release decision.

---

## 2. Hard Safety Rules

Allowed:

- Use 1-2 small HGT rows with `CODEX_TEST_HGT_*` marker.
- Use Android notification/alarm settings needed for normal operation.
- Capture screenshots and logs.
- Roll back only test rows/values.

Forbidden:

- Do not change workbook permissions.
- Do not change sheet headers or structure.
- Do not bulk write rows.
- Do not run destructive ADB commands such as `pm clear` unless Manager explicitly approves.
- Do not force-stop the app for the app-closed notification test; swipe it from recent apps instead. Android force-stop can intentionally block alarms/receivers until the user launches the app again.
- Do not declare "Android real device pass" without screenshot/log evidence.

---

## 3. Preconditions

- Debug build and unit tests have passed with:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-android-safe.ps1
```

- APK installed on Android phone.
- Phone connected to internet.
- Google account has editor access to workbook.
- Notification permission is allowed.
- Exact alarm permission is allowed if Android version requires it.
- Battery optimization may be temporarily disabled for reliable UAT.
- `adb devices` shows the phone if log capture is needed.

---

## 4. HGT Sync UAT

### HGT-SYNC-01: Pull Sheet To App

1. Add one HGT test row on `HGT dinh ky`.
2. Include marker `CODEX_TEST_HGT_PULL_<timestamp>`.
3. Run full sync in app.
4. Search for the test device/code in app.
5. Verify values match the sheet.
6. Roll back the test row.

Pass:

- Test HGT row appears in app.
- No unrelated HGT row changes.

### HGT-SYNC-02: Push App To Sheet

1. Create one HGT test item in app with marker `CODEX_TEST_HGT_PUSH_<timestamp>`.
2. Run full sync.
3. Find the marker on `HGT dinh ky`.
4. Verify one row only.
5. Roll back the app/sheet test item.

Pass:

- One correct row appears in sheet.
- No duplicate row appears after one retry sync.

### HGT-SYNC-03: PENDING Protection

1. Create or identify a safe local PENDING HGT test item.
2. Add a remote row with the same key and a different value.
3. Run pull/full sync.
4. Verify local PENDING data is not overwritten.
5. Roll back.

Pass:

- Local pending values stay unchanged.

---

## 5. Notification UAT

Use a short trigger window only for test records. Record setup time and expected trigger time.

### HGT-NOTIF-01: Foreground

1. Open app.
2. Create/select HGT reminder with trigger about 2-5 minutes ahead.
3. Keep app visible.
4. Wait for trigger.
5. Capture notification screenshot/log.

Pass:

- Notification appears near expected time.
- Content contains the correct device/check info.
- Tapping opens the app.

### HGT-NOTIF-02: Background

1. Create/select HGT reminder with trigger about 5 minutes ahead.
2. Press Home so app stays in background.
3. Wait for trigger.
4. Capture screenshot/log.

Pass:

- Notification appears while app is backgrounded.

### HGT-NOTIF-03: App Closed From Recent Apps

1. Create/select HGT reminder with trigger about 5-10 minutes ahead.
2. Swipe app from recent apps.
3. Do not use force-stop.
4. Wait for trigger.
5. Capture screenshot/log.

Pass:

- Notification appears or documented Android/device limitation is observed.

### HGT-NOTIF-04: Reboot Reschedule

Run only if needed before release sign-off.

1. Create/select HGT reminder with trigger after reboot.
2. Reboot phone.
3. Wait until phone fully boots.
4. Do not open app unless the test case says to.
5. Observe notification and capture evidence.

Pass:

- Reminder is rescheduled and notification appears, or the exact Android limitation is documented.

---

## 6. Evidence Table

| Test | Device | Android version | Marker | Expected time | Actual result | Screenshot/log | Result |
|---|---|---|---|---|---|---|---|
| HGT-SYNC-01 | | | | | | | |
| HGT-SYNC-02 | | | | | | | |
| HGT-SYNC-03 | | | | | | | |
| HGT-NOTIF-01 | | | | | | | |
| HGT-NOTIF-02 | | | | | | | |
| HGT-NOTIF-03 | | | | | | | |
| HGT-NOTIF-04 | | | | | | | |

---

## 7. Useful Non-Destructive Commands

```powershell
adb devices
adb logcat -d -s HgtReminderScheduler HgtReminderReceiver HgtReminderBootReceiver
adb shell dumpsys alarm
adb shell dumpsys notification
```

Do not run destructive commands during UAT unless Manager explicitly approves.

---

## 8. Stop Conditions

Stop UAT immediately if:

- App crashes.
- Notification causes repeated errors.
- HGT sync writes to the wrong sheet.
- More than the expected test row changes.
- Test data cannot be rolled back.

Report the issue and do not continue to the next case until reviewed.

---

## 9. Release Criteria For HGT

HGT can be marked UAT PASS only when:

- HGT pull passes.
- HGT push passes.
- PENDING protection passes.
- Foreground notification passes.
- Background notification passes.
- App-closed notification behavior is verified or documented.
- Rollback confirms no `CODEX_TEST_HGT_*` data remains.
