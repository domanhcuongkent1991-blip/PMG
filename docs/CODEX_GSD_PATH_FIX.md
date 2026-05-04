# Codex GSD Path Fix

## Van de

Codex Desktop co the luu thread path trong `C:\Users\Admin\.codex\state_5.sqlite` theo 2 dang khac nhau:

- `C:\Users\Admin\.codex\sessions\...`
- `\\?\C:\Users\Admin\.codex\sessions\...`

Khi resume thread cu, Codex so sanh 2 dang nay nhu 2 path khac nhau va bao loi:

```text
cannot resume running thread ... with mismatched path
```

Day la state toan cuc cua Codex App, khong phai loi rieng trong Android app hay GSD planning files.

## Kiem tra nhanh truoc moi phien GSD

Chay trong `F:\codex_android_gsheet_full_pack`:

```powershell
npm run gsd:health:strict
```

Kiem tra toan cuc tat ca project/thread Codex:

```powershell
npm run gsd:health:global:strict
```

## Sua triet de state toan cuc

Chay tu PowerShell ben ngoai Codex App:

```powershell
npm run gsd:repair:global
```

Neu muon mo lai Codex sau khi sua:

```powershell
npm run gsd:repair:global:relaunch
```

## Dieu kien PASS

Ket qua mong muon:

```text
with_extended_prefix = 0
mismatch_db_vs_session_cwd = 0
rollout_missing = 0
```

## Luu y

Neu sau khi sua ma loi quay lai, Codex Desktop dang ghi lai path dang `\\?\...`. Khi do can update Codex Desktop len ban moi nhat roi chay lai:

```powershell
npm run gsd:repair:global
```
