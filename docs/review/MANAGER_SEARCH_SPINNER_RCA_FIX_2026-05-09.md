# Manager Investigation - Search Screen Spinner Stuck

Date: 2026-05-09
Device: Realme RMX3081 (`1a79dec0`)
Package: `com.example.devicetracker`
Evidence folder: `docs/uat/evidence/spinner-investigation-2026-05-09`

## User Report

The app list screen kept showing a circular loading spinner while data was already visible on the UI.

## Evidence Before Fix

Screenshot/UI dump:
- `current_screen_pull.png`
- `current_ui.xml`

Observed UI:
- Screen: `Danh sach bao tri`.
- Data was visible, including records `S64.01B` and `M44MR02`.
- UI tree still had `android.widget.ProgressBar` at bounds `[480,1507][600,1627]`.

Local DB evidence:

```text
device_logs_total|1823
device_pending|0
device_failed|0
hgt_total|52
hgt_pending|0
queue_total|0
top1|S64.01B|01/04/2026|SYNCED
```

This means the spinner was not caused by missing local data or active sync queue.

## Root Cause

`SearchViewModel` has two active flows:
- Main search/list flow: loads visible maintenance records.
- Timeline/sidebar reference-year flow: refreshes category/year options.

The timeline flow called `applyTimelineFilter()` without explicitly clearing loading. If it ran while the main flow had `isLoading=true`, it could preserve that stale loading flag even after the list had already rendered data.

`SearchScreen` also rendered a centered `CircularProgressIndicator` over the populated list whenever `uiState.isLoading=true`.

## Fix Applied

Files:
- `android-mvp/app/src/main/java/com/example/devicetracker/ui/search/SearchViewModel.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/ui/search/SearchScreen.kt`

Changes:
- Timeline/reference-year refresh now keeps loading only when no list data has been loaded yet.
- Search UI now only shows the centered list spinner when `isLoading=true` and `items.isEmpty()`.

Scope control:
- No sync core changes.
- No database/schema changes.
- No API contract changes.
- No Google Sheet write.

## Verification

Commands/results:
- `./gradlew.bat --no-daemon :app:assembleDebug` -> PASS.
- `adb -s 1a79dec0 install -r android-mvp/app/build/outputs/apk/debug/app-debug.apk` -> Success.
- App launched on real phone.
- After-fix screenshot: `after_fix_screen.png`.
- After-fix UI dump: `after_fix_ui.xml`.
- After 6 seconds: `after_fix_ui_6s.xml` still showed no `ProgressBar` on the populated list screen.

Installed package evidence:
- `firstInstallTime=2026-05-07 20:55:45`
- `lastUpdateTime=2026-05-09 20:51:28`
- `versionName=0.1.0`

## Conclusion

Status: APPROVE FOR CONTROLLED UAT.

The spinner issue was a Search UI loading-state race, not a sync/data issue. The visible spinner is removed after reinstall, and the list still renders local data correctly.
