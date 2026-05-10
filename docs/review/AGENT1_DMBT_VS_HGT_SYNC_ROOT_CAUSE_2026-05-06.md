# AGENT1 DMBT vs HGT Sync Root Cause - 2026-05-06

## 1. Executive Summary
- Ket luan chinh: DMBT/Sua chua chua on dinh 2 chieu khong phai vi 1 bug don le, ma do to hop 3 van de he thong: (1) danh tinh record multi-sheet phuc tap hon HGT, (2) du lieu legacy DMBT co nhieu row `sourceSheetId = null`, (3) pull/merge phai dung rule an toan de tranh merge nham nen co the skip update hop le trong mot so case.
- HGT nhin on dinh hon vi flow don gian hon: 1 role chinh, 1 sheet chinh, merge key gon (`record_id`/`ma_thiet_bi`), va push/pull idempotent de dat duoc trong da so case binh thuong.
- Co the dung HGT lam nen tang cho DMBT/Sua chua, nhung chi tai cac pattern chung (guard PENDING/FAILED, updatedAt fallback, gid routing, update-truoc-append). Khong the copy-nguyen xi vi DMBT co multi-sheet + repair-sheet + business key conflict phuc tap hon.
- Root cause P0 hien tai tap trung vao: provenance `sourceSheetId` cua DMBT legacy, identity matching giua recordId namespaced/non-namespaced, va push routing khi record khong xac dinh duoc sheet dich.

## 2. Luong HGT hien tai

### 2.1 Pull tu Sheet ve app
1. `HgtCheckRepositoryImpl.refreshFromRemote()` goi `remoteDataSource.pullHgtChecks()` voi timeout (`HgtCheckRepositoryImpl.kt:141`).
2. `pullHgtChecks()` route theo `sheetId` cua role `HGT_CHECKS` (`SheetsRemoteDataSource.kt:333`).
3. Sheet title duoc resolve bang metadata theo `gid/sheetId`, khong hard-code ten tab (`SheetsRemoteDataSource.kt:339`).
4. Parse schema HGT co alias header + duplicate header check (`SheetsRemoteDataSource.kt:790`).
5. Row HGT khong hop le bi skip an toan (blank ma, chu ky khong hop le, ngay khong hop le) trong pull (`SheetsRemoteDataSource.kt:343`).

### 2.2 Merge local
1. Merge uu tien theo `id`; neu khong co thi fallback theo `ma_thiet_bi` normalized (`HgtCheckRepositoryImpl.kt:159`).
2. Rule ap remote: 
- local null -> apply
- local PENDING/FAILED (khac `SYNCED`) -> khong apply
- remote `updatedAt <= 0` -> so sanh noi dung thuc te
- nguoc lai -> remote phai `>= local.updatedAt`
(`HgtCheckRepositoryImpl.kt:247`).

### 2.3 Push tu app len Sheet
1. `syncPending()` gom upsert/delete queue HGT (`HgtCheckRepositoryImpl.kt:93`).
2. `pushHgtChecks()` tim row cu theo `record_id` hoac `ma_thiet_bi`; co row thi update, khong co thi append (`SheetsRemoteDataSource.kt:257`, `SheetsRemoteDataSource.kt:284`, `SheetsRemoteDataSource.kt:1166`).
3. Delete theo `ma_thiet_bi` map (`SheetsRemoteDataSource.kt:312`).

### 2.4 Xu ly updatedAt thieu hoac = 0
- Pull merge da co fallback content-diff neu `updatedAt <= 0` (`HgtCheckRepositoryImpl.kt:250`).
- Khi ghi local tu remote ma `updatedAt <= 0`, code dat `System.currentTimeMillis()` de tranh 0-keo-dai (`HgtCheckRepositoryImpl.kt:170`).

### 2.5 Chong duplicate
- Push idempotency dua vao row match (`record_id` truoc, roi `ma_thiet_bi`) (`SheetsRemoteDataSource.kt:1166`).
- Rui ro con lai: parser schema map `rowByDeviceCode` la map 1-1, neu sheet co nhieu row cung `ma_thiet_bi` thi row sau ghi de row truoc (`SheetsRemoteDataSource.kt:853`) -> giai thich duoc count mismatch HGT da duoc ghi nhan trong review (`docs/review/AGENT2_COUNT_MISMATCH_ANALYSIS_2026-05-05.md:23-27`).

### 2.6 Bao ve PENDING/FAILED
- Pull khong ghi de local neu local chua `SYNCED` (`HgtCheckRepositoryImpl.kt:249`).
- Push chi mark `SYNCED` khi `updatedAt` local van trung ban da day (`HgtCheckRepositoryImpl.kt:242`).

## 3. Luong DMBT/Sua chua hien tai

### 3.1 Pull nhieu sheet DMBT
1. `DeviceLogRepositoryImpl.refreshFromRemote()` goi `pullLatestLogs()` (`DeviceLogRepositoryImpl.kt:163`).
2. `pullLatestLogs()` lay danh sach target tu `dmbtPullTargets(dmbtSheetBindings)` (`SheetsRemoteDataSource.kt:155`, `SheetsRemoteDataSource.kt:1304`).
3. Moi sheet duoc resolve title theo gid tu metadata (khong phu thuoc ten tab T4/T5) (`SheetsRemoteDataSource.kt:163`).
4. Row tu sheet non-default duoc namespace recordId `readonly-dmbt-<gid>-...`, dong thoi set `sourceSheetId = gid` (`SheetsRemoteDataSource.kt:181`, `SheetsRemoteDataSource.kt:1260`).

### 3.2 Merge local theo recordId/sourceSheetId/business key
1. Tim local theo thu tu an toan:
- exact `recordId`
- `sourceSheetId + ma_thiet_bi` candidates roi loc business key
- fallback `ma_thiet_bi` candidates co `sourceSheetId null` (neu remote co sourceSheetId)
(`DeviceLogRepositoryImpl.kt:438-466`).
2. Business key gom: `ma_thiet_bi + ngay_phat_hien(normalize) + hang_muc + tinh_trang_thiet_bi` (`DeviceLogRepositoryImpl.kt:502`).
3. Chi merge khi unique match; neu mo ho -> tra null de tranh merge nham (`DeviceLogRepositoryImpl.kt:471`).
4. Khi merge thanh cong vao local legacy, `sourceSheetId` duoc backfill theo remote (`DeviceLogRepositoryImpl.kt:198-205`).

### 3.3 Pull sheet Sua chua thang
1. Sau pull DMBT, repo tiep tuc `mergeRepairLogsFromRemote()` (`DeviceLogRepositoryImpl.kt:219`, `DeviceLogRepositoryImpl.kt:242`).
2. `pullRepairLogs()` route theo role `DMBT_REPAIR_LOG` (gid repair) (`SheetsRemoteDataSource.kt:1201`).
3. Repair row merge vao DMBT local qua `RepairRecordIdentityResolver` (exact -> strip namespace hop le -> unique match) (`DeviceLogRepositoryImpl.kt:266`, `RepairRecordIdentityResolver.kt`).
4. Khong tao DMBT moi tu repair log; chi update field repair cua row da co (`DeviceLogRepositoryImpl.kt:240`, `DeviceLogRepositoryImpl.kt:306`).

### 3.4 Push tu app len dung sheet (route by sourceSheetId/gid)
1. `pushLogs()` group logs theo target sheet (`SheetsRemoteDataSource.kt:58`, `SheetsRemoteDataSource.kt:1275`).
2. Thu tu route:
- `sourceSheetId` neu co
- gid trich tu `readonly-dmbt-<gid>-...` neu co
- fallback default create sheet chi cho record auto an toan (`dmbt-auto-*`) khi multi-sheet
(`SheetsRemoteDataSource.kt:1283-1292`, `SheetsRemoteDataSource.kt:1341`, `SheetsRemoteDataSource.kt:1346`).
3. Neu khong resolve duoc target sheet -> fail an toan, khong ghi nham (`SheetsRemoteDataSource.kt:65`).

### 3.5 Xu ly updatedAt thieu hoac = 0
- DMBT pull merge: neu `remote.updatedAt <= 0` dung content-diff de quyet dinh apply (`DeviceLogRepositoryImpl.kt:431-435`, `DeviceLogRepositoryImpl.kt:547`).
- Repair merge: neu `repair.updatedAt <= 0` dung content-diff (`DeviceLogRepositoryImpl.kt:538-539`, `DeviceLogRepositoryImpl.kt:559`).

### 3.6 Chong duplicate
- Khi push, co `dedupeDmbtLogsForPush()` tren `sheetRecordId` de giu ban moi nhat (`SheetsRemoteDataSource.kt:1313`).
- Match row tren sheet uu tien `record_id`, fallback business key (`SheetsRemoteDataSource.kt:1125`).
- Rui ro duplicate van xay ra neu identity drift (recordId doi format/khong unique business key) lam cho pull khong resolve duoc local cu -> tao row local moi (`DeviceLogRepositoryImpl.kt:438-466` + `refreshFromRemote()` ap dung local null la row moi tai `DeviceLogRepositoryImpl.kt:195-206`).

### 3.7 Bao ve PENDING/FAILED
- Pull DMBT khong ghi de local neu local khong phai `SYNCED` (`DeviceLogRepositoryImpl.kt:433`).
- Pull repair khong ghi de local neu local dang PENDING/FAILED (`DeviceLogRepositoryImpl.kt:534`).
- Push mark SYNCED chi khi `updatedAt` local van khop ban da day (`DeviceLogRepositoryImpl.kt:426`).

## 4. Bang so sanh HGT vs DMBT

| Tieu chi | HGT | DMBT/Sua chua |
|---|---|---|
| Key dinh danh | `record_id`, fallback `ma_thiet_bi` | `record_id` + `sourceSheetId` + business key 4 truong |
| So luong sheet | Thuong 1 sheet role HGT | Nhieu sheet DMBT (2022-2026 + thang) + 1 sheet repair |
| Doi ten tab co anh huong? | Khong, route theo gid + metadata title | Khong neu gid dung; yeu cau binding day du cho tat ca gid |
| sourceSheetId | Khong can cho HGT domain | Bat buoc de route push on dinh multi-sheet |
| recordId namespaced | Khong can namespace | Co namespace `readonly-dmbt-<gid>-...` cho sheet non-default |
| Conflict rule | Nhe hon, id/device | Chat hon: unique business key + sourceSheet guard |
| updatedAt rule | Co content-diff fallback khi <=0 | Co content-diff fallback cho DMBT va repair |
| Duplicate prevention | update-neu-match, append-neu-khong | dedupe truoc push + row match recordId/fallback key |
| Tolerance schema/header | parse alias + duplicate header check | parse alias + dynamic header detect + duplicate header check |
| app -> sheet routing | 1 gid HGT role | group theo sourceSheetId/gid; fallback default bi gioi han an toan |
| sheet -> app merge | id/device fallback | exact recordId -> sourceSheet+business key -> fallback null-source |

## 5. Root Cause Findings

### P0 (release-blocking / rat kha nghi)
1. Legacy DMBT row co `sourceSheetId = null` lam mat provenance sheet dich.
- Evidence user/UAT: `sourceSheetId null = 1869 rows`, case `463KL01`, `523RF03` deu null (`docs/uat/results/USER_UAT_DMBT_2025_SYNC_RESULT_2026-05-05.md:67-83`).
- Tac dong: push khong route chac chan ve dung tab nam/thang; pull kho backfill neu khong unique match.
- Code lien quan: route target va fail-safe unresolved (`SheetsRemoteDataSource.kt:65`, `SheetsRemoteDataSource.kt:1275-1298`), merge/backfill (`DeviceLogRepositoryImpl.kt:198-205`, `DeviceLogRepositoryImpl.kt:438-466`).

2. Identity drift giua `record_id` sheet va local (namespaced/non-namespaced/legacy) + ambiguity business key.
- Tac dong: pull co the khong resolve duoc local cu, khi do `currentLocal == null` se upsert row moi -> duplicate local card.
- Evidence UAT user: duplicate `473CV03`, `473RB02` (UAT-06/UAT-09) (`docs/uat/results/USER_UAT_RESULT_2026-05-05.md:41-44`).
- Code lien quan: resolve local theo exact + unique business key; neu khong unique tra null (`DeviceLogRepositoryImpl.kt:438-466`, `DeviceLogRepositoryImpl.kt:471`), sau do refresh coi nhu row moi (`DeviceLogRepositoryImpl.kt:195-206`).

3. Push idempotency cua DMBT phu thuoc manh vao match row cu; neu key fallback drift co the append dong moi tren sheet.
- Tac dong: bam sync lap lai van co the sinh dong trung tren sheet (UAT-09).
- Code lien quan: `findExistingRow(record_id -> fallback key)` (`SheetsRemoteDataSource.kt:106`, `SheetsRemoteDataSource.kt:1125`), append neu khong match (`SheetsRemoteDataSource.kt:122`).

### P1 (co the gay lech du lieu)
1. Repair merge co chu truong skip an toan khi not-found/ambiguous -> co the bo sot cap nhat tu sheet repair.
- Code lien quan: resolver va skip ambiguous (`DeviceLogRepositoryImpl.kt:279-283`, `RepairRecordIdentityResolver.kt`).

2. Validate structure hien chi bat buoc role DMBT_LOG (`requiredRolesForSync`), khong ep repair/HGT role trong preflight chung.
- Tac dong: co the tao trang thai "khong loi cau hinh" o buoc validate chung nhung thuc te role repair/HGT chua map day du.
- Code lien quan: `SheetContract.requiredRolesForSync` (`SheetContract.kt:68`), validate su dung tap nay (`SheetsRemoteDataSource.kt:402`).

### P2 (diagnostic/UX)
1. Telemetry skip reason chua tong hop theo ma tran ro rang cho user non-tech (du da co log tung row).
2. HGT count mismatch co the do row invalid/duplicate ma_thiet_bi bi collapse trong parser map, can canh bao ro trong UI/bao cao (`docs/review/AGENT2_COUNT_MISMATCH_ANALYSIS_2026-05-05.md:23-27`, `SheetsRemoteDataSource.kt:853`).

## 6. Co the dung HGT lam nen tang khong?
- Co, nhung chi dung mot phan.

### Co the tai su dung truc tiep
1. Guard bao ve du lieu local PENDING/FAILED truoc khi apply remote (`HgtCheckRepositoryImpl.kt:247`, `DeviceLogRepositoryImpl.kt:431`).
2. Rule `updatedAt <= 0` thi fallback content-diff (`HgtCheckRepositoryImpl.kt:250`, `DeviceLogRepositoryImpl.kt:434`, `DeviceLogRepositoryImpl.kt:538`).
3. Route bang gid + metadata title thay vi ten tab (`SheetsRemoteDataSource.kt:163`, `SheetsRemoteDataSource.kt:339`).
4. Update-truoc-append + mark synced chi khi updatedAt khop (`HgtCheckRepositoryImpl.kt:242`, `DeviceLogRepositoryImpl.kt:426`).

### Khong the copy-nguyen xi
1. HGT khong co bai toan multi-sheet provenance nhu DMBT.
2. DMBT can them layer resolve `sourceSheetId` + namespace + business key 4 truong de tranh merge nham.
3. DMBT con co sheet repair tach rieng, can resolver an toan khong tao record moi.

## 7. Phuong an fix triet de (an toan, it loi)

### Buoc 1 - Lam sach provenance cho DMBT (uu tien cao nhat)
- Sua gi: harden merge/backfill de moi row DMBT match hop le deu duoc set `sourceSheetId` ngay khi pull.
- File du kien: `DeviceLogRepositoryImpl.kt`, `DeviceLogDao.kt`.
- Test them: pull tu tung gid (2022/2025/thang) voi local null-source phai backfill dung gid.
- Rui ro: backfill nham neu business key mo ho.
- Rollback: bat co merge-safe flag + chi apply khi unique; neu mismatch tang thi tat patch va giu rule skip.

### Buoc 2 - Chot identity rule de tri duplicate pull
- Sua gi: tang rang buoc identity khi local da co `sourceSheetId` khac remote thi khong merge, va khong tao row moi neu phat hien ambiguity ma co dau hieu trung issue.
- File du kien: `DeviceLogRepositoryImpl.kt`.
- Test them: pull cung row 2 lan voi recordId format khac nhau khong tao duplicate.
- Rui ro: skip qua nhieu row hop le.
- Rollback: fallback ve rule hien tai nhung giu log ambiguity de dieu tra.

### Buoc 3 - Khoa push routing/idempotency theo gid
- Sua gi: neu record khong resolve duoc target gid thi khong chan toan bo batch; tach record loi + giu queue record loi, van day record an toan.
- File du kien: `SheetsRemoteDataSource.kt`, `DeviceLogRepositoryImpl.kt`.
- Test them: retry push cung record khong append dong trung; record co `sourceSheetId=989601207` phai vao DMBT 2025, khong vao default.
- Rui ro: tang do phuc tap xu ly queue partial.
- Rollback: giu co che fail-fast toan batch neu phat hien loi moi.

### Buoc 4 - On dinh merge repair
- Sua gi: bo sung guard de reject repair update neu resolver ambiguous, dong thoi tang thong ke skip reason de UAT doc duoc ngay.
- File du kien: `DeviceLogRepositoryImpl.kt`, `RepairRecordIdentityResolver.kt` (neu can), `SheetsRemoteDataSource.kt`.
- Test them: pull repair cho record namespaced/exact/ambiguous.
- Rui ro: bo sot cap nhat repair neu data sheet xau.
- Rollback: tiep tuc skip-safe, khong cho phep write nham.

### Buoc 5 - Validation/config guard cho full role
- Sua gi: mo rong `requiredRolesForSync` hoac them validation rieng cho mode DMBT+Repair+HGT truoc full sync.
- File du kien: `SheetContract.kt`, `SheetsRemoteDataSource.kt`, test mapping.
- Rui ro: fail som hon tren moi truong cau hinh thieu.
- Rollback: h? scope validation ve canh bao (warning) neu can release gap.

## 8. Test can them
1. Pull DMBT 2022 (`gid=849979183`) match local `sourceSheetId=null` -> backfill 849979183.
2. Pull DMBT 2025 (`gid=989601207`) match local null-source -> backfill 989601207.
3. Pull DMBT thang (`gid=1383308512`) match local null-source -> backfill 1383308512.
4. Pull voi local da co `sourceSheetId` khac remote, business key giong -> khong merge nham.
5. Case `463KL01` ngay `07/01/2025` pull tu DMBT 2025 co ngay sua -> update dung row cu, khong tao row moi.
6. Push record `sourceSheetId=989601207` -> ghi DMBT 2025, khong ghi default.
7. Push retry cung record 2 lan -> khong append dong trung tren sheet.
8. Repair merge exact namespaced/base id -> update dung local row.
9. Repair merge ambiguous -> skip an toan + co skip reason ro rang.
10. Test tab rename scenario: gid giu nguyen (1383308512 cho DMBT T5.2026, 157327514 cho Sua chua T5.2026) van sync binh thuong.

## 9. UAT can user kiem tra tren Google Sheet/Android
1. Chon 1 record that moi gid DMBT: 2022, 2025, thang hien tai (1383308512).
2. Edit truc tiep tren sheet (ngay_sua_chua + ghi_chu marker), bam Dong bo day du, xac nhan app update dung 1 row khong x2.
3. Edit tren app, sync len, xac nhan ghi dung ve dung gid sheet nguon.
4. Lap lai sync 2-3 lan lien tiep, xac nhan khong append trung.
5. Test sheet repair gid 157327514: them 1 dong marker cho record co that, pull ve app, xac nhan update dung row.
6. Rollback ngay marker tren sheet, sync lai, xac nhan marker khong con.
7. Ghi lai pending/queue/retry error sau moi buoc.

## 10. Ket luan: nen sua gi truoc, chua nen sua gi

### Nen sua truoc
1. P0 provenance + identity cua DMBT (`sourceSheetId`, recordId namespace, unique business key merge).
2. P0 push idempotency/routing theo gid (khong append sai sheet, khong duplicate khi retry).
3. P0/P1 repair merge safety + observability skip reason.

### Chua nen sua ngay
1. UI/refactor lon.
2. Thay doi schema DB (chua can neu chua co quyet dinh migration du lieu).
3. Toi uu hieu nang/UX nhe (P2) truoc khi data integrity P0/P1 on dinh.

## Appendix - Bang chung nguon su that da doi chieu
- `docs/uat/results/USER_UAT_RESULT_2026-05-05.md` (UAT-06/UAT-09 duplicate, UAT-05 case 743BC04).
- `docs/uat/results/USER_UAT_DMBT_2025_SYNC_RESULT_2026-05-05.md` (`sourceSheetId=null` mass, case 463KL01/523RF03).
- `docs/review/MANAGER_ROOT_CAUSE_SHEET_PULL_UPDATED_AT_2026-05-05.md` (updated_at missing va fallback content-diff).
- `docs/review/AGENT2_COUNT_MISMATCH_ANALYSIS_2026-05-05.md` (HGT count mismatch do skip/collapse logic).
