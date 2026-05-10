# Manager White Screen Navigation RCA Fix (2026-05-08)

## Context

- User reported the app still shows a white screen on the connected device after the latest install.
- Device serial observed: `1a79dec0`.

## Evidence Captured

- `docs/uat/evidence/white-screen-live-2026-05-08/current_screen_pulled.png`
- `docs/uat/evidence/white-screen-live-2026-05-08/dumpsys_activity_activities.txt`
- `docs/uat/evidence/white-screen-live-2026-05-08/dumpsys_window.txt`
- `docs/uat/evidence/white-screen-live-2026-05-08/gfxinfo.txt`
- `docs/uat/evidence/white-screen-live-2026-05-08/logcat_dump.txt`
- `docs/uat/evidence/white-screen-live-2026-05-08/ui_dump.xml`
- `docs/uat/evidence/white-screen-live-2026-05-08/after_force_stop_relaunch.png`

## Findings

- Screenshot confirmed a real white/blank content area while the app was foreground.
- `dumpsys activity/window` showed `com.example.devicetracker/.MainActivity` was `RESUMED`, visible, and focused.
- `dumpsys window lastanr` showed no ANR since boot.
- UI dump showed only the root `android:id/content` frame and no visible Compose nodes.
- Force-stop and cold relaunch restored the normal search screen.

## RCA

The most likely cause is a navigation back-stack empty state after rapid/double back navigation from child screens. In that state, `MainActivity` remains resumed, but `NavHost` has no active destination to draw, leaving a blank content surface.

This matches the evidence better than a crash or permanent main-thread CPU stall:

- no app `FATAL EXCEPTION`
- no ANR
- app remains focused
- cold relaunch restores UI
- UI hierarchy is empty under the content root

## Fix Applied

- File: `android-mvp/app/src/main/java/com/example/devicetracker/ui/navigation/DeviceTrackerNavGraph.kt`
- Replaced direct `navController.popBackStack()` callbacks with `safePopBackStack()`.
- `safePopBackStack()` refuses to pop the root Search route.
- If pop fails or leaves no current entry, it navigates back to `NavRoutes.Search.route`.

## Verification

- Ran: `.\scripts\build-android-safe.ps1 -SkipTests`
- Result: `BUILD SUCCESSFUL`
- New APK: `android-mvp/.codex-build/1778257277724/app/outputs/apk/debug/app-debug.apk`
- SHA256: `846228FD53AD9F2F99B50421849E44C4575A617BFA2C3590E61A78569B1A01B0`

## Runtime Install Follow-up

- Device `1a79dec0` was reconnected.
- Installed `android-mvp/.codex-build/1778257277724/app/outputs/apk/debug/app-debug.apk`.
- Install result: `Success`.
- Package verification: `com.example.devicetracker` exists and resolves to `com.example.devicetracker/.MainActivity`.
- Device package metadata showed `lastUpdateTime=2026-05-08 23:30:30`.
- Cold launch after force-stop succeeded.
- Evidence screenshot: `docs/uat/evidence/white-screen-nav-fix-install-2026-05-08/launch_after_nav_fix_install.png`.
- Install result report: `docs/uat/results/MANAGER_NAV_FIX_INSTALL_RESULT_2026-05-08.md`.

## Decision

- Code/build status: PASS.
- Reinstall status: PASS.
- Cold launch status: PASS.
- User UAT status: PASS; after testing, the user no longer observed the white-screen issue.
- Runtime closure: PASS for the reported white-screen defect.
- Monitoring note: keep observing during longer continuous use because the original defect was intermittent.
