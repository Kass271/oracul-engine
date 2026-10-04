
## Round 1
- Trigger: E2E RED
- To builders: yes · to tester: no
- Output:

```
Error Context: test-results/mobile-layout-FR-34-mobile-29d34-scape-backdrop-keeps-values-chromium/error-context.md

    attachment #2: trace (application/zip) ─────────────────────────────────────────────────────────
    test-results/mobile-layout-FR-34-mobile-29d34-scape-backdrop-keeps-values-chromium/trace.zip
    Usage:

        npx playwright show-trace test-results/mobile-layout-FR-34-mobile-29d34-scape-backdrop-keeps-values-chromium/trace.zip

    ────────────────────────────────────────────────────────────────────────────────────────────────

  1 failed
    [chromium] › tests/mobile-layout.spec.ts:42:3 › FR-34 mobile layout › open, edit, close via button / Escape / backdrop keeps values 
  93 passed (13.5m)

E2E FAIL
==== E2E FAILURES (1) ====
✘ mobile-layout.spec.ts › open, edit, close via button / Escape / backdrop keeps values · chromium
    Error: expect(locator).toBeHidden() failed
    Locator:  getByTestId('scenario-panel')
    Expected: hidden
    Received: visible
    Timeout:  5000ms
    Call log:
      - Expect "toBeHidden" getByTestId('scenario-panel') with timeout 5000ms
      - waiting for getByTestId('scenario-panel')
        14 × locator resolved to <aside class="panel" _ngcontent-ng-c3252267225="" data-testid="scenario-panel">…</aside>
           - unexpected value "visible"
    attachment: e2e/test-results/mobile-layout-FR-34-mobile-29d34-scape-backdrop-keeps-values-chromium/trace.zip
==== END E2E FAILURES ====

```

## Round 2
- Trigger: verify RED
- To builders: yes · to tester: no
- Output:

```
.spec.ts, frontend/src/app/scenario/scenario.store.spec.ts
PASS     FR-3 → backend/src/test/java/com/oracul/app/runs/StartRunValidationTest.java, backend/src/test/java/com/oracul/app/scenario/ScenarioControllerTest.java, e2e/tests/scenario-controls.spec.ts, frontend/src/app/app.spec.ts, frontend/src/app/scenario/horizon-selector.spec.ts, frontend/src/app/scenario/scenario.store.spec.ts
PASS     FR-30 → backend/src/test/java/com/oracul/app/reasoning/AlternativeDistinctnessTest.java, backend/src/test/java/com/oracul/app/reasoning/ScenarioGenerationPromptTest.java, backend/src/test/java/com/oracul/app/runs/AlternativeRunIT.java, backend/src/test/java/com/oracul/app/runs/AlternativeRunStagesIT.java, e2e/tests/alternative-future.spec.ts, frontend/src/app/runs/quick-actions.spec.ts, frontend/src/app/runs/run.store.spec.ts
PASS     FR-31 → backend/src/test/java/com/oracul/app/research/EvidenceConfigurationTest.java, backend/src/test/java/com/oracul/app/research/EvidenceSufficiencyTest.java, backend/src/test/java/com/oracul/app/result/InsufficientEvidenceIT.java, backend/src/test/java/com/oracul/app/runs/RunFailuresTest.java, e2e/tests/insufficient-evidence.spec.ts, frontend/src/app/runs/insufficient-evidence.spec.ts, frontend/src/app/runs/run-view.spec.ts
PASS     FR-32 → backend/src/test/java/com/oracul/app/runs/RunDeadlineIT.java, backend/src/test/java/com/oracul/app/runs/RunDeadlineQueuedIT.java, backend/src/test/java/com/oracul/app/runs/RunDeadlineSchedulerIT.java, backend/src/test/java/com/oracul/app/runs/RunFailureHygieneIT.java, backend/src/test/java/com/oracul/app/runs/RunFailuresTest.java, backend/src/test/java/com/oracul/app/runs/RunStartupSweepIT.java, backend/src/test/java/com/oracul/app/runs/RunStartupSweepTest.java, e2e/tests/run-failures.spec.ts, frontend/src/app/app.spec.ts, frontend/src/app/runs/run-failure.spec.ts, frontend/src/app/runs/run-view.spec.ts, frontend/src/app/runs/run.store.spec.ts
PASS     FR-33 → backend/src/test/java/com/oracul/app/history/RecentRunsIT.java, e2e/tests/recent-futures.spec.ts, frontend/src/app/history/recent-futures.spec.ts, frontend/src/app/runs/run-view.spec.ts, frontend/src/app/runs/run.store.spec.ts
PASS     FR-34 → e2e/tests/mobile-layout.spec.ts, frontend/src/app/app.mobile.spec.ts
RESULT  OK

== coverage ratchet ==
PASS     backend 94.6% >= baseline 94.6%
INVALID  frontend 99.9% < baseline 100.0% — add tests, never lower the baseline
RESULT  FAIL (1 problem)

== contract ==
PASS     openapi.yaml valid (17 operations)
PASS     backend generates interfaces from openapi.yaml before compile
PASS     frontend generates the client from openapi.yaml before build/test/start
PASS     backend generated code is up to date
PASS     frontend generated code is up to date
RESULT  OK

== review evidence ==
PASS     slice 01_scenario-controls: clean (round 6, 7 finding(s), 0 open low)
PASS     slice 18_custom-wildcards-output: clean (round 4, 3 finding(s), 2 open low)
RESULT  OK

==== VERIFY RED: check-coverage ====
```

## Round 3
- Trigger: E2E RED
- To builders: yes · to tester: frontend/src/app/app.mobile.spec.ts
- Output:

```
Error Context: test-results/mobile-layout-FR-34-mobile-29d34-scape-backdrop-keeps-values-chromium/error-context.md

    attachment #2: trace (application/zip) ─────────────────────────────────────────────────────────
    test-results/mobile-layout-FR-34-mobile-29d34-scape-backdrop-keeps-values-chromium/trace.zip
    Usage:

        npx playwright show-trace test-results/mobile-layout-FR-34-mobile-29d34-scape-backdrop-keeps-values-chromium/trace.zip

    ────────────────────────────────────────────────────────────────────────────────────────────────

  1 failed
    [chromium] › tests/mobile-layout.spec.ts:42:3 › FR-34 mobile layout › open, edit, close via button / Escape / backdrop keeps values 
  93 passed (13.5m)

E2E FAIL
==== E2E FAILURES (1) ====
✘ mobile-layout.spec.ts › open, edit, close via button / Escape / backdrop keeps values · chromium
    Error: expect(locator).toBeHidden() failed
    Locator:  getByTestId('scenario-panel')
    Expected: hidden
    Received: visible
    Timeout:  5000ms
    Call log:
      - Expect "toBeHidden" getByTestId('scenario-panel') with timeout 5000ms
      - waiting for getByTestId('scenario-panel')
        14 × locator resolved to <aside class="panel" _ngcontent-ng-c3252267225="" data-testid="scenario-panel">…</aside>
           - unexpected value "visible"
    attachment: e2e/test-results/mobile-layout-FR-34-mobile-29d34-scape-backdrop-keeps-values-chromium/trace.zip
==== END E2E FAILURES ====

```

## Recovery (in place, 2026-10-04)
- Cause of block: E2E "open, edit, close via button / Escape / backdrop keeps values" — trace showed Escape (not the close button) left the over-mode drawer open; focus stayed on the toolbar toggle outside the drawer.
- Frontend builder: document-level `keydown.escape` → `onEscape()` closes the mobile drawer; `[autoFocus]` first-tabbable on mobile. Tester: Escape unit tests (mobile open/closed, desktop).
- Full verify GREEN; E2E 94 passed. Full review (round 4, none had run before): R1 medium tests (aria-expanded sync on backdrop/sidenav Escape, weak reopen checks), R2/R3 low.
- Tester fixed R1 (unit backdrop + element-Escape tests; E2E aria-expanded + panel visibility after every open/close). Full verify GREEN (445 tests, 100% lines); E2E 94 passed (13.5m).
- Re-review (round 5): R1 fixed, 0 open high/medium (R2, R3 low remain) → clean.
