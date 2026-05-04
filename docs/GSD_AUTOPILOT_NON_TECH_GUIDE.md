# GSD Autopilot Guide

## Muc tieu

Dung mot lenh trung tam de AI chay GSD dung cach cho tung project, nhung van chan loi Codex App truoc khi chay.

## Lenh khuyen dung

```powershell
powershell -ExecutionPolicy Bypass -File D:\GSD-GIAMSAT-PROJECT\scripts\Run-GsdAutopilot.ps1 -ProjectPath "F:\codex_android_gsheet_full_pack"
```

## Nguyen tac an toan quan trong

Autopilot luon chay Level 0 truoc:

```text
Codex session path health check
```

Neu con loi `\\?\...` trong Codex state, Autopilot se dung ngay. Day la hanh vi dung, khong phai loi.

## Khi bi chan o Level 0

Neu dang o repo da co package script, chay:

```powershell
npm run gsd:doctor
```

Neu ket qua van `WARN`, dong Codex Desktop, mo PowerShell ben ngoai Codex App, roi chay:

```powershell
npm run gsd:repair:codex
```

Neu can mo lai Codex sau khi sua:

```powershell
npm run gsd:repair:codex:relaunch
```

Lenh truc tiep tu repo trung tam:

```powershell
powershell -ExecutionPolicy Bypass -File D:\GSD-GIAMSAT-PROJECT\scripts\fix-codex-thread-path-hard.ps1 -StopCodexProcesses
```

Sau do mo lai Codex va chay lai Autopilot.

## Vi sao khong tu sua ngay trong Codex?

Vi neu dang o trong Codex App ma script tu dong tat Codex, chinh phien chat hien tai co the bi mat ket noi. Nen workflow chon cach an toan hon:

1. Phat hien som.
2. Dung lai.
3. Dua lenh sua ro rang.
4. Chi chay GSD khi state da sach.

## Profile theo project

- `full-gsd`: chay GSD day du cho project san pham that.
- `phase-gsd`: chi dung GSD cho phase lon hoac release.
- `audit-only`: chi doc, review, lap bao cao.
- `external-audit-only`: khong cai GSD vao repo ngoai/tool phu tro.
- `orchestrator`: repo dieu phoi trung tam.
- `governance-source`: repo nguon rule/skill/MCP.

## Dieu can nho

Neu Autopilot dung lai, do khong co nghia la that bai. No dang bao ve project khoi loi lap lai.
