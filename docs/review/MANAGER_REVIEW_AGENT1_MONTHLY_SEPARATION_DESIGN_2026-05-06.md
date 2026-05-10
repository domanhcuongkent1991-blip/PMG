# Manager Review - Agent 1 Monthly Separation Design - 2026-05-06

## Ket luan

Trang thai: **Duyet thiet ke, cho phep chuyen sang code theo phase nho**.

Manager dong y voi de xuat cua Agent 1: **chon phuong an A**.

Phuong an A nghia la:

- Khong tao bang DB moi trong phase dau.
- Tiep tuc dung chung `device_logs`.
- Tach DMBT nam, DMBT thang, Sua chua thang bang `sourceSheetId`/gid va stream logic rieng.
- Khong cho monthly fail lam fail DMBT nam.
- Khong cho monthly merge/push cheo sang yearly.

## Diem Agent 1 lam dung

1. Xac dinh dung monthly hien dang tron vao DMBT nam o config, remote, repository, repair merge va UI.
2. So sanh A/B/C ro rang.
3. Khong de xuat tao bang moi ngay, tranh migration risk voi du lieu that.
4. Xac dinh dung rule quan trong: repair monthly chi duoc merge vao monthly DMBT, khong merge sang DMBT nam.
5. De xuat yearly la mandatory, monthly la optional-with-warning. Day la huong on dinh de release.

## Dieu Manager chot lai

### Chon A, khong chon B luc nay

Khong tao `monthly_device_logs` trong phase dau. Tao bang moi co ve sach, nhung rui ro migration va UI hop nhat qua cao trong giai do dang on dinh du lieu that.

### Phase code dau tien phai nho

Khong code tat ca design trong mot luot.

Phase 1 chi nen lam:

1. Tach DMBT sheet bindings thanh yearly vs monthly trong logic.
2. Pull yearly va monthly rieng.
3. Yearly fail thi full sync fail.
4. Monthly fail thi warning/skip, khong lam yearly fail.
5. Khong doi DB schema.
6. Khong doi Google Sheet.
7. Khong doi UI lon ngoai pham vi can thiet.

### Chua lam ngay

1. Chua them `sourceType` DB.
2. Chua tao bang monthly rieng.
3. Chua refactor repository lon.
4. Chua doi sync queue operation neu chua can.

## Rui ro can canh

1. Neu chi tach pull ma chua tach merge, monthly van co the merge vao yearly bang business key.
2. Neu UI van classify monthly bang text/seed prefix, user van co the thay sai nhom.
3. Neu repair monthly merge vao all local recordIds, van co nguy co update sai yearly row.

Vi vay sau Phase 1 phai tiep Phase 2:

- Partition merge/router theo stream.
- Monthly repair chi resolve trong monthly candidates.
- UI category dua tren sourceSheetId, khong dua tren text.

## Quyet dinh tiep theo

Giao Agent 1 code **Phase 1: Monthly fault isolation + pull separation**.

Dieu kien thanh cong Phase 1:

- DMBT yearly pull khong phu thuoc monthly gid.
- Monthly gid missing/fail khong lam yearly fail.
- Build/test pass.
- Co report Agent 1 sau code.

Sau khi Manager review Phase 1 pass moi giao Phase 2.
