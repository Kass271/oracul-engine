# Failure note — 04_run-start

Status: BLOCKED after 1 of 5 fix rounds · 2026-10-03

FRs in this slice: FR-10, FR-11, FR-24

## What failed

Failing check: `close: artifacts/coverage`. The slice reached `04_run-start DONE` (`subStep = none`), but the close step failed on the coverage ratchet. Backend line coverage dropped below the recorded baseline, and the ratchet does not allow the baseline to be lowered.

Exact output:

```
04_run-start DONE
subStep = none
== coverage ratchet (update) ==
INVALID  backend 78.5% < baseline 83.5% — add tests, never lower the baseline
PASS     frontend 100.0% ≥ baseline 100.0%
INVALID  update refused: the ratchet only tightens
RESULT  FAIL (2 problems)
```

- Backend: 78.5% against a baseline of 83.5%, so it is 5.0 points short.
- Frontend: 100.0% against a baseline of 100.0%, which passes.
- Baseline update: refused, because the ratchet only tightens.

Review findings from round 1 that are still open (`review-findings.json`). All are severity low, and none of them causes the failure above:

| ID | FR | File | Problem |
|----|----|------|---------|
| R1 | FR-10 | `frontend/src/app/runs/run.store.ts:63` | `open(runId)` for another id calls `reset()`. This stops polling of the started run, so `active()` becomes false while that run is still active. |
| R2 | FR-10 | `frontend/src/app/runs/run.store.ts:147` | `showError` removes snackbar containers from the DOM by hand, which bypasses the CDK overlay lifecycle. |
| R3 | FR-24 | `frontend/src/app/runs/progress-view.ts:28` | `mat-progress-bar` has no accessible name, and the stage text has no `aria-live` region. |
| R4 | FR-24 | `frontend/src/app/runs/run-view.ts:13` | On a fresh load of `/futures/<id>`, nothing shows a loading state before the first `getRun` returns. |
| R5 | FR-10 | `backend/src/main/java/com/oracul/app/runs/RunService.java:58` | The 409 mapping finds the constraint name by matching text in the `DuplicateKeyException` message. This is fragile. |

## What each round tried

1. Round 1 (of 5): GREEN implementation for FR-10, FR-11 and FR-24 against the RED tests in `red-evidence.md`. The RED run had 46 of 221 backend tests failing, and `run.store.spec.ts` had frontend failures such as "Expected one matching request for criteria isRunsPost, found none". The independent review found 5 low-severity issues (R1–R5). The slice then reached DONE, and the close step failed on the backend coverage ratchet. `rounds.md` has no further detail for this round.

## Not delivered

The slice cannot close until backend coverage gets back to at least 83.5%. Until then, these FRs are not signed off:

- FR-10: start a generation run (tagged tests: `StartRunIT`, `StartRunCompletedIT`, `GenerationIdIT`, `generate-button.spec.ts`, `run.store.spec.ts`, `e2e/tests/run-start.spec.ts`)
- FR-11: run research for a run (tagged tests: `ResearchProfileFactoryTest`, `RunResearchIT`, `e2e/tests/run-start.spec.ts`)
- FR-24: run progress and status (tagged tests: `GetRunIT`, `GetRunProgressIT`, `GetRunTerminalIT`, `RunStageTest`, `progress-view.spec.ts`, `run-view.spec.ts`, `run.store.spec.ts`, `e2e/tests/run-start.spec.ts`)

## Impact

Dependent slices: the provided facts do not list them. Review finding R1 says slice 15 (Recent futures) will use this slice's run store.
→ decision: STOP

Add backend tests to raise backend coverage from 78.5% to at least 83.5%. Do not lower the baseline. Then run the close step again. Fixing the open low-severity findings R1–R5 in the same pass is recommended.
