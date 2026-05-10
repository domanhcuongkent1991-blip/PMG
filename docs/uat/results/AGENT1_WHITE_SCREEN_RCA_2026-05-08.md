# AGENT1 White Screen RCA Runtime (2026-05-08)

## Task
- Task ID: `A1-WHITE-SCREEN-RCA-RUNTIME`
- Goal: reproduce white-screen after Save/Back/Sync navigation loop, capture runtime evidence, identify likely root cause.

## Environment
- Device: `RMX3081` (`adb serial: 1a79dec0`)
- APK installed:
  - `F:\codex_android_gsheet_full_pack\android-mvp\.codex-build\1778248878447\app\outputs\apk\debug\app-debug.apk`
- Install result: `Success`

## Repro Scenario Executed
Repeated loop on device:
- `Save -> Back -> Sync Status -> Back -> Save again` (target 20-30 loops)

User reported white screen during loop:
- Repro timestamp captured: `2026-05-08 21:21:48 +07:00` (host capture time right after user said "vừa bị xong").

## Evidence Collected
Folder:
- `docs/uat/evidence/white-screen-2026-05-08/`

Files:
- `logcat_full_dump.txt` (full logcat dump at incident window)
- `logcat_runtime_watch.txt` (focused runtime watch output)
- `dumpsys_activity_top.txt` (activity top snapshot after issue)
- `white_screen_live_capture.png` (live screenshot at incident window)

Note:
- Background `screenrecord` file was not found at expected path (`/sdcard/Download/white_screen_repro_2026-05-08.mp4`), so video evidence was unavailable in this run.

## Runtime Findings
1. **No crash stacktrace for app process** found in incident window.
   - No `FATAL EXCEPTION` from `AndroidRuntime` tied to `com.example.devicetracker`.

2. **Strong UI stall/freeze signal on main thread**:
   - `Choreographer`: `Skipped 34 frames` at `21:20:33.795`
   - `Choreographer`: `Skipped 43 frames` at `21:20:34.527`
   - `Choreographer`: `Skipped 1874 frames` at `21:20:41.389`
   - `Choreographer`: `Skipped 1216 frames` at `21:21:11.342`

3. App remained foreground/top in system traces around issue window.
   - `top=com.example.devicetracker.MainActivity` in battery/activity telemetry.

## Interpretation (RCA)
- Current evidence points to **render/main-thread starvation (freeze/blank frame)** rather than hard crash.
- White screen is likely a runtime UI freeze condition triggered by repeated Save/Back/Sync navigation churn.
- Because no app crash stacktrace surfaced, root cause is likely in heavy synchronous work / blocking on UI path, not immediate fatal exception.

## Frequency Before/After
- Before (from user complaint): white-screen still recurring during Save->Sync->Back loop.
- This run: white-screen **reproduced** (>=1 occurrence within loop session).
- Therefore: issue is **not proven fixed**.

## Crash vs Freeze Classification
- **Crash stacktrace:** not observed.
- **Freeze/render stall:** observed (very high skipped-frame bursts).

## Suggested Next Fix Step (without changing sync core scope)
1. Add fine-grained timing logs around Save action -> navigation transitions (UI layer / ViewModel entry-exit).
2. Add strict-mode / main-thread block instrumentation for debug builds during repro script.
3. Capture `perfetto` or `atrace` slice for 10-20s around white-screen to identify exact blocking section.
4. Add debounce/guard on rapid Save+Back transitions to avoid overlapping UI state transitions.

## Verdict
- RCA status: **Reproduced**.
- Root cause confidence: **Medium** (runtime freeze symptoms are clear; exact blocking code path still needs targeted trace instrumentation).
- Functional status: **Improved not proven fixed**.
