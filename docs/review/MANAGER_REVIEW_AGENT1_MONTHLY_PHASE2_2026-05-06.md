# MANAGER REVIEW - AGENT 1 MONTHLY SEPARATION PHASE 2 (2026-05-06)

## 1. Ket luan

Trang thai: DUYET CO DIEU KIEN.

Agent 1 da lam dung muc tieu Phase 2: tach luong DMBT yearly va DMBT monthly bang `sourceSheetId/gid`, scope repair monthly chi merge vao DMBT monthly, va push DMBT khong con roi ve default sheet khi provenance khong ro.

Chua coi la hoan tat du an vi van can Phase 3 de lam ro UI/filter va can cai ban moi len dien thoai de UAT that tren Google Sheet.

## 2. Bang doi chieu yeu cau

| Hang muc | Ket qua review | Danh gia |
|---|---|---|
| Chan merge cheo yearly/monthly | `resolveExistingLocalForRemote` da kiem tra source compatibility truoc khi nhan exact match va business-key match | PASS |
| Repair monthly chi an vao DMBT monthly | `mergeRepairLogsFromRemote` chi lay candidates theo `MONTHLY_DMBT_SHEET_IDS` | PASS |
| Push khong ghi sai sheet | `groupDmbtLogsByTargetSheet` route theo `sourceSheetId` hoac readonly gid; unknown provenance bi skip fail-safe | PASS |
| Khong phu thuoc ten tab T4/T5 | Core sync dung gid `1383308512` va `157327514`, khong dung label tab | PASS |
| Test/build | `./scripts/build-android-safe.ps1` pass ca `:app:testDebugUnitTest` va `:app:assembleDebug` | PASS |

## 3. Bang chung kiem tra

Lenh da chay:

```powershell
.\scripts\build-android-safe.ps1
```

Ket qua:

- `:app:testDebugUnitTest` PASS.
- `:app:assembleDebug` PASS.
- `BUILD SUCCESSFUL`.

Canh bao con lai:

- Android metrics path `.android` trong sandbox khong khoi tao duoc. Day la warning moi truong, khong lam fail build/test.
- Kotlin/Hilt deprecation warnings con ton tai, khong thuoc scope Phase 2.

## 4. Rui ro con lai

1. `MONTHLY_DMBT_SHEET_IDS` dang hard-code `1383308512`.
   - An toan cho hien tai vi user chi doi ten tab, gid khong doi.
   - Neu tuong lai tao tab thang moi voi gid moi, can cap nhat config hoac co registry dynamic.

2. Local legacy `sourceSheetId = null` van duoc phep match khi remote co source sheet, nhung chi khi key du duy nhat.
   - Muc dich la backfill an toan cho du lieu cu.
   - Rui ro con lai: record legacy qua ban hoac trung key se bi skip de tranh ghi nham.

3. Record `dmbt-auto-*` van duoc phep route vao default create sheet.
   - Day la luong tao loi moi tu app.
   - Can UAT de xac nhan default create target hien tai dung la sheet thang can ghi.

4. UI/filter van co the chua phan loai hoan toan theo `sourceSheetId`.
   - Core sync da tach, nhung nguoi dung can nhin thay dung nhom tren app.
   - Day la ly do can Phase 3.

## 5. Quyet dinh Manager

Cho phep Agent 1 tiep tuc Phase 3.

Phase 3 chi nen lam UI/filter classification va UAT readiness, khong sua schema database, khong sua Google Sheet contract, khong sua logic HGT, va khong refactor sync core lon.

## 6. Dieu kien truoc UAT that

Truoc khi user test lai tren dien thoai:

1. Phase 3 phai pass build/test.
2. Cai APK moi len dien thoai that.
3. UAT lai rieng cac ca:
   - DMBT 2025 sheet -> app.
   - App -> DMBT 2025 sheet.
   - DMBT T5.2026 sheet -> app.
   - App -> DMBT T5.2026 sheet.
   - Sua chua T5.2026 -> app.
   - Retry sync 2 lan khong duplicate.
