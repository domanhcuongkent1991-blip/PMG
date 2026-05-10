# Manager Review - Agent 1 DMBT vs HGT root cause - 2026-05-06

## Ket luan quan ly

Bao cao Agent 1 dung huong, nhung chua du "triet de" neu tiep tuc sua bang business key. Ket luan chinh cua Manager:

**Muon on dinh that su, DMBT/Sua chua phai duoc dua ve co che dinh danh on dinh nhu HGT: moi row can co identity ro rang, app khong duoc tiep tuc doan row bang noi dung.**

HGT on dinh vi domain cua HGT gan nhu co 1 key tu nhien: `ma_thiet_bi`. DMBT khong co key tu nhien don gian, vi mot ma thiet bi co the co nhieu loi qua nhieu nam/thang. Do do DMBT bat buoc can `record_id` on dinh tren Sheet va `sourceSheetId/gid` trong local.

## Danh gia bao cao Agent 1

### Diem dung

1. Xac dinh dung khac biet lon nhat: HGT la 1 sheet/1 role/1 key don gian; DMBT la multi-sheet + repair sheet + legacy data.
2. Xac dinh dung P0: `sourceSheetId=null` tren DMBT legacy lam app khong biet row thuoc sheet nao.
3. Xac dinh dung rui ro identity drift: recordId co the base/namespaced/legacy, gay skip hoac duplicate.
4. Xac dinh dung khong nen copy nguyen xi HGT sang DMBT.

### Diem chua du

1. Bao cao van nghieng ve tiep tuc "harden business key". Business key chi la fallback, khong nen la nen tang chinh cho sync 2 chieu du lieu that.
2. Chua noi du manh ve can co **one-time migration/backfill identity tren Google Sheet**.
3. Chua tach ro:
   - fix tam thoi: content-diff, business-key fallback, sourceSheetId backfill khi unique.
   - fix triet de: moi row DMBT co `record_id` on dinh va local co provenance.
4. Chua de xuat gating truoc khi bat ghi nguoc hang loat: neu sheet chua du identity thi chi pull/diagnostic, khong push am tham.

## Nguyen nhan goc sau khi doi chieu code

### Root cause 1 - DMBT khong co key tu nhien du manh

HGT co the match theo `ma_thiet_bi`, vi moi thiet bi HGT thuong la 1 muc can theo doi. DMBT thi khac: mot `ma_thiet_bi` co the co nhieu loi trong nhieu sheet, ngay khac nhau, tinh trang gan giong nhau.

Neu DMBT khong co `record_id` on dinh, app buoc phai doan bang:

- ma thiet bi
- ngay phat hien
- hang muc
- tinh trang

Cach doan nay co the dung voi nhieu case, nhung khong bao dam 100% khi du lieu that co duplicate, sua text, sai dau/cach, hoac record_id doi format.

### Root cause 2 - Local legacy thieu `sourceSheetId`

Nhieu row DMBT trong app co `sourceSheetId=null`. Khi push, app khong biet row do thuoc DMBT 2025 hay DMBT 2026 hay DMBT thang.

Code hien tai da co fail-safe: neu khong resolve duoc sheet dich thi khong day nham default. Day la dung ve an toan, nhung user se thay "khong sync duoc".

### Root cause 3 - Sheet edit tay khong cap nhat `updated_at`

Van de nay da duoc Manager fix truoc do bang content-diff fallback. Tuy nhien day chi la mot phan cua loi Sheet -> app, khong giai quyet het identity/provenance.

### Root cause 4 - Repair sheet phu thuoc vao DMBT local da resolve duoc identity

Sheet `Sua chua T*.2026` khong nen tao row DMBT moi. No chi merge vao row DMBT da co. Neu DMBT local chua co identity on dinh, repair merge se skip an toan.

Skip an toan tot hon merge nham, nhung neu skip nhieu thi user thay "Sua chua khong ve app".

## Co nen lay HGT lam nen tang khong?

Co, nhung chi lay pattern:

1. Local-first: khong ghi de `PENDING/FAILED`.
2. Neu `updated_at` thieu thi so sanh noi dung.
3. Update row cu truoc, append sau.
4. Mark synced chi khi local khong doi trong luc push.
5. Route sheet bang gid, khong bang ten tab.

Khong the lay nguyen logic HGT vi HGT co key don gian, con DMBT can identity rieng cho tung loi.

## Phuong an fix triet de

### Phase 1 - Identity audit, khong ghi du lieu hang loat

Muc tieu: biet chinh xac moi sheet DMBT dang thieu gi.

Can lam:

1. Them diagnostic/dry-run cho 6 sheet DMBT:
   - DMBT 2022
   - DMBT 2023
   - DMBT 2024
   - DMBT 2025
   - DMBT 2026
   - DMBT thang gid 1383308512
2. Dem:
   - tong row co ma thiet bi
   - row thieu `record_id`
   - row thieu `updated_at`
   - row duplicate business key
   - row parse bi skip
3. Xuat report de user xem truoc khi app backfill.

Ket qua mong muon: biet sheet nao co the sync an toan, sheet nao can backfill.

### Phase 2 - One-time backfill identity cho DMBT sheets

Muc tieu: moi row DMBT co `record_id` on dinh de app khong phai doan.

Can lam:

1. Neu sheet chua co cot `record_id`/`updated_at`, app/script phai them cot an toan hoac yeu cau user tao cot hidden.
2. Backfill `record_id` cho row blank theo format on dinh:
   - `dmbt-<gid>-r<rowNumber>-<shortHash>`
3. Backfill `updated_at` cho row blank bang timestamp tai thoi diem migration.
4. Khong sua noi dung nghiep vu: hang muc, ma thiet bi, tinh trang, ngay sua, ghi chu.
5. Ghi log rollback: sheet, rowNumber, old record_id, new record_id.

Ket qua mong muon: tu lan sync sau, app match row bang `record_id` truoc, business key chi con la fallback khan cap.

### Phase 3 - Local provenance migration

Muc tieu: local DB khong con phu thuoc `sourceSheetId=null`.

Can lam:

1. Sau khi pull sheet co record_id on dinh, app backfill local:
   - local recordId cu -> recordId on dinh hoac giu local id nhung luu `sourceSheetId`.
2. Chi backfill khi match unique.
3. Neu ambiguous, ghi skip reason, khong merge nham.

Ket qua mong muon: app -> Sheet route theo gid/sourceSheetId chac chan.

### Phase 4 - Push gate

Muc tieu: khong de app ghi nham sheet hoac append trung.

Can lam:

1. Neu DMBT record thieu `sourceSheetId` va khong co gid trong record_id, khong push record do.
2. Queue record loi phai giu lai, app bao message ro: "Ban ghi chua xac dinh duoc sheet nguon, can dong bo backfill truoc".
3. Cac record khac van duoc push, khong fail ca batch.

Ket qua mong muon: khong mat du lieu, khong ghi nham sheet, user biet ro record nao bi chan.

### Phase 5 - Repair merge sau khi DMBT identity on dinh

Muc tieu: Sua chua T*.2026 merge vao DMBT dung row.

Can lam:

1. Repair row phai dung `record_id` DMBT on dinh.
2. Neu repair sheet chi co base id/ma thiet bi, app chi merge khi resolve unique.
3. Bao cao skip reason cho not found/ambiguous.

Ket qua mong muon: repair sheet khong tao duplicate, khong merge nham.

## Viec nen giao tiep theo

Chua nen tiep tuc sua logic merge nho le. Nen giao Agent 1 lam task code/doc tiep theo:

**A1-DMBT-IDENTITY-AUDIT-DRY-RUN**

Muc tieu:

- Tao diagnostic read-only/dry-run de dem row thieu identity tren tung DMBT sheet.
- Chua backfill, chua ghi len Sheet.
- Tao report bang so lieu that de Manager quyet dinh co cho migration/backfill hay khong.

Neu bo qua buoc audit va tiep tuc fix bang business key, rui ro lap lai bug van cao.

## Ket luan release

Chua nen coi sync DMBT/Sua chua la on dinh de release. HGT co the xem la pattern tot, nhung DMBT can chot identity/provenance bang migration co kiem soat.

Trang thai hien tai:

- HGT: gan on dinh.
- DMBT: da co nhieu guard an toan hon, nhung chua triet de.
- Sua chua thang: phu thuoc DMBT identity, nen chua the ket luan on dinh.
