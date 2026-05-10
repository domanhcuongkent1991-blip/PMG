# AGENT1 PROVENANCE BACKFILL FIX (2026-05-07)

## 1) File đã sửa
- `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImpl.kt`
- `android-mvp/app/src/test/java/com/example/devicetracker/data/repository/DeviceLogRepositorySyncRulesTest.kt`

## 2) Logic sửa
Vấn đề gốc:
- Luồng refresh chỉ gọi `buildMergedSyncedEntityFromRemote(...)` khi `shouldApplyRemoteLog(...) == true`.
- Với case legacy:
  - local `syncStatus=SYNCED`
  - local `sourceSheetId=null`
  - remote có `sourceSheetId` đúng
  - `remote.updatedAt=0`
  - content giống nhau
- Hàm cũ trả `false` (vì content không khác), nên provenance không được backfill.

Fix tối thiểu:
- Thêm nhánh ưu tiên trong `shouldApplyRemoteLog(...)`:
  - nếu `shouldBackfillSourceSheetIdOnly(currentLocal, remoteLog)` thì trả `true`.
- Thêm helper `shouldBackfillSourceSheetIdOnly(...)`:
  - chỉ `true` khi local `sourceSheetId == null` và remote `sourceSheetId != null`.
- Không thay đổi rule không đè provenance:
  - `buildMergedSyncedEntityFromRemote(...)` vẫn ưu tiên `currentLocal.sourceSheetId` nếu local đã có.
  - vì vậy local có source khác remote sẽ không bị ghi đè.

## 3) Test đã thêm/cập nhật
Fail-first test mới:
- `shouldApplyRemoteLog_allowsProvenanceOnlyBackfill_whenLegacyLocalHasNullSource`
  - tái hiện đúng case bug và yêu cầu backfill provenance-only.

Guard test mới:
- `shouldBackfillSourceSheetIdOnly_returnsFalse_whenLocalAlreadyHasSource`
  - đảm bảo không mở đường ghi đè provenance khi local đã có source.

## 4) Lệnh kiểm tra đã chạy
Fail-first trước fix:
- `./gradlew.bat -PcodexBuildId=agent1_fix_provenance_backfill_failfirst_20260507 :app:testDebugUnitTest --tests "com.example.devicetracker.data.repository.DeviceLogRepositorySyncRulesTest.shouldApplyRemoteLog_allowsProvenanceOnlyBackfill_whenLegacyLocalHasNullSource" --no-daemon --max-workers=1`
- Kết quả: `1 test completed, 1 failed`.

Sau fix:
- `./gradlew.bat -PcodexBuildId=agent1_fix_provenance_backfill_pass_20260507 :app:testDebugUnitTest --tests "com.example.devicetracker.data.repository.DeviceLogRepositorySyncRulesTest" --no-daemon --max-workers=1`
- Kết quả: PASS.

## 5) Rủi ro còn lại
- Đây là fix nhắm vào provenance backfill tại layer rule merge local/remote, chưa phải xác nhận E2E trên Google Sheet thật.
- Cần UAT controlled để xác nhận các row legacy thực tế (đặc biệt DMBT 2022-2025) đã được backfill `sourceSheetId` như kỳ vọng trong lần refresh kế tiếp.
