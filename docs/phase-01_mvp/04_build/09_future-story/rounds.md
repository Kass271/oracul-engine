
## Recovery (GREEN run manually, review rounds 1–2) — 2026-10-03
- The workflow crashed in red-check (the runner returned no structured output). The orchestrator re-ran red-check in the foreground (RESULT RED) and ran GREEN manually: backend-builder and frontend-builder.
- The tester restored frontend coverage to 100% and extended the E2E stub (STORY_WRITING + /__control/story). E2E found a production defect: "Evidence used" showed sourcesUsed instead of eventsSelected. The frontend-builder fixed it.
- Review round 1: R1 medium (the FR-24 "no technical details" E2E assertion had been lost). Fixed by the tester. Per a user decision, low findings are no longer fixed, only listed.
- A user-requested test-infra speed-up landed in the same window: one shared Postgres container per JVM with one database per context, a 60 s startup timeout, the context cache capped at 8, and maxParallelForks=2. Backend suite: about 4.5 min → 2m27s. It also fixes the 40-minute hang, which came from per-context containers exhausting a full Docker VM; the user approved a build-cache prune.
- Review round 2: clean. Open lows: R2, R3, R4, R6, R7, R8.
- verify GREEN (942 backend tests, 93.6% / frontend 100%); Playwright 62 passed. Slice closed DONE.
