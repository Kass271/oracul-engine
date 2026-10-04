# Failure note — 03_run-modes-readme

Status: BLOCKED after 5 fix rounds · 2026-10-04

## What failed
Rounds used: 5 of 5. Failing step: verify (related tests), backend. The last round (5) ended with `==== VERIFY (related tests) RED: backend ====`. Every other section of that run passed (contract, stack, review evidence, artifacts 00_setup to 04_build, `RESULT  OK`). The review-findings.json for this slice does not exist, so no review ran.

The backend failure text of rounds 3 to 5 was cut off in rounds.md. Only the tail survives (all PASS lines plus `verify took 7s` and the RED header). The last readable backend failures are these.

Round 2 (build error, not a test failure):

```
Problem configuring task :jacocoTestReport from command line.
> Unknown command-line option '--tests'.
BUILD FAILED in 286ms
FAIL     backend (0s)
```

Round 1 (full verify), recorded in red-evidence.md and rounds.md:

```
567 tests completed, 220 failed
StopRunIT > aStopRacingTheEndOfTheRunEndsEitherCompletedWithAStoryOrStoppedWithoutOne() FAILED
    org.opentest4j.AssertionFailedError at StopRunIT.java:56
INVALID  backend 9.5% < baseline 95.2% — add tests, never lower the baseline
MISSING  frontend coverage report not found — run verify (it builds the reports)
```

The frontend also failed in round 1. `stop-run.spec.ts` reported `element generate-button: expected null not to be null` and `expected null not to be null` for `stopped-view`. Frontend related tests passed in round 2 (`PASS     frontend (6s)`).

Before round 1 the contract sync stopped twice:
- First because `api/openapi.yaml` would not parse (`bad indentation of a mapping entry (1088:52)`).
- Then because check-sync flagged `HistoryController`. This was `RecentRunSummary.headline` becoming optional for STOPPED runs (FR-45). The user accepted it on 2026-10-04.

## What each round tried
1. Verify went RED on backend, frontend and check-coverage. The work was sent to the tester for GoogleNewsSourceIT, GetRunProgressIT, StopRunIT, evidence-note.spec.ts and e2e/stubs/server.mjs.
2. Sent to the builders. Backend related tests failed to run because `--tests` was passed to `:jacocoTestReport`. Frontend related tests passed.
3. Sent to the builders. Verify related tests stayed RED on backend.
4. Sent to the builders. Verify related tests stayed RED on backend.
5. Sent to the builders. Verify related tests stayed RED on backend, and the slice was blocked.

## Not delivered
All six FRs are unconfirmed, because the slice gate (full verify, E2E, independent review) was never reached:
- FR-42 — run modes (traceability points to `e2e/tests/run-modes.spec.ts`)
- FR-43 — README (traceability points to `e2e/tests/readme.spec.ts`)
- FR-45 — stop a generation and start a new one
- FR-46 — events and sources
- FR-47 — evidence and insufficient-evidence handling
- FR-48 — Google News search

## Impact
Dependent slices: not stated in the slice files → decision: STOP (the user decides)
