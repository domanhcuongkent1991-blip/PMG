# Standard AI Commands (Operator Pack)

## A) End-to-end governance start
"Read the 3 workflows and execute end-to-end governance flow. Produce reading receipt, skill selection report, MCP execution plan, then continue only if EXECUTION_READY."

## B) Daily health check
"Run workflow health check and report PASS/FAIL with blockers."

## C) Before risky change
"Classify risk level and create rollback notes before editing files."

## D) Before final handoff
"Run release-readiness gates and show remaining risks in Vietnamese."

## E) Incident mode
"Stop new feature work and run root-cause analysis for repeated failure."

## Canonical status language
Use only:
- NEED_MORE_INFO
- PLAN_DRAFT_READY
- PLAN_REVIEW_READY
- PLAN_APPROVED_FOR_HANDOFF
- EXECUTION_READY
- RELEASE_READY
