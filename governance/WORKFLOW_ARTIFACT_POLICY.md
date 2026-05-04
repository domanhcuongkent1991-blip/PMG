# Workflow Artifact Policy

## Rule
No major implementation task may proceed unless required governance artifacts exist.

## Required by phase
### Plan phase
- `11_APPROVED_PROJECT_PLAN.md`
- `12_HANDOFF_PACKAGE.md`
- `13_WORKFLOW_HANDSHAKE.json`

### Skill/routing phase
- `MANDATORY_READING_RECEIPT*.md`
- `SKILL_SELECTION_REPORT*.md`

### Execution/verification phase
- evidence logs from validators/tests
- `WORKLOG_YYYY-MM-DD.md`

### Release/handoff phase
- release readiness report
- secret/log redaction confirmation
- rollback notes for high-risk changes

## Enforcement
- Missing artifact => status is `NEED_MORE_INFO` (or blocked), not done.
- Artifact inconsistent with current scope => stop and re-plan.
