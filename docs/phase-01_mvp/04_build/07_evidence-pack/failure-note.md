> **Superseded:** the slice was recovered in place and closed DONE after review round 6. See rounds.md.

# Failure note — 07_evidence-pack

Status: BLOCKED after 5 fix rounds · 2026-10-03

FRs in this slice: FR-16, FR-17, FR-18

## What failed

**Failing check: `verify`** (rounds used: 5 of 5).

The last verify run (round 5) never finished. It had printed only the backend step header when it was recorded as RED, so it gave no exit code and no test result:

```
verify is RED:
COMMAND STILL RUNNING - Process has not completed yet

The command: node "/Users/frederiks/courses/agent-crash/oracul/factory-engine/checks/verify.mjs"
Started: Oct 3, 12:59 PM
Status: Still executing (PID 83725)
Output file: /private/tmp/claude-501/-Users-frederiks-courses-agent-crash-oracul/b725725a-e669-461d-9c54-29ead535d23d/tasks/bn6jaskxi.output

Current output (complete, 2 lines only):
== backend: ./gradlew test jacocoTestReport --console=plain -q ==
```

Round 4 ended the same way, with the background task `bg1j7j59d` still running and only the backend header captured. So the last two rounds do not show whether the backend tests pass. They show only that verify did not finish inside the round. The last complete verify result is from round 2 (`VERIFY RED: backend · check-coverage`, exit code 1).

The review findings (`review-findings.json`, review round 3) still have findings with status `open`:

- **R1 (high, tests, FR-17)**: `e2e/stubs/server.mjs` was not changed. `POST /__control/events` does not accept mode `evidence`, so `evidence-pack.spec.ts` gets 400 `unknown_mode`. `GET /articles/*` hard-codes `og:site_name 'Stub Site'`, so max-per-publisher=3 limits the pack to 3 items. Because of this, both FR-16/17/18 E2E tests fail, and nothing yet proves the traceability claim for `e2e/tests/evidence-pack.spec.ts`.
- **R2 (low, correctness, FR-18)**: `EvidencePackRenderer.java:64` truncates the summary with `summary.substring(0, 600)`, which counts UTF-16 code units. This can leave a lone high surrogate in `promptText`.

## What each round tried

1. **Round 1 (verify RED)**: The backend was RED with `715 tests completed, 6 failed` (`:test` failed, `BUILD FAILED in 3m 38s`). Frontend PASS (statements 98.09%, branches 92.73%, lines 100%). Traceability PASS for FR-1 to FR-4 in the captured excerpt.
2. **Round 2 (verify RED)**: The artifact checks for 01_scope, 02_specs, 03_plan and 04_build (slices 01–06) were all `RESULT OK`. Verify still failed at `VERIFY RED: backend · check-coverage` (exit code 1).
3. **Round 3 (review not clean)**: The gate rejected the slice with `INVALID slice 07_evidence-pack: 1 open high/medium finding(s): R1`. The required E2E stub changes in `e2e/stubs/server.mjs` had not been made.
4. **Round 4 (verify RED)**: Verify was started as background task `bg1j7j59d`. It was still running the backend step (`./gradlew test jacocoTestReport`) when the round was recorded, and no exit code came back.
5. **Round 5 (verify RED)**: Verify was started again (PID 83725, started 12:59 PM). It was still running and had printed only the backend step header when the round was recorded, and no exit code came back.

For reference, the RED baseline (`red-evidence.md`) was backend exit 1 with `715 tests completed, 101 failed`, classified as FAIL (assertions / missing behaviour). `RESULT: RED`.

## Not delivered

The FR titles are not in this slice's evidence. The descriptions below come from the tagged tests in `red-evidence.md`.

- FR-16: event ranking (`EventRankerTest`, `RankingMinQualityIT`, `RankingSelectionIT`, `EvidenceConfigurationTest`, `e2e/tests/evidence-pack.spec.ts`)
- FR-17: evidence selection (`EvidenceSelectorTest`, `RankingSelectionIT`, `EvidenceConfigurationTest`, `e2e/tests/evidence-pack.spec.ts`). Open finding R1 applies.
- FR-18: evidence pack build and rendering (`EvidencePackIT`, `EvidencePackAtomicIT`, `EvidencePackPendingIT`, `EvidencePackRendererTest`, `e2e/tests/evidence-pack.spec.ts`). Open finding R2 applies.

## Impact

Dependent slices: cannot be determined from this slice's evidence. The orchestrator should check which later slices in `03_plan/plan.md` depend on FR-16, FR-17 or FR-18.

Decision: pending (CONTINUE | STOP). The orchestrator should decide once it has those dependencies.

Before any retry:

- Let `verify.mjs` run to completion. The backend step takes about 3.5 minutes; rounds 1 and 2 recorded `BUILD FAILED in 3m 38s` and `3m 34s`. Record its real exit code.
- Fix R1 in `e2e/stubs/server.mjs`.
- Fix R2 in `EvidencePackRenderer.java`.
