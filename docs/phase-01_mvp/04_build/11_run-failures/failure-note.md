> **Superseded:** the slice was recovered in place and closed DONE after review round 6. See rounds.md.

# Failure note — 11_run-failures

Status: BLOCKED after 5 fix rounds · 2026-10-03

## What failed
Failing check: **verify** (still RED after round 5 of 5).

In the last round the artifact checks passed for slices 01–10 (`RESULT  OK`). The run then stopped at the backend coverage step:

```
PASS     docs/phase-01_mvp/04_build/10_critic/review-findings.json [reviewFile]
PASS     docs/phase-01_mvp/04_build/10_critic/review-findings.json [reviewClean]
RESULT  OK

==== VERIFY RED: backend · check-coverage ====

[exited with code 1]
```

No `review-findings.json` exists for this slice, so the independent review never ran and there are no review findings.

The RED baseline in `red-evidence.md` (taken before GREEN) records these failures:
- backend: `1021 tests completed, 27 failed`. Examples: `RunStartupSweepIT > anActiveRunIsFailedAsInterruptedAndTerminalRunsStay()` and `RunStartupSweepIT > theInterruptedRunDoesNothingAfterTheGateIsReleased()`. The Gradle `:test` task also failed with `NoSuchFileException ... build/test-results/test/binary/in-progress-results-generic.bin`.
- frontend: failure-view content was empty. The `try-again` button was missing. On `CHATGPT_SESSION_EXPIRED` the connection state was not loaded (`run-view.spec.ts`, `run.store.spec.ts`).

## What each round tried
`rounds.md` records only the trigger and the verify output for each round. It does not record which fix was tried.
1. Trigger: verify RED. Result: still RED at `VERIFY RED: backend` (exit 1).
2. Trigger: verify RED. Result: still RED at `VERIFY RED: backend` (exit 1).
3. Trigger: verify RED. Result: still RED at `VERIFY RED: backend` (exit 1).
4. Trigger: verify RED. Result: still RED at `VERIFY RED: backend` (exit 1).
5. Trigger: verify RED. Result: the backend test step no longer blocked, but the run failed at `VERIFY RED: backend · check-coverage` (exit 1).

## Not delivered
- FR-32 — Run failure handling

## Impact
Dependent slices: none recorded in the given facts → decision: CONTINUE (FR-32 stays open and is listed as not delivered for this phase)
