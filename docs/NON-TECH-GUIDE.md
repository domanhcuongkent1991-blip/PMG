# Huong dan cho nguoi non-tech

## Muc tieu
Ban khong can biet code van co the cho AI khoi tao va van hanh GSD tu dau den cuoi.

## Buoc 1: Dien de bai

Mo file `input/prd.md` va tra loi ngan gon cac muc.

Meo de AI lam dung:
- Viet ro "Ai la nguoi dung?"
- Viet ro "3 tinh nang quan trong nhat"
- Viet ro "Khong duoc sai o dau?"

## Buoc 2: Chay tu dong

Khuyen dung Autopilot khi chay cho project that:

```powershell
powershell -ExecutionPolicy Bypass -File D:\GSD-GIAMSAT-PROJECT\scripts\Run-GsdAutopilot.ps1 -ProjectPath "F:\codex_android_gsheet_full_pack"
```

Autopilot se chan truoc neu Codex App dang co loi path `\\?\...`, de tranh lap lai loi khong tiep tuc duoc doan chat.

Lenh bootstrap truc tiep trong project hien tai:

Trong PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-end-to-end.ps1 -Runtime codex -SafetyLevel safe
```

Neu ban muon AI cai cho project khac tu xa:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\bootstrap-target-project.ps1 -TargetProjectPath "F:\codex_android_gsheet_full_pack" -Runtime codex -SafetyLevel safe -RunInstall
```

## Buoc 3: Xem bao cao

Mo file `out/bootstrap-report.md`:
- Neu `Status: SUCCESS`: quy trinh da chay xong.
- Neu `Status: FAILED`: xem cot `Error` de biet buoc loi.

## Neu muon test truoc khi chay that

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-end-to-end.ps1 -DryRun
```

## Khuyen nghi an toan

1. Khong dan API key, password vao `prd.md`.
2. Dung `.env` local, khong commit.
3. Giu `SafetyLevel safe` cho du an that.
4. Chay `balanced` chi khi da quen quy trinh.
5. `fast` chi dung cho prototype.

## Neu lo commit `.env` (xu ly khan)

1. Doi toan bo secret ngay (rotate key/password/token).
2. Xoa `.env` khoi git:
```powershell
git rm --cached .env
git commit -m "remove .env"
```
3. Neu da push len remote, nho ky thuat ho tro rewrite history.
4. Tu buoc nay tro di, hook pre-commit cua repo se chan lai loi nay.
