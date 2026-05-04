# TEST PLAN (SIET CHAT)

## 1. Muc tieu test
- Chan regression cac luong nut bam chinh.
- Bao dam rule nghiep vu khong bi vo.
- Bao dam local-first + sync-later dung trang thai.

## 2. Gate bat buoc truoc khi dong task
1. Unit tests pass cho logic vua chinh sua.
2. `assembleDebug` pass.
3. Smoke test tren may Android that pass.
4. Co evidence command moi trong worklog.

## 3. Core flow test (nut bam)
1. Sidebar mo/dong on dinh.
2. Chuyen khu 1/khu 2/khu 3/khu 4 dung danh sach.
3. Nut `Them ban ghi` vao dung man hinh va luu duoc.
4. Nut vao chi tiet + cap nhat ngay sua chua.
5. Man HGT: sua `lan_gan_nhat` -> `lan_tiep_theo` tinh dung.

## 4. Rule validation test
1. Khong luu neu thieu `ma_thiet_bi`.
2. Khong luu neu `ngay_phat_hien` sai dinh dang `dd/MM/yyyy`.
3. `ngay_sua_chua` trong -> record la `chua_sua`.
4. `ngay_sua_chua` co gia tri -> record la `da_sua`.
5. Khong cho trung `record_id`.

## 5. Offline + sync test
1. Them ban ghi khi offline -> `saved_local/pending_sync`.
2. Mo lai app van con du lieu local.
3. Co mang lai -> sync thanh cong, khong mat du lieu.
4. Sync loi -> hien trang thai loi ro rang, cho retry.

## 6. Sheet structure change test
1. Missing required column -> fail ro + thong bao ro.
2. Doi thu tu tab/ten tab nhung `sheetId` dung -> app van dung mapping.
3. Tuyet doi khong retry mu khi loi schema.

## 7. Regression package checklist
- [ ] Unit test pass
- [ ] Build debug pass
- [ ] Sidebar flow pass tren may that
- [ ] Khu 1/2/3/4 hien thi dung
- [ ] Rule `ngay_sua_chua` pass
- [ ] HGT auto-calc pass
- [ ] Worklog cap nhat theo ngay

## 8. Stop-the-line rule
Neu cung mot loi xuat hien lan 2 trong 48h:
1. Dung them feature moi.
2. Tao RCA ngan gon (nguyen nhan, cach tai hien, fix, test phong tai phat).
3. Chi mo lai feature sau khi RCA da pass gate verify.
