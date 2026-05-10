# MCP_RISK_REPORT

Generated at UTC: 2026-04-27T11:55:33Z
Project path: F:\codex_android_gsheet_full_pack

## 1. Request summary

- Task type: mcp_governance_design
- Data class: restricted
- Profile: android-local-first-restricted
- Policy bundle version: 2.0.0
- Policy bundle hash: abc46c1a1c37db13c17ea4072a177ae0759f457dce44c27b55eeedacc81ee1f1

## 2. MCP plan (allow/deny)

- Primary MCPs: shell_command
- Fallback MCPs: github
- Blocked MCPs: browser_use, cloudflare, google_drive, node_repl, playwright

## 3. Risk table

| MCP | Role | Risk | Requires explicit approval for |
|---|---|---|---|
| browser_use | blocked | unknown | none |
| cloudflare | blocked | unknown | none |
| github | fallback | unknown | none |
| google_drive | blocked | unknown | none |
| node_repl | blocked | unknown | none |
| playwright | blocked | unknown | none |
| shell_command | primary | unknown | none |

## 4. Mandatory controls

### Policy controls
- [ ] always_generate_execution_plan_before_tool_action
- [ ] confirm_external_writes
- [ ] define_confirmation_points_for_risky_actions
- [ ] discover_project_context_before_rule_design
- [ ] explain_reason_for_each_allowed_or_blocked_mcp
- [ ] log_tool_selection
- [ ] mask_sensitive_output
- [ ] minimize_data_access
- [ ] no_external_copy
- [ ] no_external_data_transmission
- [ ] prefer_local_validation_before_remote_write
- [ ] prefer_read_before_write
- [ ] require_change_scope
- [ ] require_explicit_scope_for_write_operations
- [ ] require_explicit_user_confirmation

### Stability controls
- [ ] capture_tool_errors_with_context
- [ ] fallback_on_primary_failure
- [ ] include_validation_steps_for_every_rule_change
- [ ] prefer_idempotent_operations
- [ ] propose_safe_defaults_before_custom_rules
- [ ] scan_existing_workflow_assets_first
- [ ] set_explicit_timeouts
- [ ] summarize_assumptions_before_write_actions

## 5. Approval requirement

MCP write/destructive operations are blocked until user confirms in chat.
Chat approval should be explicit, for example: 	oi duyet mcp theo bao cao nay.

## 6. Residual risk and recommendation

- Residual risk exists; some MCPs are blocked by profile/policy.
- Recommendation: proceed only with primary MCPs first, fallback only when primary fails.
- Recommendation: re-confirm write scope immediately before any mutating action.
