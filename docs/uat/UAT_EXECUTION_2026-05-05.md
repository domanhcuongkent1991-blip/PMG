# UAT Execution Log - 2026-05-05

## Scope

Controlled real-device UAT preparation and initial smoke test.

## Director Approval

The Director approved real UAT on Google Sheet and Android phone after both runbooks were completed.

## Environment

| Item | Value |
|---|---|
| Device model | RMX3081 |
| Android version | 12 |
| ADB state | device |
| App package | com.example.devicetracker |
| APK | android-mvp/app/build/outputs/apk/debug/app-debug.apk |
| App version | 0.1.0 |
| versionCode | 1 |

## Actions Completed

| Time | Action | Result |
|---|---|---|
| 2026-05-05 | Checked ADB device | PASS - RMX3081 detected |
| 2026-05-05 | Tried installing debug APK over existing app | BLOCKED - signature mismatch |
| 2026-05-05 | Director approved uninstalling old app | APPROVED |
| 2026-05-05 | Uninstalled old `com.example.devicetracker` | PASS |
| 2026-05-05 | Installed current debug APK | PASS |
| 2026-05-05 | Launched app | PASS |
| 2026-05-05 | Checked short crash log | PASS - no AndroidRuntime crash found in filtered log |
| 2026-05-05 | Captured home screen evidence | PASS |
| 2026-05-05 | Opened app main menu | PASS - DMBT year filters and sync status entry visible |
| 2026-05-05 | Opened sync status screen | PASS - 2043 local records, 1995 DMBT, 48 HGT, 0 pending |
| 2026-05-05 | Ran full-sync smoke test with 0 pending rows | PASS - screen still shows 2043 synced, 0 pending, queue 0, retry errors 0 |

## Evidence

| Evidence | Path |
|---|---|
| Home screen after install/launch | docs/uat/evidence/devicetracker_smoke_home.png |
| Main menu/navigation | docs/uat/evidence/devicetracker_drawer.png |
| Sync status before real sync smoke test | docs/uat/evidence/devicetracker_sync_status.png |
| Sync status after full-sync smoke test | docs/uat/evidence/devicetracker_after_full_sync_smoke.png |

## Current Observation

The app opens to the maintenance list screen and displays DMBT data. This verifies that the APK can be installed and launched on the real Android phone.

The main menu opens and shows DMBT year filters plus the sync status entry.

The sync status screen shows zero pending records before the first full-sync smoke test.

After the first full-sync smoke test, the sync status screen still shows all local records synced and zero pending/retry items.

## Remaining UAT

- DMBT/repair real-sheet UAT.
- HGT sync real-sheet UAT.
- HGT notification foreground/background/app-closed UAT.

## Notes

- The old installed app used a different signature. It was uninstalled after Director approval, which clears local app data on the phone.
- No destructive ADB cleanup commands were used.
