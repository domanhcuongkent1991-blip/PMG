# E2E Workflow Improvement Plan - 2026-04-26

Status: APPROVED_FOR_EXECUTION
Owner priority: stability, safety, low-error, reusable for all projects.

## 1) Objective
Build a universal end-to-end operating layer so any AI that reads the three workflows can automatically know:
- what to read first,
- what to output next,
- what gates must pass,
- when to stop and ask,
- when it is safe to continue.

## 2) Scope
In scope:
- planning workflow hardening,
- rule/skill workflow hardening,
- MCP workflow hardening,
- cross-workflow handshake contract,
- validation and daily operations layer.

Out of scope:
- app feature implementation,
- production deployment changes,
- destructive migration or data deletion.

## 3) End-to-end contract
### 3.1 Canonical sequence
1. PLAN-WORKFLOW produces approved plan outputs.
2. Rule/Skill workflow consumes plan handoff + selects safe skill set.
3. AUTO-MCP consumes plan + skill artifacts and resolves tool plan.
4. Implementation runs only after EXECUTION_READY.
5. Release/handoff runs only after RELEASE_READY.

### 3.2 Required handoff artifacts
- `11_APPROVED_PROJECT_PLAN.md`
- `12_HANDOFF_PACKAGE.md`
- `13_WORKFLOW_HANDSHAKE.json`
- `SKILL_SELECTION_REPORT.md`
- `MANDATORY_READING_RECEIPT*.md`

### 3.3 Shared statuses
- `NEED_MORE_INFO`
- `PLAN_DRAFT_READY`
- `PLAN_REVIEW_READY`
- `PLAN_APPROVED_FOR_HANDOFF`
- `EXECUTION_READY`
- `RELEASE_READY`

## 4) Improvement phases
### Phase A - Contract and routing alignment
Actions:
1. Add machine-readable handshake to planning outputs.
2. Force rule/skill workflow to read handoff artifacts before implementation routing.
3. Force MCP workflow to align with handoff constraints and skill report.

Exit criteria:
- all three workflows document same sequence and transition logic.

### Phase B - Safety and stability controls
Actions:
1. enforce least-privilege MCP policy,
2. enforce explicit confirmation for risky write actions,
3. enforce stop-the-line for repeated severe failures.

Exit criteria:
- risk controls are documented and testable.

### Phase C - Validation and observability
Actions:
1. run all validators and regression suites,
2. create one-command health-check runner,
3. produce a dated health report.

Exit criteria:
- all suites pass,
- report generated with PASS/FAIL summary and timestamps.

### Phase D - Daily operation standardization
Actions:
1. publish one-page operator guide,
2. publish standard AI command pack,
3. publish KPI and incident policy templates.

Exit criteria:
- non-tech owner can operate and audit workflow daily.

## 5) Risk controls
1. No destructive command in workflow operations.
2. No secret exposure in reports/logs.
3. No external publish/deploy without explicit approval.
4. Keep changes revertible and file-scoped.
5. Validate after every governance change.

## 6) Acceptance criteria
1. End-to-end command path is documented and executable.
2. AI can classify and continue without manual technical routing.
3. Validators pass for all three workflow systems.
4. Regression includes at least one E2E orchestration case.
5. Daily operator checklist exists and is actionable.

## 7) Rollback
If governance update causes confusion:
1. restore previous changed files from backup/version control,
2. keep generated reports for audit,
3. re-apply only contract and test pieces first.

## 8) Completion evidence required
- validator outputs,
- regression outputs,
- generated health report path,
- worklog update.
