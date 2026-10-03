# Failure note — 15_recent-futures

> **Superseded (2026-10-04):** recovered in place — see "Recovery" in rounds.md. Slice closed DONE.

Status: BLOCKED after 3 of 3 fix rounds · 2026-10-04

FRs: FR-33

## What failed
Failing check: **review**. One open medium finding remains in `review-findings.json` (round 3).

```
== review evidence ==
INVALID  slice 15_recent-futures: 1 open high/medium finding(s): R1
RESULT  FAIL (1 problem)
```

- **R1** (medium, dimension: tests, FR-33), `frontend/src/app/runs/run-view.spec.ts:1`.
  The fix for the round 2 E2E failure made `RunView` subscribe to `route.paramMap` and made `FutureResultComponent` reload when `runId` changes (`run-view.ts`, `future-result.ts`). No unit test covers moving between two `/futures/:runId` URLs inside the same `RunView` instance, so only the 60 s E2E guards against a regression.
  - Required: `/futures/A -> /futures/B` must issue `getRun(B)`, then `getFutureResult(B)`, with no further `getFutureResult(A)`. It must also render B's headline and meta and load B's configuration into `ScenarioStore`. `/futures/A -> /futures/A` (same id) must issue no second `getRun` and must not touch the panel.
  - Suggested fix: add a `// @trace FR-33` describe in `run-view.spec.ts` that uses `RouterTestingHarness`. Navigate from A (darkness 9) to B (darkness 3). Assert `meta-darkness` = "Darkness 3/10", B's `story-headline`, `ScenarioStore.darkness() === 3`, and that no requests are made to A's endpoints. Add a second `it()` for the same-id case.

## What each round tried
1. **Trigger: E2E RED.** Sent to the builders. The E2E stack did not build. `./gradlew bootJar` inside the backend Docker image failed because the Gradle distribution download (`gradle-9.7.1-bin.zip`) returned HTTP 503. This was an infrastructure failure, not a test assertion.
2. **Trigger: E2E RED.** Sent to the builders. 76 passed, 1 failed: `recent-futures.spec.ts` › "FR-33 two futures are listed newest first and the older one reopens with its data". `getByTestId('meta-darkness')` expected "Darkness 9/10" but received "Darkness 3/10". The reopened run kept showing the previous run's data. The builders then made `RunView` and `FutureResultComponent` react to `runId` route changes (as R1 describes).
3. **Trigger: review not clean.** Sent to the tester only (R1), with nothing for the builders. The review still reported `INVALID ... 1 open high/medium finding(s): R1`. The round budget (3 of 3) ran out with the finding still open.

## Not delivered
- FR-33 — recent futures list: futures are listed newest first, and reopening an older one loads its data and configuration. The behaviour fix is in place according to R1. The slice is not closed because the required unit-test coverage for changing the route parameter is missing.

## Impact
Dependent slices: none recorded in this slice's evidence (`rounds.md`, `red-evidence.md`, `review-findings.json`) → decision: CONTINUE

The only open item is a test-only finding (R1, dimension: tests). It can be recovered in place: tester adds the `run-view.spec.ts` cases, then verify, then reviewer, then close.
