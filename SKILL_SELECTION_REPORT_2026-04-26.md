# SKILL SELECTION REPORT - 2026-04-26

Task: read external workflow and tighten project rule/skill + MCP governance.
Project: `F:\codex_android_gsheet_full_pack`

## 1) Project understanding
- Android app for abnormal equipment tracking.
- Local-first data model with Google Sheets sync.
- Core business lookup by `ma_thiet_bi`.
- Data safety and stable operation are priority.

## 2) Task and risk classification
- Task type: `mcp_governance_design` (from AUTO-MCP advisor).
- Data class: `restricted` (due project signals about secret/token handling).
- Risk level: HIGH (rule/governance update affects future tool usage and process behavior).

## 3) Selected workflow/rules
### Core planning and execution
- `writing-plans`
- `test-driven-development`
- `systematic-debugging`
- `verification-before-completion`
- `requesting-code-review`

### Governance and safety controls adopted
From codex-skill-store + AUTO-MCP:
- mandatory reading sequence before major edits
- risk matrix (LOW/MEDIUM/HIGH/CRITICAL)
- least-privilege MCP usage
- primary/fallback MCP strategy
- explicit reconfirmation before risky writes
- release-boundary gate before final handoff/share

## 4) MCP routing decision for this phase
Primary tool:
- `shell_command` (local docs + controlled file edits)

Fallback tools:
- `mcp__github__` (only if remote repo review/write required)
- `web` (official source lookup when needed)

Blocked/not needed for this phase:
- `mcp__cloudflare_api__`
- `mcp__playwright__`
- `mcp__mempalace__`
- `mcp__node_repl__`

## 5) Validation steps required by selected controls
- Confirm control files actually read (`MANDATORY_READING_RECEIPT_2026-04-26.md`).
- Apply runbook rewrite with explicit gating and rollback expectations.
- Update worklog with dated entry.
- Summarize residual risks.

## 6) Residual risks after this phase
1. AUTO-MCP advisor auto-registered project profile in external registry; should be reviewed by owner.
2. Team discipline is still required: gates only work if followed in every task.
3. `mcp__context7__` is not available in current session, so fallback source path must remain explicit.

## 7) Safe default assumptions used
- Owner wants strict governance and low-bug workflow.
- Internal project handoff is current boundary unless explicitly changed.
- No destructive or public-release action is allowed without explicit confirmation.
