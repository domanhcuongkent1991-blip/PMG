# AGENT2 POST DUPLICATE FIX VALIDATION CHECKLIST - 2026-05-05

## Muc tieu
Checklist nay dung ngay sau khi Agent 1 hoan tat P0 duplicate (pull duplicate DMBT + retry sync duplicate) de xac minh nhanh:
- UAT-11 con lech `app 22` vs `sheet 19` hay khong.
- UAT-12 con lech `sheet 52` vs `app 48` hay khong.

## Nguyen tac
- Khong sua code trong buoc verify nay.
- Khong sua cau truc/permission Google Sheet.
- Uu tien doi chieu cung thoi diem (snapshot app va sheet trong 1 lan full sync).

## A. Preconditions truoc khi test lai
1. Agent 1 da xac nhan merge fix P0 duplicate xong va co bang chung:
- Khong con duplicate DMBT khi pull lai.
- Retry sync khong sinh dong trung tren sheet.
2. App build test la ban da chua fix P0.
3. User/Manager xac nhan workbook dang test la workbook UAT that.

## B. Quy trinh verify nhanh UAT-11 (Sua chua thang count)
1. Chay 1 lan `Dong bo day du` tren app.
2. Tai cung thoi diem, ghi 2 so:
- `R_sheet`: so dong thuc te trong tab `Sua chua T*.2026` (bo row header).
- `R_app`: so item app hien o man hinh user dang goi la "Sua chua thang".
3. Neu co bo loc tren app, dam bao dang o trang thai "tat ca" truoc khi dem.
4. Luu bang chung toi thieu:
- 1 anh/man hinh app co tong so item.
- 1 anh/man hinh sheet co tong so dong.

### Ket luan UAT-11
- PASS tam thoi neu: `R_app == R_sheet` sau khi duplicate P0 da fix.
- Neu van lech:
  - Neu lech giam ro ret so voi truoc (vi du tu `22-19` ve `19-19`):
    - Xac nhan mismatch cu la **hau qua cua duplicate DMBT**.
  - Neu van lech on dinh du duplicate da het:
    - Danh dau can P1 tach dinh nghia count (dem theo tab Sua chua vs dem DMBT da merge).

## C. Quy trinh verify nhanh UAT-12 (HGT count)
1. Chay 1 lan `Dong bo day du` tren app.
2. Tai cung thoi diem, ghi 2 so:
- `H_sheet`: so dong thuc te tab `HGT dinh ky` (bo header).
- `H_app`: so item dang hien tren man HGT app (query rong, khong loc).
3. Luu bang chung toi thieu:
- 1 anh/man hinh app HGT full list.
- 1 anh/man hinh sheet HGT full count.

### Ket luan UAT-12
- PASS tam thoi neu: `H_app == H_sheet`.
- Neu van lech (`H_app < H_sheet`) sau khi P0 duplicate da fix:
  - Danh dau **HGT can code fix rieng** (khong quy het cho duplicate DMBT).
  - Huong fix uu tien: telemetry skip reason trong pull HGT + review merge collapse theo `ma_thiet_bi`.

## D. Dieu kien xac dinh "HGT can code fix rieng"
Danh dau HGT can fix rieng khi thoa tat ca:
1. Duplicate DMBT da het (P0 da pass).
2. Da full sync lai it nhat 1 lan.
3. `H_sheet` van lon hon `H_app`.
4. Khong co UI filter dang an bot (query rong / tat ca).

## E. Dieu kien xac dinh "Sua chua thang chi la hau qua duplicate DMBT"
Danh dau mismatch sua chua thang la hau qua duplicate neu thoa tat ca:
1. Truoc fix P0 co duplicate DMBT ro rang (da co bang chung UAT-06/UAT-09).
2. Sau fix P0, `R_app` giam ve bang `R_sheet` (hoac chenh lech bien mat).
3. Khong thay phat sinh mismatch moi o luong repair merge.

## F. Neu van lech, du lieu can user/Manager cung cap ngay
1. Cho UAT-11:
- Danh sach row `Sua chua T*.2026` user dang dem.
- Danh sach item app user dang dem (toi thieu: `record_id`, `ma_thiet_bi`, `ngay_sua_chua`, `sourceSheetId` neu co).
2. Cho UAT-12:
- Snapshot 52 row HGT gom: `record_id`, `ma_thiet_bi`, `chu_ky_ngay`, `lan_gan_nhat`, `lan_tiep_theo`, `updated_at`.
- Snapshot danh sach HGT app cung timestamp.

## G. Ket qua can ghi sau lan verify
- UAT-11: PASS/FAIL + `R_app`/`R_sheet`.
- UAT-12: PASS/FAIL + `H_app`/`H_sheet`.
- Nhanh ket luan:
  - "Repair mismatch la hau qua duplicate" hoac
  - "HGT can code fix rieng".
