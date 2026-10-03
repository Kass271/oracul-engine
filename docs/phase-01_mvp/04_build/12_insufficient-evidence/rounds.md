
## Round 1
- Trigger: E2E RED
- To builders: yes · to tester: no
- Output:

```
xpected) // Object.is equality

    Expected: "COMPLETED"
    Received: "INSUFFICIENT_EVIDENCE"

    Call Log:
    - Timeout 40000ms exceeded while waiting on the predicate

      56 |   await expect
      57 |     .poll(async () => (await (await page.request.get(`/api/runs/${id}`)).json()).status, { timeout, intervals: [500] })
      58 |     .toBe(wanted);
         |      ^
      59 |     return (await page.request.get(`/api/runs/${id}`)).json();
      60 | }
      61 |
        at awaitStatus (/Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/e2e/tests/validated-scenario.spec.ts:58:6)
        at /Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/e2e/tests/validated-scenario.spec.ts:128:23

    Error Context: test-results/validated-scenario-FR-19-F-bc4a5-from-the-Evidence-Pack-only-chromium/error-context.md

    attachment #2: trace (application/zip) ─────────────────────────────────────────────────────────
    test-results/validated-scenario-FR-19-F-bc4a5-from-the-Evidence-Pack-only-chromium/trace.zip
    Usage:

        npx playwright show-trace test-results/validated-scenario-FR-19-F-bc4a5-from-the-Evidence-Pack-only-chromium/trace.zip

    ────────────────────────────────────────────────────────────────────────────────────────────────

  9 failed
    [chromium] › tests/critic.spec.ts:103:3 › FR-22 Critic validation › FR-22 a passing critic is called once between the scenario and the story and shows nothing 
    [chromium] › tests/events.spec.ts:73:3 › FR-14 / FR-15 Event normalisation and semantic classification › the acceptance run produces 41 normalized, classified events 
    [chromium] › tests/evidence-pack.spec.ts:133:3 › FR-16 / FR-17 / FR-18 Ranking, evidence selection and the Evidence Pack › default classification: every event is bright, so only counter-signals are selected 
    [chromium] › tests/future-story.spec.ts:108:3 › FR-23 / FR-25 Future story and metadata › FR-23 FR-25 the acceptance run ends in the labelled story with the metadata panel 
    [chromium] › tests/insufficient-evidence.spec.ts:79:3 › FR-31 Insufficient evidence › FR-31 Realism 10 with 2 core items ends in the insufficient view and LOWER REALISM starts a Realism 8 run 
    [chromium] › tests/run-failures.spec.ts:87:3 › FR-32 Run failure handling › FR-32 a ChatGPT 429 ends the run with the friendly message, the panel stays usable and Try again re-submits 
    [chromium] › tests/run-start.spec.ts:173:3 › FR-24 Generation progress › progress walks through every stage label and ends with the result view 
    [chromium] › tests/search-sources.spec.ts:74:3 › FR-12 Search plan and query generation › the acceptance run stores a plan with 8/6/4/2 queries and one tool-less expansion request 
    [chromium] › tests/validated-scenario.spec.ts:125:3 › FR-19 / FR-20 / FR-21 Validated scenario (API level) › FR-19 FR-20 the acceptance run produces a validated structured scenario from the Evidence Pack only 
  28 did not run
  36 passed (7.1m)

E2E FAIL
```

## Round 2
- Trigger: E2E RED
- To builders: yes · to tester: no
- Output:

```
docker compose up -d --build …
UP  frontend http://localhost:4200  ·  backend http://localhost:8080/api  ·  health http://localhost:8080/actuator/health
```

## Round 3
- Trigger: E2E RED
- To builders: no · to tester: /Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/e2e/tests/insufficient-evidence.spec.ts
- Output:

```
Command timed out after 600 seconds. No output available.
```

## Recovery (review rounds 1–2) — 2026-10-03
- Green stage ended BLOCKED on E2E after 3 rounds. Round 1 failures: acceptance runs ended INSUFFICIENT_EVIDENCE (the override thresholds came later). Rounds 2–3 were collisions between parallel Playwright runs (shared stub on 4010 / test-results) or timeouts.
- Per a user instruction, E2E was rerun alone: 73 passed. No code change was needed for E2E.
- The analyst added the new `sliceSpec` lines for FR-31 (Changes earlier behaviour with test files; Ranges & invariants).
- Review round 1: R1/R2 medium (min-core thresholds capped at a literal 10, imprecise error; no test for core ≠ 10). Fixed via tester RED → builder GREEN.
- Review round 2: clean. Open lows R3–R9 (R9: flaky EventNormalizationIT from slices 06/07, a GDELT first-request race).
- verify GREEN (1098 backend tests, 94.5% / frontend 100%). Slice closed DONE.
