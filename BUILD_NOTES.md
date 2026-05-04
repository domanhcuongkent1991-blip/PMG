# Build Notes

## Truoc khi chay project
1. Mo thu muc `android-mvp` bang Android Studio.
2. Cai JDK 17 va Android SDK 36 (Platform + Build-Tools + Platform-Tools).
3. Uu tien chay tren may Android that (USB debugging) de giam tai cho may.
4. Du an da co `gradlew` va `gradle/wrapper`, uu tien build bang wrapper de dong nhat moi truong.
5. Cau hinh `SheetConfig` truoc khi bat sync that:
   - `spreadsheetId`
   - `sheetRoleToId` cho cac role bat buoc

## Cac file can sua dau tien
- `android-mvp/app/src/main/java/com/example/devicetracker/data/remote/SheetsRemoteDataSource.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/data/sheet/SheetConfig.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/ui/search/SearchScreen.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/ui/edit/EditLogScreen.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/ui/repair/UpdateRepairDateScreen.kt`

## Lenh build khuyen dung
- `cd android-mvp`
- `PowerShell: . "F:\codex_android_gsheet_full_pack\.tools\env-android.ps1"`
- `PowerShell (triệt de lock file): .\scripts\build-android-safe.ps1`
- `.\gradlew.bat --no-daemon assembleDebug`
- `.\gradlew.bat --no-daemon testDebugUnitTest`

## Cau hinh Google Sheets truoc khi bat sync that
1. Sao chep `android-mvp/local.properties.example` thanh `android-mvp/local.properties`.
2. Dien:
   - `SHEETS_SPREADSHEET_ID`
   - `SHEETS_DMBT_LOG_SHEET_ID`
   - `SHEETS_HGT_CHECKS_SHEET_ID`
   - `SHEETS_ACCESS_TOKEN` (tam thoi cho dev)
3. Khong commit token vao git.
4. Neu chua dien du config, worker sync se fail an toan (`NonRetryableSyncException`), khong retry mu.

## Luu y an toan du lieu
- Room khong con `fallbackToDestructiveMigration`.
- `record_id` duoc tao bang UUID de tranh trung.
- Worker sync se `failure` neu loi cau hinh sheet (khong retry mu).
