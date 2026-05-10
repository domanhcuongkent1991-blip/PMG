# Root Cause - Sheet pull ignores manual edits when updated_at is missing - 2026-05-05

## Van de user phan anh

1. Loi thiet bi sua tren Google Sheet van chua dong bo ve app.
2. DMBT 2025 van bi loi hai chieu.
3. Du lieu tu Google Sheet dong bo ve app chua day du.

Vi du moi:

- Ma thiet bi: `754SC03`
- Ngay phat hien: `10/01/2026`
- Tren Sheet co ngay sua `05/05/2026` va ghi chu `test`
- Tren app van hien `Chua sua`

## Nguyen nhan goc

Khi user sua truc tiep tren Google Sheet, cot `updated_at` co the de trong hoac khong duoc cap nhat.

Code pull DMBT doc `updated_at` nhu sau:

- Neu `updated_at` khong phai so, app gan `updatedAt = 0`.
- Repository chi ap dung remote neu `remote.updatedAt >= local.updatedAt`.
- Vi local thuong co `updatedAt` lon hon 0, nen remote tu Sheet bi xem la cu hon va bi bo qua.

Ket qua: ngay sua/ghi chu da co tren Sheet nhung app khong keo ve.

## Huong fix

Khong thay doi kien truc sync. Chi sua rule ap dung remote:

1. Neu local dang `PENDING` hoac `FAILED`, khong ghi de de bao ve du lieu offline.
2. Neu remote co `updatedAt > 0`, van dung rule cu: remote moi hon hoac bang thi apply.
3. Neu remote co `updatedAt <= 0`, so sanh noi dung thuc te:
   - DMBT: ma thiet bi, hang muc, nguoi bao cao, tinh trang, KTV, ngay phat hien, ngay sua, ghi chu.
   - Repair: ngay sua, ghi chu.
4. Neu noi dung Sheet khac local va local da `SYNCED`, app keo thay doi ve.

## File da sua

- `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImpl.kt`
- `android-mvp/app/src/test/java/com/example/devicetracker/data/repository/DeviceLogRepositorySyncRulesTest.kt`
- `android-mvp/app/src/test/java/com/example/devicetracker/data/repository/DeviceLogRepositoryRepairPullMergeTest.kt`

## Test bo sung

Da them test cho cac truong hop:

1. DMBT manual Sheet edit co `updatedAt = 0` nhung ngay sua/ghi chu khac local thi phai apply.
2. DMBT `updatedAt = 0` nhung noi dung giong local thi khong apply lap lai.
3. Repair log manual co `updatedAt = 0` nhung ngay sua/ghi chu khac local thi phai merge.
4. Repair log `updatedAt = 0` nhung noi dung giong local thi khong merge lap lai.

## Verify

Da chay:

```powershell
./scripts/build-android-safe.ps1
```

Ket qua:

- `testDebugUnitTest`: PASS
- `assembleDebug`: PASS

Da cai APK moi len dien thoai:

- Device: `RMX3081`
- Package: `com.example.devicetracker`
- Ket qua: `adb install -r ...`: Success

## UAT can test lai

1. Bam `Dong bo day du`.
2. Tim `754SC03`, ngay phat hien `10/01/2026`.
3. Ket qua dung: app phai hien `Da sua`, ngay sua `05/05/2026`, ghi chu `test`.
4. Test lai DMBT 2025:
   - `463KL01`, ngay phat hien `07/01/2025`
   - `523RF03`, ngay phat hien `08/01/2025`
5. Bam sync them 2 lan de xac nhan khong duplicate.

## Rui ro con lai

Neu mot row tren Sheet thieu business key qua nhieu, vi du thieu ma thiet bi/ngay phat hien/hang muc/tinh trang, app se skip an toan thay vi merge nham. Day la hanh vi dung de tranh ghi sai sheet/sai row.
