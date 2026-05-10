# AGENT2 DMBT MONTHLY + REPAIR + SIDEBAR UAT MATRIX - 2026-05-05

## 1) Scope
Bo sung ma tran UAT cho 2 tab thang user vua yeu cau:
- `DMBT T5.2026` - gid `1383308512`
- `Sua chua T5.2026` - gid `157327514`

Luu y quan trong:
- 2 gid tren giong voi tab T4 truoc day (tab da doi ten theo thang), phu hop rule sync theo gid trong PRD.
- Luot nay chi lap checklist + phan tich. Khong sua code, khong sua Sheet that.

## 2) Matrix UAT de chay sau ban fix core cua Agent 1

| Case ID | Sheet | Chieu | Buoc test toi thieu | Pass criteria | Fail criteria |
|---|---|---|---|---|---|
| M-DMBT-01 | DMBT T5.2026 (gid 1383308512) | Sheet -> App | Sua 1 dong nho tren tab T5 (record co san), bam Dong bo day du tren app, mo dung ma thiet bi do | App thay doi dung row/field vua sua, khong duplicate | App khong thay doi, hoac cap nhat sai row, hoac duplicate |
| M-DMBT-02 | DMBT T5.2026 (gid 1383308512) | App -> Sheet | Tren app cap nhat ngay sua/ghi chu cho 1 record thuoc T5, bam Dong bo day du | Tab T5 thay doi dung row tuong ung, dung gia tri, khong ghi sang tab khac | Khong len sheet, len sai row, hoac ghi nham sheet |
| M-REPAIR-01 | Sua chua T5.2026 (gid 157327514) | Sheet -> App | Tren tab Sua chua T5 them/sua 1 repair row co `record_id` that da ton tai local/DMBT, bam Dong bo day du | App chuyen dung ban ghi sang da sua (ngay_sua/ghi_chu dung), khong tao row moi sai | Khong merge duoc, merge nham row, hoac phat sinh duplicate |
| M-REPAIR-02 | Sua chua T5.2026 (gid 157327514) | App -> Sheet | Tren app cap nhat ngay sua, bam Dong bo day du | Sheet dich phai cap nhat dung theo contract hien tai (DMBT target va/hoac repair monthly theo implementation hien hanh), khong sai sheet | Khong phan anh tren sheet dich, hoac ghi sai sheet, hoac duplicate |

## 3) Sidebar label analysis (code read-only)

### 3.1 Ket qua phan tich
- Sidebar category thang hien **hard-code T4.2026**:
  - `CategoryFilterMapper.kt` co constant:
    - `CATEGORY_MONTHLY_DMBT_T4_2026`
    - `CATEGORY_MONTHLY_REPAIR_T4_2026`
  - `SearchScreen.kt` map label qua string:
    - `category_monthly_dmbt_t4_2026`
    - `category_monthly_repair_t4_2026`
  - `strings.xml` hien text cung T4.2026.
- Luong sync du lieu remote ben `SheetsRemoteDataSource.kt` dang dua tren `sheetId/gid` + metadata title (khong khoa theo ten tab co dinh).

### 3.2 Phan biet 2 tinh huong bat buoc khi UAT
- a) **Sync dung gid nhung label sidebar cu**:
  - Danh gia: loi UX/label, khong phai loi data P0.
  - Priority de xuat: P2 (hoac P1 neu gay chon nham nghiep vu).
- b) **Doi ten tab lam sync sai/khong sync**:
  - Danh gia: loi data nghiem trong.
  - Priority: P0.

## 4) Bang chung user can gui de ket luan nhanh
1. Ten tab that dang hien tren Google Sheet (anh ro phan tab).
2. gid tung tab (DMBT T5, Sua chua T5).
3. Anh sidebar app dang hien label.
4. Anh dong test truoc/sau tren sheet (cung record).
5. Anh app sau sync (hien dung record vua sua).
6. Neu co lech: video ngan 1 luot thao tac Sheet -> sync app -> kiem tra.

## 5) Dieu kien PASS / FAIL

### PASS
1. Doi ten tab nhung sync van dung theo gid.
2. Du lieu pull/push dung row, dung sheet dich, khong duplicate.
3. `DMBT T5.2026` va `Sua chua T5.2026` deu qua 2 chieu theo matrix tren.
4. Sidebar:
   - Hoac hien dung ten moi,
   - Hoac co ket luan ro rang: label dang hard-code (UX fix), nhung sync data van dung.

### FAIL
1. Doi ten tab lam app khong sync hoac sync sai.
2. Ghi nham sheet (vi du dang test T5 nhung ghi sang tab khac).
3. Khong keo/khong day du lieu cho DMBT T5 hoac Sua chua T5.
4. Duplicate phat sinh lai.
5. Sidebar label cu gay user chon nham luong du lieu (nang len P1 UX).

## 6) Ket luan va de xuat

### 6.1 Co can code fix sidebar khong?
- **Co**. Theo code hien tai, label monthly dang hard-code T4.2026.
- Muc uu tien de xuat:
  - P2 neu chi sai nhan va user van thao tac dung.
  - P1 neu gay chon nham luong/nham expectation nguoi dung.

### 6.2 Co can code fix sync monthly/repair khong?
- **Can xac nhan bang UAT matrix tren sau ban fix Agent 1**.
- Neu sync theo gid van dung cho T5 (ca DMBT va repair): khong mo bug P0 moi ve sync rename.
- Neu doi ten tab lam sai pull/push: mo **P0 sync bug** ngay.

### 6.3 Can user cung cap gi de chot?
- Bo anh du muc 4 (tab ten that + gid + sidebar + before/after row + app after sync).
- Neu van fail: gui them 1 cap record_id cu the cho moi case (1 case DMBT T5, 1 case repair T5) de trace nhanh.
