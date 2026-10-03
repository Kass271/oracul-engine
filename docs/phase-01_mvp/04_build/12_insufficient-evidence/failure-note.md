> **Superseded:** the slice was recovered in place and closed DONE after review round 2. See rounds.md.

# Failure note — 12_insufficient-evidence

Status: BLOCKED after 3 fix rounds (3 of 3 used) · 2026-10-03

## What failed
- Failing check: **e2e**. The Playwright E2E run against the Docker stack did not pass.
- Last output (round 3):

```
Playwright E2E against the Docker stack failed:
Command timed out after 600 seconds. No output available.
```

- The last E2E run that produced output (round 1) ended with `9 failed, 28 did not run, 36 passed (7.1m)`. The FR-31 test was among the failures:
  `[chromium] › tests/insufficient-evidence.spec.ts:79:3 › FR-31 Insufficient evidence › FR-31 Realism 10 with 2 core items ends in the insufficient view and LOWER REALISM starts a Realism 8 run`
  - The other failures were existing specs from earlier slices: critic (FR-22), events (FR-14/15), evidence-pack (FR-16/17/18), future-story (FR-23/25), run-failures (FR-32), run-start (FR-24), search-sources (FR-12) and validated-scenario (FR-19/20/21).
  - Typical assertion: `Expected: "COMPLETED"` / `Received: "INSUFFICIENT_EVIDENCE"` (timeout of 40000ms while polling `/api/runs/{id}` status, `validated-scenario.spec.ts:58`). This suggests the new sufficiency check also stops the acceptance runs that earlier slices expect to complete.
- No review findings file (`review-findings.json`) exists for this slice.

## What each round tried
1. Round 1 (trigger: E2E RED). The E2E output went to the builders (not to the tester). Result: 9 E2E failures, including the FR-31 spec and regressions in the earlier slices' acceptance-run specs (runs ended `INSUFFICIENT_EVIDENCE` instead of `COMPLETED`).
2. Round 2 (trigger: E2E RED). The E2E output went to the builders again (not to the tester). The Docker stack rebuilt and came up (`frontend http://localhost:4200 · backend http://localhost:8080/api · health http://localhost:8080/actuator/health`). The output does not show a passing E2E result.
3. Round 3 (trigger: E2E RED). The tester was asked to fix `e2e/tests/insufficient-evidence.spec.ts`. The builders were not involved. Result: the E2E command timed out after 600 seconds with no output.

## Not delivered
- FR-31 — Insufficient evidence (Realism 10 with too few core items ends in the insufficient view; LOWER REALISM starts a Realism 8 run)

## Impact
Dependent slices: not identified in the available evidence (rounds.md, red-evidence.md). Earlier slices' E2E specs (FR-12, FR-14/15, FR-16/17/18, FR-19/20/21, FR-22, FR-23/25, FR-24, FR-32) regressed in round 1, and the evidence does not show that the regression was fixed. → decision: STOP until it is confirmed that the stack no longer ends acceptance runs as `INSUFFICIENT_EVIDENCE`.
