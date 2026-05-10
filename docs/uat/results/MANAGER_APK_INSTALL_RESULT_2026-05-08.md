# Manager APK Install Result (2026-05-08)

## Scope

- Install latest debug APK after Agent 1 `WS-FIX-02`.
- Verify package presence and launch on the connected Android device.

## Device

- Serial: `1a79dec0`
- Model: `RMX3081`
- Owner user: `0`

## APK

- Source APK: `F:\codex_android_gsheet_full_pack\android-mvp\.codex-build\1778254298285\app\outputs\apk\debug\app-debug.apk`
- SHA256: `48397E441603FAE4AC960C6E6B5E3B0CDEA075ED0B352557242B98EB9BA06D58`

## Commands And Results

- `adb -s 1a79dec0 install -r <apk>` -> `Success`
- `adb -s 1a79dec0 shell pm list packages | rg "com.example.devicetracker"` -> package present
- `adb -s 1a79dec0 shell cmd package resolve-activity --brief com.example.devicetracker` -> `com.example.devicetracker/.MainActivity`
- `adb -s 1a79dec0 shell am start -n com.example.devicetracker/.MainActivity` -> activity started
- `adb -s 1a79dec0 shell pidof com.example.devicetracker` -> `8054`
- `dumpsys activity activities` -> `mResumedActivity: ... com.example.devicetracker/.MainActivity ... U=0`

## Evidence

- Screenshot after launch: `docs/uat/evidence/installed-apk-check-2026-05-08/launch_after_install.png`

## Decision

- Install and launch verification: PASS.
- Runtime white-screen stability is not proven by this install step. It still requires the dedicated loop UAT.
