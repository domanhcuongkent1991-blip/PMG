# AGENT1 MONTHLY SEPARATION DESIGN - 2026-05-06

## 1. Executive Summary
- Co the tach monthly theo huong giong HGT (flow rieng, loi rieng, retry rieng), va day la huong dung de giam lap lai bug duplicate/sai sheet.
- Monthly hien dang tron vao flow DMBT chung o ca pull, merge, push, queue va UI. Vi vay loi monthly co the lam full sync DMBT that bai.
- De xuat chinh: chon **Phuong an A theo lo trinh an toan** (dung chung `device_logs`, provenance ro rang theo `sourceSheetId` + source group, tach repository/remote flow yearly vs monthly), **khong tao bang moi trong phase dau**.
- Khong de monthly fail lam fail full sync DMBT nam: yearly la mandatory, monthly la optional-with-warning.

## 2. Monthly hien dang tron vao DMBT nam nhu the nao
1. Tron o config sheet bindings:
- `SheetConfig.dmbtSheetBindings` gom tat ca DMBT IDs (nam + thang) trong cung 1 danh sach (`SheetConfig.kt`, `dmbtSheetBindings`).
- `dmbtDefaultCreateSheetId` cung chung cho tat ca (`SheetConfig.kt`).

2. Tron o remote pull/push:
- `pullLatestLogs()` lap qua toan bo `dmbtPullTargets(sheetConfig.dmbtSheetBindings)` (`SheetsRemoteDataSource.kt:144, 155, 1304`).
- `pushLogs()` group theo target sheet cung 1 ham `groupDmbtLogsByTargetSheet` (`SheetsRemoteDataSource.kt:45, 58, 1275`).

3. Tron o repository merge:
- `DeviceLogRepositoryImpl.refreshFromRemote()` coi ket qua `pullLatestLogs()` la 1 tap DMBT duy nhat (`DeviceLogRepositoryImpl.kt:163-215`).
- Merge fallback co the match business key neu identity khong ro (`DeviceLogRepositoryImpl.kt:438-502`).

4. Tron o repair:
- `mergeRepairLogsFromRemote()` merge vao `device_logs` chung, khong phan tach monthly/yearly stream (`DeviceLogRepositoryImpl.kt:242-315`).

5. Tron o UI/filter/sidebar:
- `CategoryFilterMapper.classifySource()` phan loai monthly dua tren pattern `recordId/hangMuc`, khong dua tren gid/sourceSheetId (`CategoryFilterMapper.kt`, `classifySource`).
- Sidebar monthly section dung category mapper nay (`SearchScreen.kt` cac section monthly).

## 3. Vi sao HGT on dinh hon
1. HGT co repository rieng:
- `HgtCheckRepositoryImpl` co `syncPending()` va `refreshFromRemote()` rieng, khong chung voi DMBT (`HgtCheckRepositoryImpl.kt:93, 141`).

2. HGT co sheet role rieng:
- Route theo `SheetRole.HGT_CHECKS` 1 gid ro rang (`SheetsRemoteDataSource.kt:257, 332`).

3. HGT key don gian hon:
- Match theo `record_id` roi fallback `ma_thiet_bi` (`parseHgtSchema/findExistingRow` trong `SheetsRemoteDataSource.kt`).

4. Loi HGT khong can merge voi DMBT:
- Khong co lop repair merge cheo domain nhu DMBT.

5. Nhieu guard da tach biet:
- PENDING/FAILED guard, updatedAt fallback, queue operation rieng `OP_UPSERT_HGT/OP_DELETE_HGT` (`HgtCheckRepositoryImpl.kt`).

## 4. So sanh 3 phuong an

| Phuong an | Mo ta | Uu diem | Nhuoc diem | Muc an toan du lieu | Do kho rollout |
|---|---|---|---|---|---|
| A | Dung chung `device_logs`, provenance bat buoc (`sourceSheetId`) + source group ro rang (yearly/monthly/repair), tach flow yearly vs monthly | Khong nhan doi du lieu, khong can copy UI/list, giu local-first hien tai, de phase hoa | Can harden merge/router ky; can bo sung gate de chan row thieu provenance | Cao neu enforce gate dung | Trung binh |
| B | Tao bang rieng `monthly_device_logs` | Cach ly domain ro nhat, kho duplicate cheo bang | Rui ro migration cao, phuc tap dong bo UI/queue/repair, can hop nhat ket qua 2 bang | Cao sau khi on dinh | Cao |
| C | Khong tach DB, chi tach repository/remote flow | Nhanh nhat, it thay doi | Van de provenance/UI heuristic van con; duplicate cheo domain van de xay ra | Trung binh | Thap-Trung binh |

Ket luan so sanh:
- B rat sach ve kien truc nhung migration risk cao cho du lieu that.
- C nhanh nhung chua xu ly goc identity/provenance.
- A can bang tot nhat cho release an toan: tach flow + provenance strict, chua can doi schema phase dau.

## 5. Phuong an Agent de xuat
De xuat: **A (shared `device_logs` + provenance strict) ket hop tach flow theo kieu HGT**.

Nguyen tac:
1. Tach 3 stream doc lap:
- DMBT_YEARLY (2022-2026)
- DMBT_MONTHLY (T5.2026 gid 1383308512)
- MONTHLY_REPAIR (Sua chua T5.2026 gid 157327514)

2. Khong cho merge cheo stream:
- Yearly row khong duoc merge boi monthly row chi vi business key giong.
- Repair monthly chi cap nhat monthly DMBT row, khong cap nhat yearly row.

3. Push route theo gid/sourceSheetId bat buoc:
- Row nao khong resolve duoc source gid thi khong push, giu queue + bao warning ro.

4. Loi monthly khong duoc lam fail full sync yearly.

## 6. File can sua theo phuong an de xuat
1. `android-mvp/app/src/main/java/com/example/devicetracker/data/sheet/SheetConfig.kt`
- Tach binding config thanh yearly IDs va monthly IDs.
- Them feature flags/optional mapping cho monthly.

2. `android-mvp/app/src/main/java/com/example/devicetracker/data/sheet/SheetContract.kt`
- Mo rong role contract: yearly DMBT va monthly DMBT/repair optional theo mode.

3. `android-mvp/app/src/main/java/com/example/devicetracker/data/remote/SheetsRemoteDataSource.kt`
- Tach ham pull/push theo stream:
- `pullYearlyLogs`, `pullMonthlyLogs`, `pullMonthlyRepairLogs`.
- `pushYearlyLogs`, `pushMonthlyLogs`.
- Khi monthly gid missing: warning + skip, khong throw fail cho yearly full sync.

4. `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImpl.kt`
- Doi orchestration `refreshFromRemote` theo multi-stream result.
- Repair merge gioi han scope chi monthly stream.

5. `android-mvp/app/src/main/java/com/example/devicetracker/data/local/dao/DeviceLogDao.kt`
- Them query helper theo `sourceSheetId IN (...)` de phan stream ro rang.

6. `android-mvp/app/src/main/java/com/example/devicetracker/ui/search/CategoryFilterMapper.kt`
- Bo heuristic theo text/record prefix cho monthly.
- Chuyen qua phan loai dua tren `sourceSheetId` va mapping gid.

7. `android-mvp/app/src/main/java/com/example/devicetracker/ui/search/SearchScreen.kt`
- Sidebar monthly section chi hien khi monthly stream enable/co du lieu monthly hop le.

8. Test files lien quan:
- `.../data/remote/SheetsRemoteDataSourceRecordIdTest.kt`
- `.../data/repository/DeviceLogRepositorySyncRulesTest.kt`
- `.../ui/search/CategoryFilterMapperTest.kt`
- Them test moi cho isolation yearly/monthly/repair.

## 7. Database/schema co can doi khong
Ket luan de xuat:
1. **Phase dau: KHONG doi schema DB**.
- Dung chung `device_logs`.
- Dua vao `sourceSheetId` + stream resolver theo gid de tach logic.

2. **Phase sau (optional): co the them cot `sourceGroup/sourceType`** neu can telemetry va query de hon.
- Day la schema migration additive (co risk): can migration script + backfill.
- Chua can bat buoc de giai quyet P0 truoc mat.

Migration risk neu doi schema ngay:
- Roi row legacy `sourceSheetId=null` can backfill truoc khi rely vao sourceType.
- Sai migration co the gay mat tham chieu queue/record.

## 8. Sync queue co can doi khong
1. Khong can doi bang queue trong phase dau.
- Tiep tuc dung `sync_queue` hien tai.

2. Can doi logic xu ly queue:
- Tach candidate logs theo stream truoc khi push.
- Push yearly va monthly thanh 2 batch doc lap.
- Neu monthly batch fail: queue monthly giu lai; yearly batch van hoan tat neu thanh cong.

3. Co the bo sung operation label de de chuan doan:
- `UPSERT_LOG_YEARLY`, `UPSERT_LOG_MONTHLY` (khong doi schema, chi them enum-string usage).

## 9. Cach xu ly DMBT T5.2026
1. DMBT T5.2026 (gid 1383308512) duoc coi la stream MONTHLY_DMBT rieng.
2. Khong nam trong yearly pull targets.
3. Pull monthly fail -> warning, khong fail yearly full sync.
4. Push monthly chi nhan records co provenance monthly hop le (`sourceSheetId=1383308512` hoac namespace gid monthly).

## 10. Cach xu ly Sua chua T5.2026
1. Sua chua T5.2026 (gid 157327514) la stream MONTHLY_REPAIR (event log) phu tro cho monthly DMBT.
2. Rule merge:
- Merge vao monthly DMBT row qua `record_id` exact truoc.
- Neu fallback thi chi tim trong monthly group; khong tim sang yearly group.
3. Khong tao DMBT row moi tu repair sheet.
4. Neu repair row ambiguous/not-found: skip + telemetry ly do.

## 11. Cach tranh duplicate giua DMBT nam va DMBT thang
1. Partition ngay tu config:
- Yearly gids va monthly gids la 2 tap roi nhau.

2. Partition ngay tu merge rule:
- `resolveExistingLocalForRemote` chi xet candidates trong cung stream.
- Cam business-key merge cheo stream.

3. Namespace strategy:
- Uu tien namespace recordId theo gid cho monthly records de tranh collision base-id.

4. Push idempotency:
- Match row truoc bang `record_id` da resolve theo gid.
- Fallback business key chi trong cung sheet/stream.

5. UI category:
- Phan loai theo sourceSheetId, khong theo `hangMuc` text hoac seed prefix.

## 12. Cach de monthly loi khong lam DMBT nam fail
Trang thai hien tai:
- `refreshFromRemote()` fail neu DMBT pull fail hoac repair merge fail (`DeviceLogRepositoryImpl.kt:178-223`).

De xuat moi:
1. Full sync chia ket qua:
- Yearly result (mandatory)
- Monthly DMBT result (optional)
- Monthly Repair result (optional)

2. Rule tra ket qua:
- Neu yearly fail -> full fail.
- Neu yearly pass, monthly fail -> full success with warning (co latestError/pending item hien thi ro).

3. Canh bao UI:
- Sync status phai hien "Monthly stream failed, yearly stream ok".

## 13. Test can them
1. Pull yearly khong doc gid 1383308512.
2. Pull monthly doc dung gid 1383308512 va khong chen vao yearly group.
3. Pull monthly repair gid 157327514 merge dung monthly row.
4. Monthly repair khong duoc merge vao yearly row du business key giong.
5. Monthly pull fail (missing gid/title) -> yearly sync van pass.
6. Push yearly khong bao gio route vao monthly gid.
7. Push monthly khong bao gio route vao yearly gid.
8. Retry push monthly khong append duplicate.
9. Category filter monthly/yearly dua tren sourceSheetId mapping, khong phu thuoc `T4/T5` text.
10. Legacy `sourceSheetId=null` record bi chan push neu khong resolve duoc stream.

## 14. UAT can user kiem tra
1. Yearly sheet update -> app:
- Sua row tren DMBT 2025, sync, app cap nhat dung 1 row.

2. App -> yearly sheet:
- Sua 1 row 2025 tren app, sync, sheet 2025 update dung row.

3. Monthly sheet update -> app:
- Sua 1 row tren DMBT T5.2026, sync, app cap nhat dung row monthly.

4. Repair monthly -> monthly DMBT:
- Them 1 dong marker tren Sua chua T5.2026, sync, app cap nhat dung row monthly.

5. Isolation check:
- Xac nhan thao tac monthly khong tao them/sua row trong yearly result list.

6. Fault isolation check:
- Tam thoi vo hieu hoa monthly gid (hoac doi ten tab monthly) va sync.
- Ket qua mong doi: yearly sync van OK, app bao warning monthly.

7. Repeat sync 2-3 lan:
- Khong duplicate tren app va sheet.

## 15. Rui ro va rollback
Rui ro chinh:
1. Partition rule qua chat co the lam skip nhieu row legacy (`sourceSheetId=null`).
2. UI category doi sang sourceSheetId co the khac ket qua cu, user thay "mat" mot so item monthly neu provenance chua du.
3. Optional monthly mode neu khai bao khong ro co the gay confusion UAT.

Rollback plan:
1. Feature-flag monthly separation, cho phep quay ve flow cu ngay lap tuc.
2. Khong doi schema phase dau nen rollback code don gian (revert app logic).
3. Giu dry-run report va log skip reason de phuc hoi quyet dinh merge/push.
4. UAT rollback: xoa test markers tren 2 sheet monthly va sync lai xac nhan marker khong con.

## 16. Ket luan: nen code phase nao truoc
Thu tu phase de xuat:
1. **Phase 1 (bat buoc): Tach orchestration yearly vs monthly + fault isolation**
- Muc tieu: monthly fail khong danh sap yearly full sync.

2. **Phase 2 (bat buoc): Partition merge/router theo stream + provenance gate**
- Muc tieu: chan duplicate cheo yearly-monthly.

3. **Phase 3 (quan trong): UI/category doi sang sourceSheetId-based classification**
- Muc tieu: bo heuristic prefix/text gay sai nhom.

4. **Phase 4 (optional): tang telemetry + operation label queue**
- Muc tieu: de triage nhanh tren du lieu that.

5. **Phase 5 (optional, sau on dinh): xem xet schema additive `sourceType`**
- Chi lam khi can query/bao cao tot hon va da co migration plan an toan.

Ket luan cuoi:
- Khong nen tao bang moi monthly ngay luc nay.
- Nen tach monthly thanh flow/module rieng nhu HGT ve mat orchestration, nhung van dung chung `device_logs` trong phase dau de giam migration risk.
- Dieu kien an toan quan trong nhat la provenance theo gid/sourceSheetId va khong merge cheo stream.
