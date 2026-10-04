
## Round 1
- Trigger: E2E RED
- To builders: no · to tester: /Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/e2e/tests/quick-regeneration.spec.ts
- Output:

```
Error Context: test-results/quick-regeneration-FR-29-q-2a86f-one-stays-in-Recent-futures-chromium/error-context.md

    attachment #2: trace (application/zip) ─────────────────────────────────────────────────────────
    test-results/quick-regeneration-FR-29-q-2a86f-one-stays-in-Recent-futures-chromium/trace.zip
    Usage:

        npx playwright show-trace test-results/quick-regeneration-FR-29-q-2a86f-one-stays-in-Recent-futures-chromium/trace.zip

    ────────────────────────────────────────────────────────────────────────────────────────────────

  1 failed
    [chromium] › tests/quick-regeneration.spec.ts:45:1 › FR-29 quick DARKER starts a new run and the previous one stays in Recent futures 
  1 did not run
  77 passed (11.1m)

E2E FAIL
==== E2E FAILURES (1) ====
✘ quick-regeneration.spec.ts › FR-29 quick DARKER starts a new run and the previous one stays in Recent futures · chromium
    Error: expect(received).toBe(expected) // Object.is equality
    Expected: "recent-future-fd5ac7dc-d50d-4802-8b73-b948ae8b224c"
    Received: undefined
    attachment: e2e/test-results/quick-regeneration-FR-29-q-2a86f-one-stays-in-Recent-futures-chromium/trace.zip
==== END E2E FAILURES ====

```

## Round 2
- Trigger: E2E RED
- To builders: no · to tester: /Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/e2e/tests/quick-regeneration.spec.ts
- Output:

```
Error Context: test-results/quick-regeneration-FR-29-D-447f2--at-10-and-is-then-disabled-chromium/error-context.md

    attachment #2: trace (application/zip) ─────────────────────────────────────────────────────────
    test-results/quick-regeneration-FR-29-D-447f2--at-10-and-is-then-disabled-chromium/trace.zip
    Usage:

        npx playwright show-trace test-results/quick-regeneration-FR-29-D-447f2--at-10-and-is-then-disabled-chromium/trace.zip

    ────────────────────────────────────────────────────────────────────────────────────────────────

  1 failed
    [chromium] › tests/quick-regeneration.spec.ts:87:1 › FR-29 DARKER clamps at 10 and is then disabled 
  78 passed (11.5m)

E2E FAIL
==== E2E FAILURES (1) ====
✘ quick-regeneration.spec.ts › FR-29 DARKER clamps at 10 and is then disabled · chromium
    Error: expect(locator).toBeDisabled() failed
    Locator: getByTestId('quick-darker')
    Expected: disabled
    Timeout: 5000ms
    Error: element(s) not found
    Call log:
      - Expect "toBeDisabled" getByTestId('quick-darker') with timeout 5000ms
      - waiting for getByTestId('quick-darker')
    attachment: e2e/test-results/quick-regeneration-FR-29-D-447f2--at-10-and-is-then-disabled-chromium/trace.zip
==== END E2E FAILURES ====

```

## Round 3
- Trigger: review not clean
- To builders: R1 · to tester: none
- INVALID  slice 16_quick-regeneration: 1 open high/medium finding(s): R1

## Recovery (in place, 2026-10-04)
- Cause of block: R1 (medium, spec-compliance) open after 3/3 rounds — `quick-actions.ts` lacked the exported contract API.
- Frontend builder exported `QuickAction` / `quickTarget(action, value)`; component delegates to it. Frontend 361/361, `ng build` OK.
- Full verify: VERIFY GREEN. First E2E attempt: stack unhealthy, Docker VM disk full (0 B) → pruned Docker build cache only (user-approved), 17 GB freed. E2E rerun: 79 passed (11.7m), E2E PASS.
- Independent re-review: R1 fixed, 0 open high/medium (R2, R3 low remain) → clean.
