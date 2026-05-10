# MANAGER REVIEW - AGENT1 P0 DMBT DUPLICATE FIX - 2026-05-05

## Scope reviewed
- Reviewed Agent 1 changes for A1-P0-DMBT-DUP.
- Source UAT: `docs/uat/results/USER_UAT_RESULT_2026-05-05.md`.
- Primary issues:
  - UAT-06: Google Sheet pull into app created duplicate cards.
  - UAT-09: repeated sync still created duplicate rows on Google Sheet.

## Manager corrections applied
1. Date key normalization
   - Agent 1 used `DateTextFormatter.normalizeInputOrNull()`, which only accepts `dd/MM/yyyy`.
   - This made `09/01/2025` and `2025-01-09` produce different business keys.
   - Manager changed DMBT business/match key generation to use `DateTextFormatter.formatForDisplay()`, which supports both date formats.

2. Cross-sheet readonly prefix safety
   - Agent 1 changed `resolveDmbtSheetRecordId()` to strip `readonly-dmbt-<anySheetId>-`.
   - Manager narrowed it back to strip only `readonly-dmbt-<targetSheetId>-`.
   - Reason: stripping a different sheet prefix can make a record from Sheet A match/update a row in Sheet B when base IDs overlap. PRD explicitly forbids writing wrong sheet/row.

## Verification
- Ran Android unit tests:
  - Command: `gradlew.bat testDebugUnitTest --no-daemon --max-workers=1`
  - Result: PASS
- Ran debug build:
  - Command: `gradlew.bat assembleDebug --no-daemon --max-workers=1`
  - Result: PASS

## Remaining risk
- Code-level checks now pass, but real Google Sheet UAT is still required.
- Must retest:
  - UAT-05: `743BC04`, ngay phat hien `09/01/2025`.
  - UAT-06: Sheet edit -> app pull, no duplicate.
  - UAT-09: repeated full sync does not create duplicate rows.

## Manager decision
- Status: READY FOR CONTROLLED UAT RETEST.
- Do not release yet until UAT-05/UAT-06/UAT-09 pass on phone + real Sheet.
