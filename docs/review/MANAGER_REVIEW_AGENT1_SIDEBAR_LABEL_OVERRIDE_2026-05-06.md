# MANAGER REVIEW - AGENT 1 SIDEBAR MONTHLY LABEL OVERRIDE (2026-05-06)

## 1. Ket luan

Trang thai: DUYET.

Agent 1 da hoan thanh dung yeu cau: nguoi dung co the doi ten hien thi thu cong cho 2 muc monthly trong Sidebar, khong tu dong doc ten tab Google Sheet, va khong dung ten tab de sync.

## 2. Doi chieu pham vi

| Yeu cau | Ket qua review | Danh gia |
|---|---|---|
| User tu sua ten DMBT thang | Co modal `Doi ten muc thang`, co o nhap DMBT thang | PASS |
| User tu sua ten Sua chua thang | Co modal `Doi ten muc thang`, co o nhap Sua chua thang | PASS |
| Co reset mac dinh | Co nut `Reset mac dinh`, xoa override local | PASS |
| Khong auto lay title tu Google Sheet | Khong thay logic fetch sheet title trong UI/sidebar | PASS |
| Khong sua sync core | Khong sua them cac file sync trong task label override | PASS |
| Khong sua DB schema | Chi them local SharedPreferences store nho | PASS |
| Build/test | `./scripts/build-android-safe.ps1` pass | PASS |

## 3. Bang chung code

File lien quan:

- `android-mvp/app/src/main/java/com/example/devicetracker/data/local/preferences/SidebarMonthlyLabelStore.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/ui/search/SearchScreen.kt`
- `android-mvp/app/src/main/res/values/strings.xml`
- `android-mvp/app/src/test/java/com/example/devicetracker/data/local/preferences/SidebarMonthlyLabelStoreTest.kt`
- `docs/review/AGENT1_SIDEBAR_MONTHLY_LABEL_OVERRIDE_REPORT_2026-05-06.md`

## 4. Ghi chu ky thuat de user non-tech de hieu

Phan nay chi doi "ten hien thi" trong app. No khong doi ten tab tren Google Sheet va khong doi cach dong bo.

Vi du:

- Google Sheet van co tab `DMBT T5.2026`.
- App van sync bang gid `1383308512`.
- User co the doi ten trong Sidebar thanh `DMBT T5.2026` hoac ten de nho khac.

Day la cach an toan vi tranh viec app phai tu doan ten tab Google Sheet.

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

- Con warning `.android` metrics path va Kotlin deprecation nhu cac luot truoc. Khong lam fail build/test.

## 6. Rui ro con lai

1. Chua UAT bang tay tren dien thoai that phan modal doi ten.
2. Label gioi han 64 ky tu, day la chu y de tranh label qua dai lam vo UI.
3. Store dung SharedPreferences nho rieng, phu hop scope hien tai. Neu sau nay co nhieu tuy chon UI hon, co the gom ve DataStore/Settings chung.

## 7. Buoc tiep theo de nghi

1. Cai APK moi len dien thoai.
2. User mo Sidebar, bam `Doi ten muc thang`.
3. Doi label thanh:
   - `DMBT T5.2026`
   - `Sua chua T5.2026`
4. Dong/mo app lai de xac nhan label van duoc luu.
5. Sau do UAT lai sync DMBT 2025, DMBT T5.2026, Sua chua T5.2026.
