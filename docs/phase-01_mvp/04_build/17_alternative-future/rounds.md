
## Round 1
- Trigger: E2E RED
- To builders: yes · to tester: no
- Output:

```
Error Context: test-results/alternative-future-FR-30-A-f75db-produces-a-different-future-chromium/error-context.md

    attachment #2: trace (application/zip) ─────────────────────────────────────────────────────────
    test-results/alternative-future-FR-30-A-f75db-produces-a-different-future-chromium/trace.zip
    Usage:

        npx playwright show-trace test-results/alternative-future-FR-30-A-f75db-produces-a-different-future-chromium/trace.zip

    ────────────────────────────────────────────────────────────────────────────────────────────────

  1 failed
    [chromium] › tests/alternative-future.spec.ts:66:1 › FR-30 ALTERNATIVE FUTURE reuses the evidence and produces a different future 
  2 did not run
  79 passed (13.0m)

E2E FAIL
==== E2E FAILURES (1) ====
✘ alternative-future.spec.ts › FR-30 ALTERNATIVE FUTURE reuses the evidence and produces a different future · chromium
    Error: expect(locator).toBeVisible() failed
    Locator: getByTestId('result-view')
    Expected: visible
    Timeout: 60000ms
    Error: element(s) not found
    Call log:
      - Expect "toBeVisible" getByTestId('result-view') with timeout 60000ms
      - waiting for getByTestId('result-view')
    attachment: e2e/test-results/alternative-future-FR-30-A-f75db-produces-a-different-future-chromium/trace.zip
==== END E2E FAILURES ====

```
