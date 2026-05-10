# Manager Review - Agent 1 DMBT Mirror Delete

Date: 2026-05-09
Scope reviewed:
- `DeviceLogRepositoryImpl.kt`
- `DeviceLogDao.kt`
- `DeviceLogRepositorySyncRulesTest.kt`

## Summary

Agent 1 implemented DMBT mirror delete after remote pull by comparing local record IDs under each `sourceSheetId` against remote record IDs from the pulled snapshot.

The implementation has useful safeguards:
- Only deletes local rows whose `syncStatus = SYNCED`.
- Keeps `PENDING` and `FAILED` rows.
- Scopes deletion by `sourceSheetId`.
- Runs after DMBT pull success, not after pull failure.
- Adds targeted tests for stale synced delete, pending/failed preservation, and cross-sheet preservation.

## Blocking Finding

### [P1] Mirror delete can delete matched legacy local rows when remote recordId format differs

File: `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImpl.kt`
Lines: around 263-283 and 734-768

`refreshFromRemote()` first resolves remote rows to existing local rows using `resolveExistingLocalForRemote()`. That resolver can match by business key when local legacy `recordId` differs from the remote namespaced `recordId`.

However, `mirrorDeleteSyncedRowsMissingFromRemoteSnapshot()` later builds `remoteRecordIds` directly from `remoteLog.recordId` and compares those raw IDs against local record IDs from Room.

Risk example:
- Local legacy row: `recordId = seed-beta-dmbt-2025-r16`, `sourceSheetId = 989601207`, `SYNCED`.
- Remote pulled row: `recordId = readonly-dmbt-989601207-seed-beta-dmbt-2025-r16`, same business data and same source sheet.
- Merge resolver can correctly match/update the local row by business key.
- Mirror delete then sees local id `seed-beta-dmbt-2025-r16` is not in remote id set `{readonly-dmbt-989601207-seed-beta-dmbt-2025-r16}` and deletes it.

This is directly dangerous because the project already has legacy/base-id and namespaced-id handling.

## Verification Run

Command:

```powershell
$env:ANDROID_USER_HOME=(Resolve-Path .\.android-home).Path; $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'; .\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.example.devicetracker.data.repository.DeviceLogRepositorySyncRulesTest
```

Result: PASS, but current tests do not cover the blocking legacy-id-vs-namespaced-id case.

## Decision

Status: BLOCK / CHUA NEN DUYET.

Agent 1 should add a failing test for the legacy local ID vs remote namespaced ID case and change mirror delete to compare against canonical/resolved local IDs, not raw remote IDs.

## Required Fix Direction

Safe direction:
- During refresh merge, collect the local record IDs that are resolved/applied/skipped as representing remote rows for each `sourceSheetId`.
- Pass those resolved local IDs into mirror delete.
- Mirror delete should compare local synced IDs against resolved remote-present local IDs, not raw `remoteLog.recordId` only.
- Add test proving legacy local row is not deleted when remote row is present with namespaced recordId.

Still required:
- Keep PENDING/FAILED preservation.
- Keep sourceSheetId scoping.
- Keep no-delete-on-pull-failure behavior.
