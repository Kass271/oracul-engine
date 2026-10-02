# Failure note — 03_wildcards

Status: BLOCKED after 2 of 5 fix rounds · 2026-10-03

## What failed
The close check `artifacts/coverage` failed: backend coverage is below the ratchet baseline, so the baseline update was refused.

```
03_wildcards DONE
subStep = none
== coverage ratchet (update) ==
INVALID  backend 19.7% < baseline 82.6% — add tests, never lower the baseline
PASS     frontend 100.0% ≥ baseline 100.0%
INVALID  update refused: the ratchet only tightens
RESULT  FAIL (2 problems)
```

- Backend: 19.7% against a baseline of 82.6% (FAIL).
- Frontend: 100.0% against a baseline of 100.0% (PASS).
- The ratchet only tightens. The baseline must not be lowered; the fix is more backend tests (or a correct backend coverage measurement).

Open review finding (from `review-findings.json`, round 2):
- R3 (low, tests) — `backend/src/test/java/com/oracul/app/runs/StartRunWildcardValidationTest.java:203`: the R1 fix in `ApiExceptionHandler.handleInvalid` has no regression test. Nothing covers an unknown wildcard together with an invalid `customWildcards` or `output`, so the documented order "wildcards -> customWildcards -> output" is untested.

## What each round tried
1. Round 1 — Trigger: review not clean (`INVALID slice 03_wildcards: 1 open high/medium finding(s): R1`). Fix target: R1 (medium, correctness). Semantic wildcard checks in `RunsController` ran after bean validation, so a later bean error (customWildcards/output) won over "unknown wildcard". R1 is marked fixed: `ApiExceptionHandler.handleInvalid` now applies `WildcardRules` when the top bean error is under wildcards/customWildcards/output.
2. Round 2 — Review round 2 marked R1 and R2 fixed. R2 (low, spec-compliance): the wildcard-label span was moved out of `mat-slide-toggle`. R3 (low) is still open. The slice then reached `03_wildcards DONE`, but the close check `artifacts/coverage` failed as shown above.

## Not delivered
- FR-4 — Wildcards (slice 03_wildcards; not closed because the coverage check failed)

## Impact
Dependent slices: not stated in the available facts → decision: STOP until the backend coverage gap (19.7% vs 82.6%) is fixed with more tests, not by lowering the baseline
