# MANAGER REVIEW - AGENT 1 MONTHLY PHASE 3 MANUAL SIDEBAR LABEL (2026-05-06)

## 1. Ket luan

Trang thai: DUYET MOT PHAN, CAN LAM TIEP.

Agent 1 da lam dung phan quan trong nhat cua Phase 3: sidebar/filter khong con phan loai DMBT thang va Sua chua thang bang text T4/T5, ma uu tien `sourceSheetId/gid`.

Tuy nhien yeu cau moi cua Giam doc la 2 muc `DMBT T5.2026` va `Sua chua T5.2026` tai Sidebar can duoc nguoi dung sua ten thu cong. Phan nay Agent 1 chua implement trong app; hien chi giu label tinh `DMBT thang` va `Sua chua thang`, va neu muon doi wording thi phai sua resource string khi build.

Voi user non-tech, sua resource string khong duoc coi la "nguoi dung sua thu cong" trong san pham.

## 2. Doi chieu yeu cau

| Yeu cau | Ket qua review | Danh gia |
|---|---|---|
| Khong auto doi ten theo Google Sheet title | Khong thay logic fetch title tab de hien thi sidebar | PASS |
| Sidebar/filter khong dua vao text T4/T5 | `CategoryFilterMapper` uu tien `sourceSheetId`, fallback readonly gid | PASS |
| DMBT monthly gid `1383308512` vao nhom DMBT thang | Co logic sourceSheetId/monthly gid | PASS |
| Sua chua monthly gid `157327514` vao nhom Sua chua thang | Co logic sourceSheetId repair gid | PASS CO LUU Y |
| Nguoi dung sua ten thu cong trong app | Chua co UI/preference de user sua ten | CHUA DAT |
| Build/test | `./scripts/build-android-safe.ps1` pass | PASS |

## 3. Bang chung code

File review:

- `android-mvp/app/src/main/java/com/example/devicetracker/ui/search/CategoryFilterMapper.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/ui/search/SearchScreen.kt`
- `android-mvp/app/src/main/res/values/strings.xml`
- `android-mvp/app/src/test/java/com/example/devicetracker/ui/search/CategoryFilterMapperTest.kt`
- `docs/review/AGENT1_MONTHLY_PHASE3_MANUAL_SIDEBAR_LABEL_REPORT_2026-05-06.md`

Ket qua tot:

- Da bo constant cu `CATEGORY_MONTHLY_DMBT_T4_2026` va `CATEGORY_MONTHLY_REPAIR_T4_2026`.
- Label UI da doi thanh trung tinh:
  - `DMBT thang`
  - `Sua chua thang`
- Test bao ve case co text `DMBT T5.2026` nhung `sourceSheetId` yearly thi khong bi classify monthly.

## 4. Rủi ro/cần làm tiếp

### Finding 1 - User chưa tự sửa được tên Sidebar trong app

Agent 1 ghi "Cách thủ công hiện tại: sửa trực tiếp resource string trong app build". Cách này chỉ phù hợp với developer, không phù hợp với Giám đốc/user non-tech.

Nếu yêu cầu sản phẩm là user tự sửa tên hiển thị, cần thêm cơ chế nhỏ trong app:

- Lưu 2 label override bằng DataStore/local preferences.
- UI đọc override nếu có, nếu không thì dùng mặc định.
- Có màn chỉnh đơn giản 2 ô text + nút reset mặc định.

Không cần và không nên lấy tên tab từ Google Sheet.

### Finding 2 - Category `Sửa chữa tháng` cần xác nhận dữ liệu hiển thị thực tế

Mapper hiện có bucket `MONTHLY_REPAIR` theo `sourceSheetId = 157327514`. Cần UAT hoặc kiểm tra luồng dữ liệu để chắc record dạng sửa chữa tháng thực sự đi vào list `DeviceLog` có sourceSheetId này.

Nếu repair monthly chỉ merge vào DMBT monthly mà không tạo danh sách DeviceLog riêng, category `Sửa chữa tháng` có thể không có item riêng để hiển thị. Đây không phải lỗi build, nhưng cần xác nhận kỳ vọng sản phẩm.

## 5. Verify

Lenh da chay:

```powershell
.\scripts\build-android-safe.ps1
```

Ket qua:

- `:app:testDebugUnitTest` PASS.
- `:app:assembleDebug` PASS.
- `BUILD SUCCESSFUL`.

Canh bao:

- `.android` metrics warning va Kotlin deprecation warning van con, khong lam fail build.

## 6. Quyet dinh Manager

Khong can bat Agent 1 lam lai phan classification.

Can giao task nho tiep theo neu Giam doc muon dung nghia "user tu sua ten thu cong trong app":

- Them label override local cho 2 muc sidebar monthly.
- Khong dung Google Sheet title.
- Khong sua sync core.
- Khong sua DB schema.
