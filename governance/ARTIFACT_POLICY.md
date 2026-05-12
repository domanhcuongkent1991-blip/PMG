# GSD Artifact Policy

## Purpose

Keep the product repository clean while still preserving enough evidence for safe GSD workflow decisions.

This policy separates files into three classes:

- Product source
- Governance evidence
- Runtime evidence

## Product Source

Product source must be reviewed and committed intentionally.

Examples:

- `android-mvp/app/src/**`
- Gradle files
- production scripts used by the Android project
- unit tests and integration tests

Rules:

- Do not hide product source changes with `.gitignore`.
- Stage product source separately from governance artifacts.
- Require tests/build/UAT evidence that matches the risk of the change.

## Governance Evidence

Governance evidence may be committed when it records an approved decision or a completed phase.

Examples:

- `planning/11_APPROVED_PROJECT_PLAN.md`
- `SKILL_SELECTION_REPORT.md`
- `.workflow-gate/check-workflow-order.ps1`
- `.workflow-gate/MCP_EXECUTION_PLAN.json`
- `.workflow-gate/MCP_RISK_REPORT.md`
- `.workflow-gate/approval-ledger.jsonl`
- `.planning/phases/**/*
- `WORKLOG_YYYY-MM-DD.md`
- `docs/review/*.md`
- `docs/uat/results/*.md`
- `governance/reports/**/*.md`

Rules:

- Prefer concise Markdown summaries over large raw files.
- Every completed phase should end with either a commit, an archive action, or an explicit cleanup note.
- Approval ledger entries are append-only evidence.
- Mutable generated state should be treated carefully; when possible, regenerate it from the ledger.

## Runtime Evidence

Runtime evidence is useful for debugging but should not normally be committed.

Examples:

- copied app databases
- logcat dumps
- XML window dumps
- screenshots
- APK zip comparisons
- raw command output text

Default location:

- `docs/uat/evidence/**` for local evidence during active work
- `out/gsd-runs/**` for temporary workflow output
- `.gsd-runtime/**` for local agent runtime output

Rules:

- Runtime evidence is ignored by default.
- Commit a small report that references local evidence path, hash, device, command, and result.
- Do not commit secrets, tokens, raw credentials, or local `local.properties`.

## Clean Workflow

Before ending a GSD phase:

1. Run `npm run workflow:clean-check`.
2. Review source changes separately from governance artifacts.
3. Move or ignore raw runtime evidence.
4. Commit or archive finished governance artifacts.
5. Write the final state to `WORKLOG_YYYY-MM-DD.md`.

## Safety Checkpoints

Use safety checkpoints to protect a task without creating noisy temporary commits.

Start a task:

```powershell
npm run workflow:task-start -- -Name "short task name"
```

The start step:

- runs the secret guard,
- runs the GSD artifact clean check,
- refuses visible raw runtime evidence,
- creates a local `safety/pre-task/...` tag,
- writes `.workflow-gate/current-safety-checkpoint.json`.

Finish a task:

```powershell
npm run workflow:task-finish
```

The finish step:

- runs the secret guard,
- runs the clean check,
- runs `workflow:check` when available,
- deletes the safety tag only after a later official commit exists,
- keeps the tag if there is no official commit or if the repo is still dirty.

List active or stale checkpoints:

```powershell
npm run workflow:task-checkpoints
```

Rules:

- Safety tags are local rescue markers and should not be pushed.
- Safety tags are temporary and should be removed after the verified official commit.
- The workflow may create and clean safety tags automatically after the owner gives a task intent.
- Destructive rollback, remote push, and real Google Sheet writes still require explicit owner approval.

## Interpretation For Non-Technical Owners

The repo should not become a storage box for every screenshot, database, and log file.

The repo should contain:

- the app code,
- important decisions,
- short proof reports,
- and enough verification evidence to know what happened.

Large/raw evidence can stay on the machine unless it is specifically needed for handoff.
