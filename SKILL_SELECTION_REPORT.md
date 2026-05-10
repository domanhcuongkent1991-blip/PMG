# SKILL_SELECTION_REPORT

Canonical artifact for workflow gate checks.

Project: `F:\codex_android_gsheet_full_pack`
Date: 2026-05-08

## 1. Project understanding

- DeviceTracker is an Android app for equipment issue tracking and Google Sheets two-way sync.
- The official scope is defined by `input/prd.md`.
- The app must protect user data, avoid wrong sheet/wrong row sync, and remain understandable for a non-technical operator.
- Workflow changes must be evidence-based and must not mark app behavior as safe without matching test/build/UAT proof.

## 2. Untrusted input review

- Google Sheet content is untrusted input because users can edit sheet rows, tab names, dates, notes, and identifiers outside the app.
- Local UI input is untrusted input because users can type invalid dates, notes, or device codes.
- Agent-generated workflow artifacts are untrusted until validated by gates, tests, review, and explicit approval evidence.
- Secret files and real credentials must not be read or logged unless the user explicitly approves a narrowly scoped task.

## 3. Security assurance level

Security level: `restricted`.

Reason: the project can touch real Google Sheets data, OAuth/token configuration, Android local data, and maintenance records. Safe workflow defaults must prefer read-only review, small phases, and explicit approval before risky actions.

## 4. Action risk classification

- Low risk: read-only audit, documentation, local report generation, no source edits.
- Medium risk: workflow artifact updates, tests, local validation scripts.
- High risk: sync logic edits, auth/token handling, real APK install, real Google Sheet write tests.
- Blocked without explicit approval: secret access, broad source refactor, destructive file/database operations, deploy/release claims.

## 5. Selected skills by phase

- Planning/review phase: `writing-plans`, `systematic-debugging`, `verification-before-completion`.
- Bug fix phase: `systematic-debugging`, `test-driven-development`, `requesting-code-review`.
- Code cleanup phase: `test-driven-development` when behavior changes, plus code review and code quality report.
- Android validation phase: Android test/UAT workflow, real-device verification only when explicitly approved.
- Workflow governance phase: local workflow tests, secret guard, gatekeeper validation, worklog update.

## 6. MCP routing decision

- Local filesystem and shell checks are allowed for repo workflow validation.
- MCP/tool usage must match the task scope and the MCP risk report.
- Browser, GitHub, cloud, Google Drive/Sheets, or device tools require explicit scope and approval when they can affect external systems or real data.

## 7. Secret and `.env` plan

- Do not commit real `.env`, tokens, OAuth secrets, or spreadsheet credentials.
- Use `.env.example` or BuildConfig placeholders for examples.
- Run `node scripts/prevent-secrets.js` before commit or handoff involving workflow/source changes.
- Do not print secret values in logs, worklogs, reports, or terminal summaries.

## 8. Validation required

- Run `npm run workflow:test` after workflow script changes.
- Run `npm run workflow:check` after final workflow artifact changes.
- Run the upgraded `F:\CODEX-AUTO` workflow gate when validating compatibility with the central workflow.
- For Android behavior changes, add targeted tests and do not claim UAT success without real evidence.

## 9. Residual risks

- Existing uncommitted source changes may predate the current workflow pass.
- Google Sheets behavior still requires real-data UAT before production confidence.
- Workflow gates prove process safety, not app correctness.

## 10. Next safe step

After this schema normalization, only run one narrow GSD-supervised code task at a time. Each source-editing task must have phase approval, fail-first evidence when it is a bug fix, code review, code quality report, verification, and worklog.
