> **Superseded:** the slice was recovered in place and closed DONE after review round 8. See rounds.md.

# Failure note — 06_events

Status: BLOCKED after 5 fix rounds · 2026-10-03

## What failed
- Failing check: **verify**. The backend section was RED in every round (rounds used: 5 of 5).
- Every gate before the backend section passed: the slice checks (`slice 05_search-sources: clean (round 10, 15 finding(s), 8 open low)`) and the artifact checks for 00_setup, 01_scope, 02_specs, 03_plan and 04_build all ended in `RESULT  OK`.
- The recorded verify output stops at the backend header. The captured excerpt has no test names or error lines, so the failing backend tests are not identified in the slice records.
- No `review-findings.json` exists for this slice. The independent review never ran because verify did not go green.

Last output (round 5, tail):

```
== artifacts 04_build ==
...
PASS     docs/phase-01_mvp/04_build/05_search-sources/review-findings.json [reviewClean]
PASS     docs/phase-01_mvp/04_build/05_search-sources/rounds.md [nonEmpty]
RESULT  OK

==== VERIFY RED: backend ====
```

Rounds 1–4 end the same way, followed by `[exited with code 1]`.

Context from `red-evidence.md` (RED phase, before GREEN): backend `:test` exit 1, `522 tests completed, 115 failed`, classified as FAIL (assertions / missing behaviour). The test report is at `backend/build/reports/tests/test/index.html`.

## What each round tried
`rounds.md` records only the trigger and output for each round. It does not describe the fix attempted.
1. Round 1: trigger was verify RED. Result: backend RED, exit code 1.
2. Round 2: trigger was verify RED. Result: backend RED, exit code 1.
3. Round 3: trigger was verify RED. Result: backend RED, exit code 1.
4. Round 4: trigger was verify RED. Result: backend RED, exit code 1.
5. Round 5: trigger was verify RED. Result: backend RED again, and the round limit was reached.

## Not delivered
- FR-14: the title is not recorded in the slice files. Tests: EventBatchingIT, EventConfigurationTest, EventCrossBatchIT, EventFailureIT, EventNormalizationIT, EventNormalizerRulesIT, EventPendingIT, EventPromptsTest, EventTimeoutIT, TokenSimilarityTest, e2e `events.spec.ts`.
- FR-15: the title is not recorded in the slice files. Tests: EventBatchingIT, EventClassificationBatchIT, EventClassificationIT, EventClassificationRulesIT, EventConfigurationTest, EventFailureIT, EventPromptsTest, EventTimeoutIT, e2e `events.spec.ts`.

## Impact
Dependent slices: the slice records do not list them; check `03_plan/plan.md` → decision: STOP

Next step: open the backend test report and the full verify log to find which tests are failing before starting another fix attempt. The truncated verify capture did not give the fix rounds enough information to work with.
