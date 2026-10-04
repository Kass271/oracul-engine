# Failure note — 18_custom-wildcards-output

> **Superseded (2026-10-04):** recovered in place — see "Recovery" in rounds.md. Slice closed DONE.

Status: BLOCKED after 3 of 3 fix rounds · 2026-10-04

## What failed
Failing check: **review**. One medium finding is still open in `review-findings.json` (round 3).

```
== review evidence ==
INVALID  slice 18_custom-wildcards-output: 1 open high/medium finding(s): R1
RESULT  FAIL (1 problem)
```

- **R1 (medium, spec-compliance, FR-5)** — `frontend/src/app/scenario/custom-wildcards.ts:27`.
  The test contract in `scenario-panel.md` puts `custom-wildcard-add` on the `mat-stroked-button` and `custom-wildcard-remove-<i>` on the `mat-icon-button` (aria-label "Remove <label>"). The code puts `data-testid="custom-wildcard-add"` on an inner `<span>` and `custom-wildcard-remove-<i>` on the `<mat-icon>`. So `getByTestId(...)` checks for enabled state, aria-label and focus target an element that cannot be interacted with. The unit tests pass only because they call `.closest('button')`.
  Fix proposed by the reviewer: move `data-testid="custom-wildcard-add"` onto `<button mat-stroked-button>` and `[attr.data-testid]="'custom-wildcard-remove-' + i"` onto `<button mat-icon-button>`. The existing `.closest('button')` calls still work after the move.

Low findings still open (they do not block, so no fix loop):
- R2 (low, correctness, FR-5): `ScenarioConfigurationDeserializer.java:146`. The backend trims with `String.strip()` and the UI trims with JS `trim()`. They handle NBSP, U+FEFF and U+2007/U+202F differently, so the API accepts `" "` as a label. This cannot happen through the panel.
- R3 (low, ui, FR-5): `custom-wildcards.ts:16`. The error is a separate `role="alert"` div. The field has no `aria-describedby`/`aria-invalid` link to it and no Material error styling.

## What each round tried
0. (Before round 1) The E2E run timed out after 600 s. This was an infrastructure problem, not a code failure, so it was not triaged. The slice stopped and resumed at stage green.
1. E2E RED: `events.spec.ts:73` (FR-14/FR-15, "41 normalized, classified events") expected 3 `EVENT_NORMALIZATION` entries and got 6. The run had 83 passed, 1 failed and 3 not run. It was sent to the tester (`e2e/tests/custom-wildcards-output.spec.ts`), not to the builders.
2. E2E RED: `custom-wildcards-output.spec.ts:63` ("connected run sends the custom wildcard and the fixed output") got `undefined` instead of a string because a 40 s predicate wait timed out. The run had 83 passed, 1 failed and 3 not run. It was sent to the tester, not to the builders.
3. Review not clean: R1 was sent to the builders and nothing to the tester. R1 was still open at the end of the round, and this used up the round budget.

## Not delivered
- FR-5 — Custom wildcards (R1 open: the test IDs for the add and remove buttons are not on the buttons)
- FR-6 — Output settings (the slice was not committed. The review found no open FR-6 finding.)

## Impact
Dependent slices: the slice evidence does not record any → decision: CONTINUE. The only open blocking item is R1, a one-line placement of test IDs with a known fix. The code stays in the working tree, so the fix can be applied when the slice resumes at green.
