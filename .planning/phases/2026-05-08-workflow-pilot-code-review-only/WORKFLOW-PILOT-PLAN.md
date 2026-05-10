# Workflow Pilot Plan - Code Review Only

## Metadata

workflow_task_type: code_review_only
workflow_profile: safe
project: codex_android_gsheet_full_pack
pilot_date: 2026-05-08
source_edit_policy: forbidden

## Goal

Run the upgraded F:\CODEX-AUTO workflow against this Android project in the safest useful mode: code-review-only. The pilot checks whether an AI can understand the workflow, produce the required GSD-style evidence, and refuse source edits when the task is audit-only.

## Non-Tech Explanation

This is a rehearsal, not a repair session. Think of it like asking the AI to inspect the car, write a checklist, and prove it did not touch the engine yet.

## Scope

- Read existing project rules and PRD.
- Review selected sync/config/code-quality risk areas.
- Produce a review report with prioritized findings.
- Produce a code quality score and decision.
- Produce proof that this pilot did not edit Android source code.
- Run workflow/security checks that are safe for a read-only pilot.

## Out Of Scope

- No Android source code edits.
- No Gradle build changes.
- No APK installation.
- No Google Sheet writes.
- No secret or credential file reads.
- No production/deploy approval.

## Required Artifacts

- WORKFLOW-PILOT-SUMMARY.md
- WORKFLOW-PILOT-REVIEW.md
- CODE_QUALITY_REPORT.md
- NO_SOURCE_EDITS.md
- WORKFLOW-PILOT-VERIFICATION.md

## Success Criteria

- The pilot creates all required artifacts.
- The code quality report contains CODE_QUALITY_SCORE, Decision, and Regression risk.
- The review-only task contains NO_SOURCE_EDITS evidence.
- Any remaining risk is written plainly enough for a non-technical owner to decide the next step.
