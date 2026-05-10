# USER UAT RESULT - 2026-05-05

## Nguon bao cao
- Nguoi test: Director/User thao tac tren Android phone va Google Sheet that.
- Bang chung kem theo trong thread:
  - Bang tong hop UAT-01 den UAT-17.
  - Anh duplicate khi tra cuu `473CV03`.
  - Anh duplicate khi tra cuu `473RB02`.
  - Anh notification HGT: `Sap den lich kiem tra HGT`.
- Ghi chu: day la UAT that, uu tien ket qua user test hon AI thao tac tu dong.

## Ket luan nhanh
- PASS/OK: UAT-01, UAT-02, UAT-03, UAT-04, UAT-07, UAT-08, UAT-10, UAT-13, UAT-14, UAT-15.
- PARTIAL / NEED INVESTIGATION: UAT-05, UAT-16, UAT-17.
- FAIL / BLOCK RELEASE: UAT-06, UAT-09, UAT-11, UAT-12.

## Bang ket qua chi tiet

| ID | Ket qua user test | Danh gia Manager | Muc uu tien |
|---|---|---|---|
| UAT-01 | Vao duoc app | PASS | P0 verified |
| UAT-02 | Dong bo duoc | PASS, nhung can doi chieu voi cac loi duplicate ben duoi | P0 verified |
| UAT-03 | Tra cuu duoc thiet bi | PASS | P0 verified |
| UAT-04 | Phan biet duoc chua sua/da sua | PASS | P0 verified |
| UAT-05 | App sync len Sheet da on nhung co case `743BC04`, ngay phat hien `09/01/2025` khong len duoc | PARTIAL FAIL: can debug theo record cu the | P1 |
| UAT-06 | Sheet sua ve app bi nhan them mot loi | FAIL: duplicate khi pull Sheet ve app | P0 |
| UAT-07 | Offline van nhap va luu duoc | PASS | P0 verified |
| UAT-08 | Bat mang lai sync du lieu cho thanh cong | PASS, nhung can kiem tra duplicate theo UAT-09 | P1 |
| UAT-09 | Sync lap lai van thay dong trung tren Google Sheet | FAIL: retry/idempotency chua an toan | P0 |
| UAT-10 | DMBT nhieu sheet hien thi dung | PASS | P0 verified |
| UAT-11 | Sua chua thang app hien 22, Sheet co 19 | FAIL: count mismatch repair monthly | P1 |
| UAT-12 | HGT Sheet 52, app 48 | FAIL: count mismatch HGT pull/display | P1 |
| UAT-13 | HGT app ghi len Sheet duoc | PASS | P0 verified |
| UAT-14 | HGT Sheet keo ve app duoc | PASS, nhung chua du vi UAT-12 count mismatch | P1 |
| UAT-15 | Da co tin nhan canh bao HGT | PASS | P0 verified |
| UAT-16 | Thinh thoang giat lag nhe | PARTIAL: can profiling/optimization sau khi sync dung | P2 |
| UAT-17 | Sheet da doi ten nhung app van giu ten cu | PARTIAL: sync theo gid co the van dung, nhung label UI chua cap nhat title moi | P2 |

## Loi release-blocking

### P0-1: Pull tu Google Sheet ve app tao duplicate
- Lien quan: UAT-06, UAT-09.
- Bang chung: anh user gui voi `473CV03` va `473RB02`, moi ma hien 2 card giong nhau, mot `Chua sua`, mot `Da sua`.
- Ly do nguy hiem: PRD cam duplicate hang loat va cam sync bao thanh cong gia.
- Gia thuyet ky thuat can kiem tra:
  - Local recordId khac nhau cho cung mot dong nghiep vu khi pull lai tu nhieu sheet.
  - Key match fallback chua dung/khong on dinh giua `record_id`, `ma_thiet_bi`, `ngay_phat_hien`, `hang_muc`, `tinh_trang`.
  - Merge repair tao ban ghi khac thay vi update ban ghi DMBT goc.

### P0-2: Sync lap lai van sinh dong trung tren Google Sheet
- Lien quan: UAT-09.
- Ly do nguy hiem: retry sync khong duoc duplicate.
- Gia thuyet ky thuat can kiem tra:
  - Push len Sheet khong tim duoc row cu nen append row moi.
  - `record_id` tren app va `record_id` tren Sheet khong cung format.
  - DMBT push sang sai target sheet/sourceSheetId bi thieu.

## Loi P1 can xu ly sau P0

### P1-1: Mot case app khong day len Sheet
- Lien quan: UAT-05.
- Case user ghi ro: `743BC04`, ngay phat hien `09/01/2025`.
- Can debug theo dung record nay: local status, queue item, sourceSheetId, sheet target, row match.

### P1-2: Sua chua thang count mismatch
- Lien quan: UAT-11.
- User thays: app 22 thiet bi, Google Sheet 19 thiet bi.
- Can xac dinh app dang tinh:
  - DMBT da merge repair?
  - Sheet `Sua chua T*.2026`?
  - Duplicate local?

### P1-3: HGT count mismatch
- Lien quan: UAT-12.
- User thays: Google Sheet 52 muc, app 48 muc.
- Can xac dinh 4 dong bi skip do:
  - thieu cot bat buoc,
  - ma thiet bi blank,
  - duplicate ma thiet bi bi collapse,
  - parse ngay/chu ky loi,
  - filter UI dang an bot.

## Loi P2

### P2-1: App giat lag nhe
- Lien quan: UAT-16.
- Chua release-blocking neu khong treo/crash, nhung can profiling sau khi sua duplicate/count mismatch.

### P2-2: App hien ten tab thang cu sau khi Sheet doi ten
- Lien quan: UAT-17.
- Neu sync van dung gid/sheetId thi khong phai loi data P0.
- Van can sua UX: app nen hien title moi lay tu Google Sheet metadata, hoac dung label trung tinh nhu `DMBT thang hien tai`.

## Review finding ve RepairRecordIdentityResolver
- Finding: malformed `readonly-dmbt-` namespace phai giu nguyen.
- Trang thai hien tai: code da dung. `stripDmbtNamespace()` chi strip khi sheetId la so va co base id; non-numeric/invalid namespace duoc giu nguyen.
- Ket luan: khong can giao lai finding nay, chi can giu test bao ve.

## Huong xu ly tiep theo
1. Khoa pham vi P0 truoc: duplicate khi pull va duplicate khi retry push.
2. Sau khi P0 fixed, moi xu ly count mismatch repair/HGT.
3. Sau khi data dung, moi toi uu lag nhe va label sheet doi ten.
