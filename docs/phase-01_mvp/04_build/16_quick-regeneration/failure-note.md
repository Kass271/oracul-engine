# Failure note — 16_quick-regeneration

> **Superseded (2026-10-04):** recovered in place — see "Recovery" in rounds.md. Slice closed DONE.

Status: BLOCKED after 3 fix rounds (3 of 3 used) · 2026-10-04

## What failed
Failing check: **review**. One medium finding is still open in `review-findings.json` (round 3):

```
== review evidence ==
INVALID  slice 16_quick-regeneration: 1 open high/medium finding(s): R1
RESULT  FAIL (1 problem)
```

- **R1 (medium, spec-compliance, FR-29)** — `frontend/src/app/runs/quick-actions.ts:8`. The spec test contract (generation-runs.md, "Slice 16_quick-regeneration", Frontend) requires a pure helper exported from `quick-actions.ts`:
  `export type QuickAction = 'MORE_REALISTIC' | 'DARKER' | 'MORE_OPTIMISTIC' | 'MORE_EXTREME'` and
  `export function quickTarget(action: QuickAction, value: number): number | null`.
  The file has only a private `type Action = 'realism-up' | ...` and a private `target()` method, so nothing is exported. Slice 17 adds `quick-alternative` to the same component and expects this contract API to exist.
  Required fix: export `QuickAction` and `quickTarget(action, value)` (+2 clamped to 10 / null at 10; MORE_EXTREME −3 clamped to 1 / null at 1), and have the component call `quickTarget(action, this.scenario.<control>())` instead of its private `target()`.

Open low findings (do not block, listed for reference):
- **R2 (low, tests)** — `frontend/src/app/runs/quick-actions.spec.ts:42`: the 40-case `quickTarget` table (4 actions x v = 1..10) is only covered through the UI, not by testing the exported helper directly. Fix after R1: add `it.each(CASES)` that imports `quickTarget`.
- **R3 (low, tests)** — `backend/src/test/java/com/oracul/app/runs/QuickRegenerationIT.java:15`: `// @trace FR-29` left out on purpose (red-check would reject a backend test that is green by design), so the trace matrix will not show this backend proof of acceptance 3.

## What each round tried
1. **Round 1 — trigger: E2E RED.** Sent to the tester (`e2e/tests/quick-regeneration.spec.ts`), not to builders. Failing: `FR-29 quick DARKER starts a new run and the previous one stays in Recent futures` — expected `"recent-future-fd5ac7dc-…"`, received `undefined` (1 failed, 1 did not run, 77 passed).
2. **Round 2 — trigger: E2E RED.** Sent to the tester again (same spec file). Round 1 test passed; new failure: `FR-29 DARKER clamps at 10 and is then disabled` — `getByTestId('quick-darker')` not found, so `toBeDisabled()` timed out (1 failed, 78 passed).
3. **Round 3 — trigger: review not clean.** R1 sent to builders, nothing to the tester. Review still reports R1 open → `INVALID … 1 open high/medium finding(s): R1`. No rounds left.

## Not delivered
- FR-29 — Quick regeneration (quick actions MORE_REALISTIC / DARKER / MORE_OPTIMISTIC / MORE_EXTREME): the behaviour runs end to end, but the contract API (`QuickAction`, `quickTarget`) required by the spec is missing, so the slice cannot be closed.

## Impact
Dependent slices: 17 (adds `quick-alternative` to the same quick-actions component and expects the exported `QuickAction` / `quickTarget` API from R1) → decision: CONTINUE — only if slice 17 first exports `QuickAction` / `quickTarget` as R1 asks; otherwise STOP before slice 17.
