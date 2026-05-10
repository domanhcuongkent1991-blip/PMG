# Manager Review - Agent 1 DMBT Mirror Delete Fix 2

Date: 2026-05-09
Scope reviewed:
- `DeviceLogRepositoryImpl.kt`
- `DeviceLogDao.kt`
- `DeviceLogRepositorySyncRulesTest.kt`

## Review Result

Status: PASS FOR CONTROLLED UAT.

The previous P1 blocker was addressed. `refreshFromRemote()` now collects remote-present IDs using the resolved local representative ID:

```kotlin
val representativeLocalId = local?.recordId ?: remoteLog.recordId
remotePresentLocalIdsBySheet.getOrPut(sheetId) { linkedSetOf() }.add(representativeLocalId)
```

The production mirror delete path now calls the safer overload:

```kotlin
mirrorDeleteSyncedRowsMissingFromRemoteSnapshot(
    deviceLogDao = deviceLogDao,
    remotePresentLocalIdsBySheet = remotePresentLocalIdsBySheet
)
```

This means legacy local rows that resolve from namespaced remote rows are treated as present and should not be deleted accidentally.

## Positive Findings

- Mirror delete remains scoped by `sourceSheetId`.
- Only `SYNCED` local rows are eligible for deletion.
- `PENDING` and `FAILED` rows are preserved.
- The prior legacy/local-id vs remote-namespaced-id risk is now covered by a new test path using `remotePresentLocalIdsBySheet`.
- Production path no longer uses raw remote record IDs for the post-merge delete decision.

## Verification

Targeted unit test:

```powershell
$env:ANDROID_USER_HOME=(Resolve-Path .\.android-home).Path; $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'; .\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.example.devicetracker.data.repository.DeviceLogRepositorySyncRulesTest
```

Result: PASS.

Debug build:

```powershell
$env:ANDROID_USER_HOME=(Resolve-Path .\.android-home).Path; $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'; .\gradlew.bat --no-daemon :app:assembleDebug
```

Result: PASS.

## Residual Risk / Recommendation

### [P3] Raw `remoteLogs` overload is still easy to misuse in future tests or code

There is still an overload of `mirrorDeleteSyncedRowsMissingFromRemoteSnapshot(deviceLogDao, remoteLogs)` that converts raw remote IDs directly. It is not used by production `refreshFromRemote()`, so this is not a release blocker, but future code could accidentally call the less-safe path.

Recommendation:
- Rename it to make it test-only/raw-id explicit, or remove it once old tests are adapted to the `remotePresentLocalIdsBySheet` overload.

## UAT Recommendation

Proceed to controlled real-device UAT only with a small, reversible scenario:
- Use a known sheet/year and delete 1-2 non-critical rows first, not 50 immediately.
- Run full sync.
- Confirm app count decreases by the same number.
- Confirm local `PENDING` rows are not deleted.
- Confirm no cross-year sheet loses rows.

Do not test large deletion batches until the 1-2 row UAT passes.
