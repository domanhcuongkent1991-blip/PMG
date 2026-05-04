# Stop-The-Line Policy

Purpose: prevent repeated severe failures and unstable delivery.

## Trigger conditions
Stop new feature work immediately if one of these happens:
1. Same high-severity failure repeats 2 times.
2. Any validator for plan/skill/MCP fails.
3. Unknown risky write action appears without confirmation point.
4. Artifact contract is broken (missing critical handoff files).

## Required response
1. Freeze feature work.
2. Open incident note in worklog.
3. Run root-cause analysis.
4. Apply fix and re-run full health checks.
5. Resume only after PASS and explicit clear status.

## Exit criteria
- Root cause documented.
- Preventive guardrail added.
- E2E health checks pass.
- Status returns to `EXECUTION_READY`.
