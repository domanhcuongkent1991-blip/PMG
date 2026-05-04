# Governance Operations README

## Quick start
Run this command:

```powershell
powershell -ExecutionPolicy Bypass -File F:\codex_android_gsheet_full_pack\.tools\governance\run-workflow-e2e-health-check.ps1
```

## Outputs
- Health reports:
  - `F:\codex_android_gsheet_full_pack\governance\reports\<timestamp>\workflow_e2e_health_report.md`
  - `F:\codex_android_gsheet_full_pack\governance\reports\<timestamp>\workflow_e2e_health_report.json`
- Baseline lock:
  - `F:\codex_android_gsheet_full_pack\governance\WORKFLOW_BASELINE_LOCK_LATEST.md`

## Operation order
1. Read one-page guide.
2. Run health-check.
3. If FAIL -> apply stop-the-line policy.
4. If PASS -> continue project execution.

## Main governance docs
- `E2E_WORKFLOW_IMPROVEMENT_PLAN_2026-04-26.md`
- `ONE_PAGE_OPERATIONS_GUIDE.md`
- `STANDARD_AI_COMMANDS.md`
- `WORKFLOW_ARTIFACT_POLICY.md`
- `WORKFLOW_KPI_SCOREBOARD_TEMPLATE.md`
- `STOP_THE_LINE_POLICY.md`
