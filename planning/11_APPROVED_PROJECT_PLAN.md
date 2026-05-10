# APPROVED PROJECT PLAN

Canonical artifact for workflow gate checks.

This file records that governed project execution is allowed to follow the current project sources of truth. It does not mark the product complete.

## Sources Of Truth

- `input/prd.md`
- `PROJECT_COMPLETION_PLAN.md`
- `AGENTS.md`
- `.workflow-gate/MCP_EXECUTION_PLAN.json`
- `.workflow-gate/MCP_RISK_REPORT.md`

## Approved Execution Scope

- Continue DeviceTracker Android + Google Sheets work under local-first, restricted-data safety rules.
- Require plan, approval, verification, and worklog evidence for risky or P0/P1 changes.
- Keep Google Sheet writes controlled, small, logged, and explicitly approved.
- Keep secrets out of logs, source control, and generated reports.

## Workflow Gate Requirement

The gate may proceed only when the following canonical artifacts exist and validate:

- `planning/11_APPROVED_PROJECT_PLAN.md`
- `SKILL_SELECTION_REPORT.md`
- `.workflow-gate/MCP_EXECUTION_PLAN.json`
- `.workflow-gate/MCP_RISK_REPORT.md`
- `.workflow-gate/state.json`
- `.workflow-gate/approval-ledger.jsonl`

## Approval Note

The user requested workflow hardening and approved proceeding in chat on 2026-05-06. This approval covers local governance workflow fixes and validation only, not broad production data writes.
