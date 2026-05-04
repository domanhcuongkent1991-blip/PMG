# One-Page Operations Guide (Daily)

Goal: run 3-workflow governance safely with one repeatable routine.

## Daily routine (10-15 minutes)
1. Run E2E health check script.
2. Read PASS/FAIL summary in generated report.
3. If any FAIL appears, trigger stop-the-line policy.
4. If all PASS, continue normal project work.

## One command
```powershell
powershell -ExecutionPolicy Bypass -File F:\codex_android_gsheet_full_pack\.tools\governance\run-workflow-e2e-health-check.ps1
```

## Required artifacts per major task
- `MANDATORY_READING_RECEIPT_YYYY-MM-DD.md`
- `SKILL_SELECTION_REPORT_YYYY-MM-DD.md`
- `WORKLOG_YYYY-MM-DD.md`
- release artifact only when boundary crossed

## Safe boundaries
- no destructive command,
- no secret in report/log,
- no external share/deploy without explicit approval.

## Fast interpretation
- PASS: continue task execution.
- FAIL: stop new feature work, open incident, fix root cause first.

## Escalation
Escalate immediately when:
- repeated failure same category >= 2,
- validator mismatch across workflows,
- unknown write action outside expected scope.
