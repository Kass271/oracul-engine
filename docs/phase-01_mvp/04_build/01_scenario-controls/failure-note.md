> **Superseded:** the slice was recovered in place and closed DONE after round 6. See rounds.md, "Recovery (round 6)".

# Failure note — 01_scenario-controls

Status: BLOCKED after 5 fix rounds · 2026-10-02

FRs: FR-1, FR-2, FR-3 · Rounds used: 5 of 5 · Failing check: review

## What failed
The independent review was still not clean after round 5. One finding at medium or high severity is still open.

```
== review evidence ==
INVALID  slice 01_scenario-controls: 1 open high/medium finding(s): R3
RESULT  FAIL (1 problem)
```

- **R3 (medium, tests, FR-2, open):** see `frontend/src/app/scenario/scenario.store.ts:43`. The spec (`scenario-panel.md:285-286`) asks for these test files, and none of them exist:
  - `scenario.store.spec.ts`
  - a component spec for the intensity slider
  - a component spec for the horizon selector

  `frontend/src/app/app.spec.ts` is the only spec file. Since round 4 it is the only frontend file that changed, and it is still a page-level (DOM) shell test. As a result, nothing tests:
  - the store guards in `scenario.store.ts:36-50`. The values 0, 11, 5.5 and NaN should be rejected, and an unknown horizon code should be ignored.
  - the 8/5/5/1y defaults before load.
  - that `load(config)` replaces the whole state.

  The DOM tests cannot reach these guards, because the slider's min/max limits and the toggle group never send such values. So the R2 acceptance is still not proven.
  - Required fix: add `scenario.store.spec.ts` with `@trace FR-2/FR-3`. It should cover:
    - the defaults before load
    - that load replaces the state
    - that each setter changes only its own field
    - that 0, 11, 5.5 and NaN leave the state unchanged
    - that an unknown horizon code is ignored

    Also add the component specs for the slider and the horizon selector.
- **R7 (low, error-handling, FR-2, open, does not block):** see `backend/src/main/java/com/oracul/app/common/ApiExceptionHandler.java:80`. Framework 4xx errors now keep their own status. A framework 5xx error still becomes a 500, which is acceptable. There are still no MockMvc tests for these cases:
  - `GET /api/runs/not-a-uuid` should give 404 `RUN_NOT_FOUND`
  - an unknown `/api` path should give 404
  - a wrong method should give 405

Findings file: `review-findings.json` (round 5). R1, R2, R4, R5 and R6 are marked fixed.

## What each round tried
1. Round 1. Trigger: review not clean, with 4 open high/medium findings: R1, R2, R3 and R4. The fixes for this round resolved:
   - R1: the catch-all exception handler turned framework 4xx errors into 500.
   - R2: the ScenarioStore did not match the spec's surface.
   - R4: the sidenav layout and the component split were missing.

   R3 stayed open.
2. Round 2. Trigger: review not clean, with 1 open high/medium finding (R3). The missing store and component specs were not added.
3. Round 3. Trigger: review not clean, with 1 open high/medium finding (R3). Same result.
4. Round 4. Trigger: review not clean, with 1 open high/medium finding (R3). Same result. By round 5, the low findings R5 (501 defaults for operations not built yet) and R6 (slider label and drag updates) were also marked fixed. R7 was partly fixed: 4xx errors keep their status, but the tests are still missing.
5. Round 5. Trigger: review not clean, with 1 open high/medium finding (R3). The only frontend file changed was `frontend/src/app/app.spec.ts`, which is still a shell test. No store or component spec was added, so R3 stays open.

## Not delivered
The slice did not reach DONE and was not committed, so none of its FRs count as delivered:
- FR-1: the scenario panel layout (title not given in the slice evidence)
- FR-2: the intensity controls and scenario configuration (title not given in the slice evidence). Its store guards have no tests (R3).
- FR-3: horizon selection (title not given in the slice evidence). Its store guard has no tests (R3).

## Impact
Dependent slices, according to `review-findings.json`:
- slice 04 (welcome/generate) builds on the center/WelcomeView structure.
- slice 19 (mobile drawer) builds on the mat-sidenav layout.
- FR-29 quick controls and later slices use the ScenarioStore surface.

Decision: STOP. The code that the dependent slices need is in place. The gap is unit tests for the ScenarioStore guards and for the slider and horizon selector components. Those tests should be added and the review re-run before slices 04 and 19 build on this store.
