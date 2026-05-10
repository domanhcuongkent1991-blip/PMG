---
description: 
alwaysApply: true
---

# AI Bootstrap Contract

Ban la AI agent phai tu van hanh repo nay theo uu tien:
1. An toan va bao mat truoc.
2. On dinh, it loi, de bao tri.
3. Tu dong toi da cho nguoi non-tech.

## Bat buoc truoc khi chay

1. Doc `input/prd.md`.
2. Chay preflight:
   - `node --version`
   - `npm --version`
   - `npx --version`
   - `git --version`
3. Neu thieu cong cu, dung lai va thong bao cach cai.

## Quy tac van hanh

1. Khong sua xoa file nhay cam.
2. Khong ghi secret vao log.
3. Uu tien profile `safe` neu user khong chi dinh.
4. Neu gap loi, retry toi da 2 lan cho moi buoc.
5. Neu van loi, dung lai va ghi ro:
   - Buoc nao loi
   - Nguyen nhan kha nghi
   - Lenh fix de user copy/paste
6. Truoc moi commit, phai chay `node scripts/prevent-secrets.js`.
7. Bat buoc ghi worklog/handoff khi co quyet dinh quan trong, fix P0/P1, UAT that, cai APK len dien thoai, hoac khi ket thuc mot ngay lam viec:
   - Tao/cap nhat `WORKLOG_YYYY-MM-DD.md` o root repo.
   - Ghi ro muc tieu, viec da lam, file/report da tao, lenh verify da chay, ket qua, loi moi truong, rui ro con lai, va buoc tiep theo.
   - Neu co giao viec cho Agent khac, ghi Task ID, muc tieu, pham vi duoc sua/cam sua, va dieu kien hoan thanh.
   - Khong ghi secret/token vao worklog.
   - Khong danh dau PASS neu chua co bang chung test/build/UAT tuong ung.

## Quy tac bao mat

1. Tu choi doc cac file khop pattern trong `policies/secure-files.txt` tru khi user yeu cau ro rang.
2. Khong commit cac file secret.
3. De xuat su dung `.env.example` thay vi `.env` that.

## Dinh nghia thanh cong

- GSD da duoc cai cho runtime.
- `@gsd-build/sdk` da san sang.
- `input/prd.md` da duoc dung de `init`.
- Auto pipeline da chay.
- Co file `out/bootstrap-report.md` tong ket ket qua.
