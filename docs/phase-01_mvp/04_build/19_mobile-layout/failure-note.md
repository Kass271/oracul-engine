# Failure note — 19_mobile-layout

> **Superseded (2026-10-04):** recovered in place — see "Recovery" in rounds.md. Slice closed DONE.

Status: BLOCKED after 3 fix rounds (3 of 3 used) · 2026-10-04

## What failed
Failing check: **e2e** (Playwright E2E against the Docker stack). 1 failed, 93 passed (13.5m).

- Test: `[chromium] › tests/mobile-layout.spec.ts:42:3 › FR-34 mobile layout › open, edit, close via button / Escape / backdrop keeps values`
- After a close action, the scenario panel is still visible; the drawer does not close.

```
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
```

Trace: `npx playwright show-trace test-results/mobile-layout-FR-34-mobile-29d34-scape-backdrop-keeps-values-chromium/trace.zip`

No review findings file (`review-findings.json`) exists for this slice. The independent review did not run, so the slice has no review record.

## What each round tried
1. Round 1 had trigger E2E RED and was sent to the builders only. The same test failed in the same way: `scenario-panel` was still visible after close.
2. Round 2 had trigger verify RED and was sent to the builders only. The FR-34 traceability check passed (`e2e/tests/mobile-layout.spec.ts`, `frontend/src/app/app.mobile.spec.ts`). The coverage ratchet failed: `INVALID frontend 99.9% < baseline 100.0%` (`VERIFY RED: check-coverage`).
3. Round 3 had trigger E2E RED and was sent to the builders and to the tester (`frontend/src/app/app.mobile.spec.ts`). The coverage problem from round 2 did not come back. The E2E test still failed the same way as in round 1.

The RED baseline is in `red-evidence.md`. It shows the frontend unit tests in `app.mobile.spec.ts` failing because the over-mode drawer and the `scenario-drawer-toggle` were missing. All 3 rounds are recorded in `rounds.md`.

## Not delivered
- FR-34: mobile layout. The scenario controls should sit in an over-mode drawer on mobile. The drawer should open from the header toggle and close with the close button, Escape or a backdrop click, keeping the values entered. Closing does not work end to end.

## Impact
Dependent slices: not stated in the provided facts. Check the slice plan for any slice that depends on FR-34. → decision: CONTINUE if no slice depends on FR-34, otherwise STOP.
