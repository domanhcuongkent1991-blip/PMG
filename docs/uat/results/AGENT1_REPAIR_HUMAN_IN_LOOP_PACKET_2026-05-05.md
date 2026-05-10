# AGENT1 REPAIR-01 HUMAN-IN-THE-LOOP PACKET - 2026-05-05

## 1) Muc tieu packet
Packet nay huong dan Manager/operator chay **REPAIR-01** tren Google Sheet that theo cach an toan:
- Chi them **1 dong test**.
- Khong doi cau truc/permission/header.
- Co ghi before/after + rollback ngay.

Luu y quan trong:
- Tai lieu nay **khong phai ket qua UAT pass**.
- UAT chi duoc danh dau PASS sau khi nguoi thao tac sheet thuc hien day du buoc va co evidence.

---

## 2) Trang thai thong tin record that
Tu cac file evidence duoc phep doc trong luot nay, chua co san mot `record_id` that cu the de copy/paste ngay.

Vi vay, can Manager/operator cung cap hoac trich xuat 1 record that theo dieu kien an toan ben duoi.

### Manager update sau review
Manager da doc database debug tren dien thoai bang `run-as` chi de lay candidate UAT. Candidate ben duoi ton tai trong app local, dang `SYNCED`, va `ngay_sua_chua` dang trong:

| Field | Candidate |
|---|---|
| REAL_RECORD_ID | `seed-beta-dmbt-2022-r5` |
| REAL_DEVICE_CODE | `523BC03` |
| syncStatus | `SYNCED` |
| ngay_sua_chua before | blank/null |
| ghi_chu before | blank |
| sourceSheetId | blank/null trong DB debug |

Can xac nhan lai tren app/sheet truoc khi ghi dong test:
- Tim `ma_thiet_bi = 523BC03` trong app.
- Xac nhan ban ghi dang "Chua sua" va ngay sua chua trong.
- Neu khong tim thay hoac co hon 1 ban ghi gay nham lan, khong dung candidate nay.
- Vi `sourceSheetId` trong DB debug dang blank/null, candidate nay chi dung cho UAT exact local `record_id`, khong dung de ket luan sourceSheetId theo sheet nam.

### Dieu kien chon record an toan
1. Record ton tai that trong DMBT + app local.
2. Nen la record dang `SYNCED`.
3. Khong phai dong test cu (`ghi_chu` khong chua `CODEX_TEST_*`).
4. Biet ro thuoc sheet nao (gid/sheetId nao).

---

## 3) Cach lay REAL_RECORD_ID (chon 1 cach)

### Cach A (uu tien cho non-tech)
1. Mo app tren dien thoai, tim 1 ban ghi DMBT that theo `ma_thiet_bi`.
2. Mo chi tiet ban ghi va xem thong tin ID (neu man hinh detail co hien `record_id`).
3. Ghi lai dung nguyen van `record_id` va `ma_thiet_bi`.

### Cach B (khi app khong hien record_id)
Manager nhan 1 cap thong tin tu nguoi ky thuat:
- `REAL_RECORD_ID`
- `REAL_DEVICE_CODE`
- `gid/sheetId` cua sheet nguon

Khong duoc tu tao `record_id` gia.

---

## 4) Mau copy/paste 1 dong test (REPAIR-01)
Dien gia tri that vao cac placeholder roi them **1 dong** vao sheet Sua chua:

- `record_id = <REAL_RECORD_ID>`
- `ma_thiet_bi = <REAL_DEVICE_CODE>`
- `ngay_sua_chua = 2026-05-05`
- `ghi_chu = CODEX_TEST_REPAIR_EXACT_20260505_<timestamp>`

Vi du marker timestamp:
- `CODEX_TEST_REPAIR_EXACT_20260505_153045`

Quy tac:
- `CODEX_TEST_*` chi duoc dat trong `ghi_chu`.
- Tuyet doi khong dung `CODEX_TEST_*` lam `record_id`.

### Dong test de copy/paste neu candidate tren da duoc xac nhan

- `record_id = seed-beta-dmbt-2022-r5`
- `ma_thiet_bi = 523BC03`
- `ngay_sua_chua = 2026-05-05`
- `ghi_chu = CODEX_TEST_REPAIR_EXACT_20260505_523BC03`

Chi them dong nay neu Manager da xac nhan candidate `523BC03` la dung ban ghi can test.

---

## 5) Bang BEFORE bat buoc ghi lai truoc khi them dong test
| Field | Gia tri truoc test |
|---|---|
| sheet | |
| gid/sheetId | |
| record_id | |
| ma_thiet_bi | |
| ngay_sua_chua truoc | |
| ghi_chu truoc | |

Goi y:
- Chup anh man hinh row goc truoc khi test de lam bang chung.

---

## 6) Checklist thuc thi sau khi Manager them dong test
1. Mo app tren RMX3081.
2. Bam **Dong bo day du**.
3. Tim lai dung `ma_thiet_bi`/record vua test.
4. Kiem tra app da hien thi:
   - `ngay_sua_chua = 2026-05-05`
   - `ghi_chu` dung marker `CODEX_TEST_REPAIR_EXACT_20260505_<timestamp>`
5. Mo man hinh sync status va xac nhan:
   - pending = 0 (hoac khong tang bat thuong)
   - retry error = 0
   - queue khong loi bat thuong

Neu thay dau hieu ghi nham dong/cot/sheet: **dung ngay** va bao Manager.

---

## 7) Rollback ngay sau khi verify
1. Quay lai sheet Sua chua.
2. Tim dung dong co marker `CODEX_TEST_REPAIR_EXACT_*` va xoa **dung 1 dong do**.
3. Neu ban ghi DMBT bi doi gia tri ngoai du kien, khoi phuc dung gia tri trong bang BEFORE:
   - `ngay_sua_chua truoc`
   - `ghi_chu truoc`
4. Bam **Dong bo day du** lai tren app.
5. Xac nhan sau rollback:
   - Khong con marker `CODEX_TEST_REPAIR_EXACT_*` tren sheet that.
   - App/local khop lai gia tri mong doi sau rollback.

---

## 8) Bang ghi ket qua cho Manager (dien sau khi chay that)
| Muc | Ket qua |
|---|---|
| Thoi gian chay | |
| Nguoi thao tac sheet | |
| REAL_RECORD_ID da dung | |
| REAL_DEVICE_CODE da dung | |
| Marker da dung | |
| App cap nhat dung ngay_sua_chua/ghi_chu | PASS / FAIL |
| Pending/retry sau sync | |
| Rollback hoan tat | YES / NO |
| Marker da xoa het khoi sheet that | YES / NO |
| Ket luan REPAIR-01 | PASS / FAIL |
| Ghi chu su co (neu co) | |

---

## 9) Thong tin bat buoc con thieu (can Manager cung cap neu chua co)
1. Xac nhan candidate `seed-beta-dmbt-2022-r5` / `523BC03` dung tren app/sheet.
2. `gid/sheetId` cua sheet nguon ban ghi neu can ghi bang chung theo sheet.
3. Anh/chung cu BEFORE cua row goc truoc khi them dong test.

Khong co cac thong tin tren thi khong nen thuc thi REPAIR-01 tren sheet that.

## 10) Manager review
- Scope compliance: PASS. Agent 1 chi tao packet, khong sua code, khong ghi sheet that.
- Safety compliance: PASS. Packet cam dung `CODEX_TEST_*` lam `record_id` va yeu cau rollback.
- Gap da duoc Manager bo sung: them 1 candidate local de giam mo ho.
- Decision: co the chuyen sang buoc human-in-the-loop, nhung Manager/operator phai xac nhan candidate tren app/sheet truoc khi them dong test.
