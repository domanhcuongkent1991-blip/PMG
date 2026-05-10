# WORKFLOW GOVERNANCE HARDENING PLAN

## Goal

Make the project workflow safer and more stable by removing manual approval JSON edits, adding validation, and giving the owner one clear check command.

## Scope

- Add canonical workflow artifacts required by the existing gate.
- Add script-assisted MCP approval evidence.
- Add script-assisted phase plan approval evidence.
- Add schema/hash validation for workflow governance files.
- Add one aggregate workflow check command.
- Add tests for the workflow governance scripts.
- Update the daily worklog with verification evidence and residual risks.

## Out Of Scope

- No Android product behavior changes.
- No Google Sheet writes.
- No secret or credential file access.
- No release or production approval.

## Validation

- `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-workflow-governance.ps1`
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\validate-workflow-governance.ps1 -ProjectRoot . -Json`
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\.workflow-gate\check-workflow-order.ps1 -ProjectRoot . -EnforceSourceApproval`
- `npm run workflow:check`

## Rollback

- Remove the added workflow scripts and canonical artifacts.
- Restore `.workflow-gate/state.json`, `.workflow-gate/approval-ledger.jsonl`, and `.workflow-gate/MCP_RISK_REPORT.meta.json` from git if needed.
- Re-run the original workflow order gate to confirm the previous state.
