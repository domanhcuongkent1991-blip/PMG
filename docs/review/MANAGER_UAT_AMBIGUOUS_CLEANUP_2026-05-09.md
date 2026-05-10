# Manager UAT - Ambiguous Pending Cleanup

Date: 2026-05-09
Device: Realme RMX3081 (`1a79dec0`)
Package: `com.example.devicetracker`
Evidence folder: `docs/uat/evidence/ambiguous-cleanup-2026-05-09`

## Goal

Validate Agent 1's ambiguous pending cleanup on a real connected phone after the Director approved the next step.

Specific target:
- The historical dirty DMBT row `E2ELO998` must no longer block sync.
- Pending count and sync queue count should become 0 after using the cleanup/ignore action.
- The app must keep valid local data instead of deleting user data unexpectedly.

## Build And Install Evidence

- Built debug APK with the existing app signing home: `android-mvp/.android-home`.
- APK SHA256: `43EE45486CD2DBB800BF4832ED5BBB3EA4422C7B6BCF5AF754F35F192AF09C57`.
- APK signing fingerprint: `8124cd8faaecac25253f01e6a5de53ba8f53554c9351d5a6a532875737c81900`.
- Installed with `adb install -r` successfully.
- App launched with `adb shell monkey -p com.example.devicetracker -c android.intent.category.LAUNCHER 1`.
- Package `lastUpdateTime`: `2026-05-09 20:25:14`.

## Before Cleanup

Database evidence:

```text
dmbt-auto-e2elo998-25_06_2026-jsjsjjss-jsjsjjssjnsnsn|E2ELO998|1607125070|PENDING
9|dmbt-auto-e2elo998-25_06_2026-jsjsjjss-jsjsjjssjnsnsn|UPSERT_LOG|5|Ambiguous DMBT fallback key for push: 'e2elo998|25_06_2026|jsjsjjss|jsjsjjssjnsnsn'. Skip append/update to avoid duplicate.
PENDING|1
SYNCED|1853
```

UI evidence:
- Pending count showed 1.
- Sync queue count showed 1.
- Retry/error count showed 1.
- The app showed the action button `Bo qua loi ambiguous nay` for the dirty `E2ELO998` item.

## Cleanup Action

On the real phone, Manager tapped the cleanup/ignore action for the ambiguous `E2ELO998` item.

## After Cleanup

Database evidence:

```text
dmbt-auto-e2elo998-25_06_2026-jsjsjjss-jsjsjjssjnsnsn|E2ELO998|1607125070|FAILED
FAILED|1
SYNCED|1853
```

Sync queue query after cleanup returned no rows.

UI evidence:
- `Cho dong bo (PENDING) = 0`.
- DMBT pending = 0.
- HGT pending = 0.
- `So muc trong hang doi = 0`.
- `Muc co loi retry = 0`.

## Manager Review

### Pass

- The dirty ambiguous row no longer remains `PENDING`.
- The sync queue was cleared.
- The remaining dirty local row was retained as `FAILED`, which is safer than deleting historical data silently.
- The cleanup target is therefore functionally met for controlled UAT.

### Residual Risk

- UX risk remains: the `FAILED` row can still appear under the screen section currently named like pending/waiting sync, with wording such as waiting to push to Google Sheet.
- This does not appear to be a sync-core blocker because queue count and pending count are already 0.
- However, it can confuse non-technical users into thinking old data is still blocking sync.

## Conclusion

Status: APPROVE FOR CONTROLLED UAT, NOT FINAL UX CLEANUP.

Recommended next step:
- Small UI-only fix in Sync Status screen/ViewModel: display ignored/FAILED ambiguous rows in a separate explanatory state or hide them from the pending section when queue count is already 0.
- Do not modify sync core unless a new data-level blocker appears.

## Follow-up UX Patch

After the first UAT pass, Manager found a UX-only issue: local rows marked `FAILED` after safe ignore were no longer pending and no longer queued, but could still appear under `Thiet bi cho dong bo` because the DAO list used `syncStatus != 'SYNCED'`.

Patch applied:
- `DeviceLogDao.getPendingLogs()` now returns only `syncStatus = 'PENDING'`.
- `HgtCheckDao.getPendingChecks()` now returns only `syncStatus = 'PENDING'`.
- The unit-test fake DAO was aligned with the production pending definition.

This keeps ignored/FAILED historical data in the local database, but stops presenting it as active pending sync work.

## Follow-up Verification

Commands:
- `node --version` -> `v24.12.0`
- `npm --version` -> `11.6.2`
- `npx --version` -> `11.6.2`
- `git --version` -> `git version 2.53.0.windows.1`
- `./gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.example.devicetracker.data.repository.DeviceLogRepositorySyncRulesTest` -> PASS
- `./gradlew.bat --no-daemon :app:assembleDebug` -> PASS
- `adb -s 1a79dec0 install -r android-mvp/app/build/outputs/apk/debug/app-debug.apk` -> Success

APK SHA256 after UX patch:

```text
1A2F053CF7E05AFAE3877B90386AF74FA56E0F65B42DBCDC261BA068EA729038
```

Real-phone UI after install:
- `Cho dong bo (PENDING) = 0`
- DMBT pending = 0
- HGT pending = 0
- `So muc trong hang doi = 0`
- `Muc co loi retry = 0`
- `Thiet bi cho dong bo` section now shows `Khong co thiet bi cho dong bo`.

Additional evidence:
- `docs/uat/evidence/ambiguous-cleanup-2026-05-09/after_ui_pending_empty_screen.png`
- `docs/uat/evidence/ambiguous-cleanup-2026-05-09/after_ui_pending_empty.xml`

## Updated Conclusion

Status: APPROVE FOR CONTROLLED UAT.

The ambiguous dirty row is no longer active pending work, the queue is empty, and the Sync Status UI no longer presents the ignored/FAILED row as waiting to sync.
