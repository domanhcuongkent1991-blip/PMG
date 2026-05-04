# PROJECT COMPLETION PLAN - DeviceTracker Android + Google Sheets

> File theo doi tien do chinh cua du an. Viec da xong danh dau `[x]`, viec chua xong danh dau `[ ]`.

## 0) Nguyen tac an toan

- [x] Doc va cap nhat PRD chinh thuc trong `input/prd.md`.
- [x] Xac nhan pham vi 8 sheet can dong bo.
- [x] Xac nhan tat ca 8 sheet deu can dong bo hai chieu.
- [x] Ghi chu sheet theo thang se do nguoi dung tu doi ten.
- [x] Ghi chu app phai dung `gid/sheetId`, khong phu thuoc vao ten sheet co dinh.
- [x] Khong doc/sua `android-mvp/local.properties`.
- [x] Khong ghi du lieu that len Google Sheet trong cac buoc code local.
- [x] User cho phep test truc tiep tren Google Sheet that, khong can tao workbook test copy.
- [ ] Truoc moi lan test ghi that, chay dry-run/kiem tra target sheet va chi ghi mot tap nho co kiem soat.

## 1) Tai lieu va yeu cau san pham

- [x] Cap nhat `input/prd.md` theo mo ta moi cua user.
- [x] Cap nhat `SHEET_DATA_CONTRACT_MVP.md`.
- [x] Cap nhat `SYNC_RULES.md`.
- [x] Cap nhat `UAT_CHECKLIST_SYNC_LOCAL_FIRST.md`.
- [x] Cap nhat `android-mvp/SYNC_SETUP.md`.
- [x] Cap nhat `android-mvp/local.properties.example`.
- [x] Cap nhat `docs/audit/AUDIT.md`.
- [x] Cap nhat worklog `WORKLOG_2026-05-03.md`.
- [x] Them `Stability Upgrade V4` vao plan sync.
- [ ] Doi ten/bo han logic tai lieu cu lien quan `read-only DMBT` sau khi code legacy duoc thay het.

## 2) Cau hinh sheet va role

- [x] Ghi nhan spreadsheetId production do user cung cap.
- [x] Ghi nhan gid `DMBT 2022`: `849979183`.
- [x] Ghi nhan gid `DMBT 2023`: `1783863163`.
- [x] Ghi nhan gid `DMBT 2024`: `1224276666`.
- [x] Ghi nhan gid `DMBT 2025`: `989601207`.
- [x] Ghi nhan gid `DMBT 2026`: `1607125070`.
- [x] Ghi nhan gid `DMBT T4.2026`: `1383308512`.
- [x] Ghi nhan gid `Sua chua T4.2026`: `157327514`.
- [x] Ghi nhan gid `HGT dinh ky`: `57428884`.
- [x] Them BuildConfig `SHEETS_DMBT_SHEET_IDS`.
- [x] Them BuildConfig `SHEETS_DMBT_DEFAULT_CREATE_SHEET_ID`.
- [x] Them BuildConfig `SHEETS_REPAIR_LOG_SHEET_ID`.
- [x] Them config parser `dmbtSheetBindings`.
- [x] Them role `DMBT_REPAIR_LOG`.
- [x] Them contract cot toi thieu cho `DMBT_REPAIR_LOG`.
- [x] Doi code pull DMBT sang `dmbtSheetBindings`, khong con goi `dmbtReadOnlySheetIds` trong remote data source.
- [ ] Them kill switch config de tat ghi nguoc DMBT/HGT/repair khi co loi production.

## 3) Local database va identity

- [x] `DeviceLogEntity` co `sourceSheetId`.
- [x] `DeviceLog` domain model co `sourceSheetId`.
- [x] Mapper entity/domain giu `sourceSheetId`.
- [x] Co migration DB them `sourceSheetId`.
- [x] Test round-trip entity/domain giu `sourceSheetId`.
- [ ] Test migration Room 2 -> 3 bang androidTest hoac migration test.
- [ ] Kiem tra seed data co sourceSheetId hop ly neu can.
- [ ] Them chi muc/index neu tra cuu 10.000 ban ghi cham.

## 4) DMBT sync hai chieu nhieu sheet

- [x] Pull DMBT gan `sourceSheetId` theo sheet dang doc.
- [x] Them parser config nhieu DMBT sheet thanh `TWO_WAY`.
- [x] `pushLogs()` da chia log theo target sheet.
- [x] Log co `sourceSheetId` push nguoc dung sheet nguon.
- [x] Log moi khong co `sourceSheetId` dung default create sheet.
- [x] Strip prefix legacy `readonly-dmbt-{sheetId}-` truoc khi ghi nguoc.
- [x] Targeted test `SheetsRemoteDataSourceRecordIdTest` pass.
- [x] Doi ten helper legacy `buildReadOnlyDmbtRecordId` sang `buildNamespacedDmbtRecordId` de khong gay hieu nham.
- [x] Doi pull logic tu "read-only ids" sang "configured DMBT sheet bindings".
- [x] Them unit test helper de dam bao pull target dung `sheetId` va namespace non-default sheet.
- [x] Test DMBT pull tu nhieu sheet khong mat `sourceSheetId`.
- [x] Test DMBT push payload co ban ghi lap lai chi giu ban moi nhat de tranh duplicate sau retry.
- [ ] Test sua ban ghi tu `DMBT 2022` chi ghi nguoc `DMBT 2022`.
- [ ] Test tao ban ghi moi chi ghi vao default create sheet thang hien tai.
- [ ] Test sheet sai schema fail ro, khong bao thanh cong gia.

## 5) Sua chua sheet sync

- [x] User xac nhan `Sua chua T4.2026` duoc pull va ghi nguoc.
- [x] Them `DMBT_REPAIR_LOG` role.
- [x] Them `SHEETS_REPAIR_LOG_SHEET_ID`.
- [x] Them test contract repair sheet.
- [x] Registry dat `DMBT_REPAIR_LOG` la `TWO_WAY`.
- [ ] Lay/kiem tra header that cua sheet `Sua chua T4.2026` tren Google Sheet that, khong ghi du lieu.
- [x] Viet mapper row -> repair update.
- [x] Viet mapper repair update -> sheet row.
- [x] Them `ghi_chu` vao cot bat buoc cua `DMBT_REPAIR_LOG` de khop yeu cau cap nhat ngay sua kem ghi chu.
- [ ] Them push/pull repair sheet khong dung nham mapper DMBT.
- [ ] Test cap nhat ngay sua + ghi chu day len dung repair sheet.
- [ ] Test Google Sheet sua repair row, app keo ve dung.

## 6) HGT dinh ky

- [x] App co model/DAO/repository HGT.
- [x] App co HGT sync push/pull co ban.
- [x] Co test tinh ngay HGT.
- [x] Co HGT reminder scheduler/receiver.
- [ ] Test HGT sync tren Google Sheet that theo tung record nho.
- [ ] Test HGT reminder tren Android that.
- [ ] Test sau khi reboot may, reminder van duoc lap lai.
- [ ] Test quyen notification Android 12/13/14.

## 7) Offline, conflict va retry

- [x] App theo local-first: luu local truoc, sync sau.
- [x] Local `PENDING` khong bi remote overwrite.
- [x] Local `FAILED` khong bi remote overwrite.
- [x] Sync queue co ghi loi khi push fail.
- [ ] Test offline them DMBT moi, online sync len Google Sheet that theo tung record nho.
- [ ] Test offline cap nhat ngay sua, online sync len Google Sheet that theo tung record nho.
- [ ] Test Google Sheet sua truoc, app full sync keo ve dung.
- [ ] Test app va Google Sheet cung sua mot record, app khong am tham mat du lieu.
- [ ] Test retry sau loi mang khong duplicate row.

## 8) Hieu nang 10.000 ban ghi

- [ ] Tao dataset test 10.000 DMBT rows.
- [ ] Test parser DMBT voi 10.000 rows.
- [ ] Test search theo `ma_thiet_bi` voi 10.000 rows.
- [ ] Test UI khong giat lag voi 10.000 rows.
- [ ] Neu cham, them paging/index/limit query.
- [ ] Test sync planning/batch voi 10.000 rows.

## 9) Workbook test va UAT

- [x] User cho phep bo qua workbook test copy va test truc tiep tren Google Sheet that.
- [ ] Ghi lai truoc moi lan test: sheetId, record_id, row du kien, thao tac du kien.
- [ ] Test add DMBT tren app -> Google Sheet that cap nhat dung sheet.
- [ ] Test sua DMBT tren Google Sheet that -> app cap nhat.
- [ ] Test sua ngay sua tren app -> repair sheet that cap nhat.
- [ ] Test sua repair sheet that -> app cap nhat.
- [ ] Test HGT app -> sheet va sheet -> app.
- [ ] Test schema sai tren pham vi an toan -> app bao loi ro.
- [ ] Test token/quyen sai -> app bao loi ro.
- [ ] Test khong ghi nham sheet.
- [ ] Test khong duplicate row sau retry.

## 10) Android device UAT

- [ ] Ket noi dien thoai Android that.
- [ ] Cai APK debug len dien thoai.
- [ ] Test mo app, search theo ma thiet bi.
- [ ] Test tao DMBT khi offline.
- [ ] Test bat mang va bam dong bo day du.
- [ ] Test cap nhat ngay sua + ghi chu.
- [ ] Test full sync keo thay doi tu Google Sheet ve app.
- [ ] Test HGT reminder.
- [ ] Ghi screenshot/log UAT vao worklog.

## 11) Bao mat va production gate

- [x] Khong commit `local.properties`.
- [x] Fix `prevent-secrets.js`: neu Node child_process/git bi sandbox chan thi fail ro, khong skip thanh cong.
- [x] Quet thu cong file da sua, chua thay secret.
- [ ] Chay `node scripts\prevent-secrets.js` trong terminal binh thuong truoc commit; trong sandbox hien fail ro voi `spawnSync ... EPERM`.
- [ ] Kiem tra APK production khong dong goi refresh token/access token that.
- [ ] Thiet ke token storage production an toan hon `BuildConfig`.
- [ ] Log redaction: khong log token, refresh token, client secret.
- [ ] Production broad sync chi bat sau khi controlled production test pass.

## 12) Verify hien tai

- [x] Targeted test `SheetsRemoteDataSourceRecordIdTest`: pass.
- [x] Full unit test `testDebugUnitTest`: pass.
- [x] Debug build `assembleDebug`: pass.
- [ ] Integration test voi Google Sheet that theo mode co kiem soat: chua lam.
- [ ] UAT tren dien thoai that: chua lam.
- [x] UAT tren workbook production: user da cho phep, nhung chua thuc hien.
- [x] Chay lai Gradle verify sau khi workaround Windows lock bang cach doi ten `android-mvp/app/build` cu thanh `android-mvp/app/build.locked-old`.

## 13) Muc hoan thanh uoc tinh

Uoc tinh theo yeu cau moi:

- Khung app Android/local/UI/sync nen tang: khoang 65-70%.
- Yeu cau sync hai chieu an toan ca 8 sheet: khoang 35-45%.
- Toan du an tinh ca controlled production UAT/production gate: khoang 50-55%.

## 14) Buoc nen lam tiep theo

1. Chay dry-run/kiem tra target sheetId truoc khi ghi Google Sheet that.
2. Chay UAT production theo tung record nho, co worklog/evidence.
3. Lay/kiem tra header that cua sheet `Sua chua T4.2026`, khong ghi du lieu.
4. Them push/pull repair sheet khong dung nham mapper DMBT.
5. Don folder build cu neu Windows tao lai lock build output.
