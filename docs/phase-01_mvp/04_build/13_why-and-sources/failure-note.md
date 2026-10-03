# Failure note — 13_why-and-sources

> **SUPERSEDED** — the slice was recovered in place and closed DONE; see "Recovery" in rounds.md. Correction: round 3 was not blocked by the guard — its E2E ran and was killed by the runner's 600 s timeout (exit 124).

Status: BLOCKED after 3 fix rounds (3 of 3 used) · 2026-10-03

## What failed
- Failing check: **e2e** (Playwright E2E against the Docker stack, subStep `e2e`).
- No review findings file (`review-findings.json`) exists for this slice, so no open reviewer findings are recorded.
- Last output excerpt:

```
Playwright E2E against the Docker stack failed:
subStep = e2e
docker compose up -d --build …
UP  frontend http://localhost:4200  ·  backend http://localhost:8080/api  ·  health http://localhost:8080/actuator/health
```

The stack came up (frontend, backend and health URLs all reported UP), but the Playwright run failed. The excerpt does not name the failing spec. The E2E test tagged for this slice is `e2e/tests/why-and-sources.spec.ts` (FR-26, FR-27).

## What each round tried
1. Round 1. Trigger: E2E RED. Sent to builders (not to the tester). The builders tried to re-run the full stack E2E (`node …/bin/stack.mjs e2e`). The oracul guard hook blocked it: "E2E and the Docker stack run only in the workflow's E2E step (subStep e2e). In a test-fix round the tester may verify a repair with: node <engine>/bin/stack.mjs e2e --scratch --grep <spec file>."
2. Round 2. Trigger: E2E RED. Sent to builders (not to the tester). The same full-stack E2E call was blocked again by the same guard message.
3. Round 3 (logged in `rounds.md` with the duplicate heading "Round 2"). Trigger: E2E RED. Sent to builders (not to the tester). The same full-stack E2E call was blocked again by the same guard message.

None of the rounds checked a repair against E2E inside the round. The scoped `--scratch --grep <spec file>` path was never used, and the round was never sent to the tester. Each round ended without E2E evidence. The workflow's E2E step then failed again.

## Not delivered
- FR-26: WHY panel (reasoning chain with step cards, arrows and evidence chips; tests in `frontend/src/app/result/why-panel.spec.ts` and `e2e/tests/why-and-sources.spec.ts`)
- FR-27: Sources panel (evidence sources that the WHY chips link to; tests in `frontend/src/app/result/sources-panel.spec.ts` and `e2e/tests/why-and-sources.spec.ts`)

## Impact
Dependent slices: not identified in the available slice evidence (rounds.md, red-evidence.md) → decision: to be set by the orchestrator (CONTINUE | STOP)
