# Mo ta du an DeviceTracker Android + Google Sheets

## 0) Trang thai tai lieu

Day la mo ta yeu cau san pham chinh thuc duoc user cap nhat ngay 2026-05-03. Neu co noi dung mau cu o ben duoi thi chi coi la tham khao cu, khong duoc dung de quyet dinh pham vi san pham.

## 1) Du an can lam gi?

Lam app Android de quan ly va tra cuu loi thiet bi. App ket noi voi Google Sheets va dong bo du lieu hai chieu:

- Khi nguoi dung nhap hoac chinh sua du lieu tren app, Google Sheet phai duoc cap nhat sau khi bam nut dong bo day du.
- Khi nguoi dung nhap hoac chinh sua du lieu tren Google Sheet, app phai cap nhat lai du lieu sau khi bam nut dong bo day du.
- App phai hoat dong duoc khi mat mang: du lieu nhap tren dien thoai duoc luu local truoc, sau khi co mang nguoi dung bam dong bo day du de day du lieu len Google Sheet.

## 2) Nguoi dung chinh la ai?

Nguoi dung la nhan su van hanh/bao tri thiet bi dung dien thoai Android de:

- Tra cuu loi theo ma thiet bi.
- Them loi thiet bi moi.
- Cap nhat ngay sua chua va ghi chu sua chua.
- Theo doi hang muc HGT dinh ky.

Nguoi dung co the khong ranh ky thuat, nen app can thong bao ro rang, thao tac don gian, khong de mat du lieu.

## 3) Cac sheet can dong bo

Tat ca sheet sau deu nam trong pham vi dong bo hai chieu giua app va Google Sheet:

| Sheet | Y nghia | Yeu cau dong bo |
|---|---|---|
| `DMBT 2022` | Du lieu DMBT nam 2022 | Hai chieu: app co the cap nhat Sheet, Sheet co the cap nhat app |
| `DMBT 2023` | Du lieu DMBT nam 2023 | Hai chieu |
| `DMBT 2024` | Du lieu DMBT nam 2024 | Hai chieu |
| `DMBT 2025` | Du lieu DMBT nam 2025 | Hai chieu |
| `DMBT 2026` | Du lieu DMBT nam 2026 | Hai chieu |
| `DMBT T4.2026` | Du lieu DMBT theo thang hien tai | Hai chieu; ten sheet thay doi theo thang, vi du sang thang 5 co the la `DMBT T5.2026` |
| `Sua chua T4.2026` | Du lieu sua chua theo thang hien tai | Hai chieu; ten sheet thay doi theo thang, vi du sang thang 5 co the la `Sua chua T5.2026` |
| `HGT dinh ky` | Du lieu hang muc HGT dinh ky | Hai chieu |

Ghi chu quan trong:

- Khong duoc dua logic phu thuoc vao ten sheet co dinh cho `DMBT T4.2026` va `Sua chua T4.2026`, vi ten sheet thay doi theo thang.
- Nguoi dung se tu doi ten sheet theo tung thang trong Google Sheets. Neu chi doi ten tab hien co thi `gid/sheetId` khong doi, app phai van dong bo binh thuong.
- Khi sang thang moi, user se doi ten tab cu, vi du `DMBT T4.2026` thanh `DMBT T5.2026`; app van phai sync bang `gid/sheetId`.
- App nen dung `gid/sheetId` hoac cau hinh role an toan de biet sheet nao la sheet nao.
- Tat ca sheet hai chieu chi duoc bat sau khi co contract cot, khoa chinh, rule conflict, test va rollback.
- User cho phep test truc tiep tren Google Sheet that, khong bat buoc tao workbook test copy. Tuy nhien moi lan test ghi that phai lam theo buoc nho, co dry-run/ghi log, tranh ghi hang loat.
- Voi cac sheet DMBT cu nhu `DMBT 2022`, `DMBT 2023`, `DMBT 2024`, `DMBT 2025`, thao tac chinh la nhap `ngay_sua_chua` va `ghi_chu` de chuyen loi sang da sua. "Xoa loi" trong ngu canh nay duoc hieu la danh dau da sua, khong xoa row khoi Sheet.

## 4) Tra cuu du lieu

Tra cuu chinh trong app dung `ma_thiet_bi` / "Ma thiet bi".

Nguoi dung nhap ma thiet bi de xem cac loi lien quan, gom:

- Loi chua sua.
- Loi da sua.
- Lich su DMBT theo nam/thang.
- Thong tin HGT dinh ky neu co.

## 5) Trang thai sua chua cua loi thiet bi

Co 2 trang thai nghiep vu:

| Trang thai | Cach phan biet |
|---|---|
| Chua sua | O/cot ngay sua chua de trong |
| Da sua | O/cot ngay sua chua co gia tri |

Khong dung `syncStatus` de phan biet da sua hay chua sua. `syncStatus` chi dung de biet du lieu da dong bo len Google Sheet hay chua.

## 6) Luong cap nhat ngay sua chua

Khi muon chuyen mot loi tu "chua sua" sang "da sua":

1. Nguoi dung mo ban ghi loi.
2. Bam "Cap nhat ngay sua".
3. Nhap ngay sua chua.
4. Nhap ghi chu ve van de sua chua.
5. App luu local truoc.
6. Khi co mang, nguoi dung bam dong bo day du.
7. Google Sheet duoc cap nhat tuong ung.

Neu Google Sheet duoc sua truoc, app cung phai keo ve va hien thi trang thai da sua/chua sua dung theo ngay sua chua.

## 7) Offline va dong bo

App phai uu tien local-first:

- Mat mang van nhap duoc du lieu.
- Du lieu offline khong duoc mat.
- Du lieu offline duoc danh dau dang cho dong bo.
- Khi co mang, nguoi dung bam nut dong bo day du de day/keo du lieu.
- Neu dong bo loi, app phai giu du lieu local va bao loi de nguoi dung biet.

## 8) Quy mo du lieu va hieu nang

Du lieu loi co the len den khoang 10.000 ban ghi hoac hon.

Yeu cau:

- App khong duoc giat lag khi tra cuu.
- Khong load toan bo du lieu len UI mot cach gay cham.
- Tra cuu theo ma thiet bi phai nhanh.
- Dong bo phai chia buoc an toan, tranh lap vo han hoac duplicate hang loat.

## 9) Cho nao khong duoc phep sai?

Nhung loi nghiem trong can tranh:

- Mat du lieu da nhap tren app khi chua dong bo.
- Ghi sai sheet.
- Ghi sai dong/cot.
- Duplicate hang loat sau khi retry sync.
- Sheet A ghi nham sang sheet B.
- Sheet doi ten theo thang lam app khong tim duoc sheet.
- Dong bo bao thanh cong gia nhung du lieu chua len Google Sheet.
- Google Sheet sua roi nhung app khong keo ve sau dong bo day du.
- App bi giat/treo khi du lieu lon.

## 10) Dinh nghia xong viec

Du an duoc coi la hoan thien khi:

- 8 sheet trong pham vi deu co cau hinh/role/contract ro rang.
- Tat ca 8 sheet deu dong bo hai chieu dung theo yeu cau.
- App co the them va chinh sua du lieu khi offline.
- Sau khi bam dong bo day du, du lieu offline duoc cap nhat len Google Sheet.
- Sau khi Google Sheet duoc sua, bam dong bo day du thi app cap nhat lai dung.
- Tra cuu theo ma thiet bi hoat dong nhanh voi khoang 10.000 ban ghi.
- Trang thai da sua/chua sua dung theo ngay sua chua.
- Cap nhat ngay sua va ghi chu sua chua hoat dong dung.
- HGT dinh ky dong bo dung va canh bao lich kiem tra hoat dong tren Android that.
- Unit test pass.
- Build debug pass.
- UAT tren workbook test pass truoc khi dung workbook that.
- Co rollback plan de tat ghi nguoc neu phat hien loi sync.
- Khong co token/secret that bi commit hoac ghi vao log.

<!-- Noi dung mau cu da duoc xoa de tranh nham lan voi PRD chinh thuc. -->

<!-- Mau cu da xoa.
3. dong bo du lieu giua app và google sheet
4. co the them va chinh sua du lieu trong app
5. có canh bao khi den lich kiem tra

## 4) Khong duoc phep sai o dau? (an toan/chat luong)
Vi du: "Khong mat du lieu, phai dam bao dong bo giua google sheet va app."

## 5) Rang buoc neu co
Vi du: "app chay tren mobile he dieu hanh android."

## 6) Dinh nghia xong viec (Done)
Vi du:
- dong bo hoan toan giua google sheet va app.
- Tao/sua/xoa cong viec dung.
- Co the tim kiem du lieu theo ma thiet bi.
- dong bo xong tat ca cac sheet ben trong google sheet vao app.
-->
