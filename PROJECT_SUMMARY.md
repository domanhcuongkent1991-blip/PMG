# TOM TAT DU AN

## Muc tieu
Xay app Android de quan ly va tra cuu thiet bi bat thuong, lay `ma_thiet_bi` lam trung tam tim kiem, local-first va sync len Google Sheet sau.

## Rule nghiep vu da khoa
1. `ma_thiet_bi` la khoa tra cuu, khong phai ID ban ghi.
2. Moi ban ghi phai co `record_id` rieng.
3. Khong dung `STT` lam ID chinh.
4. Trang thai sua chua suy ra tu `ngay_sua_chua`:
   - co ngay -> da sua
   - trong -> chua sua
5. Khong cho phep nhap tay trang thai sua chua.
6. Mapping Google Sheet bat buoc theo vai tro + `sheetId`, khong theo ten tab/thu tu tab.

## Pham vi du lieu hien tai
1. Khu 1: DMBT theo nam (`DMBT 2022..2026`).
2. Khu 2: DMBT theo thang (`DMBT T4.2026`).
3. Khu 3: Sua chua theo thang (`Sua chua T4.2026`).
4. Khu 4: HGT dinh ky.

## Muc tieu MVP
1. Tim kiem theo `ma_thiet_bi`.
2. Loc `Tat ca/Da sua/Chua sua`.
3. Dieu huong ro 4 khu trong sidebar.
4. Them/sua ban ghi voi validation ro rang.
5. Ho tro offline save + pending sync + retry.
6. HGT: sua `lan_gan_nhat`, tu tinh `lan_tiep_theo`.

## Van de quan tri can siet
1. Loi lap lai phai co RCA bat buoc.
2. Moi task lon phai qua plan gate truoc khi code.
3. Nhanh test flow nut bam can co checklist + evidence moi.
4. Worklog phai theo ngay, UTF-8 sach, co risk con lai.

## Tai lieu plan chinh
- [PROJECT_PLAN_TIGHTENED_V3_4_2026-04-26.md](F:/codex_android_gsheet_full_pack/PROJECT_PLAN_TIGHTENED_V3_4_2026-04-26.md)
