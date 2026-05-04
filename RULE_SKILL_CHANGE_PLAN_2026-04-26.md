# RULE SKILL CHANGE PLAN - 2026-04-26

Status: APPLIED (governance docs phase)
Scope: tighten project rule/skill workflow and MCP governance only.

## 1) Objectives
1. Remove loose process behavior that caused repeated mistakes.
2. Standardize reading -> planning -> execution -> verify -> handoff gates.
3. Define safe MCP usage boundaries for this Android + Google Sheets project.

## 2) Planned changes
### Change A - Rewrite runbook
File:
- `MCP_SKILL_RULE_RUNBOOK.md`

What changed:
- Added control-priority model.
- Added mandatory reading sequence and required receipt file.
- Added risk matrix and approval boundaries.
- Added explicit MCP routing and AUTO-MCP advisor step.
- Added release boundary checklist.

Why:
- Prevent repeated workflow drift and unclear tool usage.

### Change B - Add reading receipt artifact
File:
- `MANDATORY_READING_RECEIPT_2026-04-26.md`

Why:
- Ensure auditability before high-risk work.

### Change C - Add skill selection artifact
File:
- `SKILL_SELECTION_REPORT_2026-04-26.md`

Why:
- Record rationale for chosen skill/rule set and MCP route.

## 3) Validation plan
1. Confirm all new files exist and are readable.
2. Confirm runbook includes:
- risk matrix,
- mandatory reading,
- MCP policy,
- release gate.
3. Confirm worklog is updated for same date.

## 4) Rollback plan
If this governance update causes confusion or overhead:
1. Restore previous `MCP_SKILL_RULE_RUNBOOK.md` from git history.
2. Keep new report files as reference, mark them deprecated.
3. Re-apply only sections that team agrees to keep.

## 5) Out of scope for this phase
- App source code feature changes.
- UI redesign implementation.
- Data migration or sync logic changes.

## 6) Next execution slice suggestion
- Introduce one reusable template file (without date suffix) for:
  - reading receipt,
  - skill selection report,
  - release readiness report.
- Then enforce in every major task.
