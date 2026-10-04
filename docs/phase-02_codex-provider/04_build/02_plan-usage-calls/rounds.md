# Rounds — 02_plan-usage-calls

Run from the main session (user-approved 2026-10-04: same agents and gates, no build-slice.js). Scope change before the build: FR-44 added after real check 1 (real GDELT 429s, see 01_scope clarification-log Round 3 and gdelt-probe.md).

| Round | Trigger | Who | Result |
|---|---|---|---|
| spec | Step 4a | analyst | spec delta FR-38/39/40 + new news-search.md (FR-44); check --stage spec OK |
| red | Step 4b | tester | 540 backend + 24 frontend RED tests; red-check (orchestrator) RESULT: RED |
| 1 | green | backend-builder + frontend-builder | 535/540 green; 5 flagged as test problems |
| 1 | test-fix | tester | 5 tests repaired; cross-test GDELT leak (pending runs from earlier ITs) fixed on both sides; 2 clean full runs |
| 1 | verify RED (coverage 94.5% < 94.9%) | tester | RawHttpGetTest + UrlNormalizerTest → 95.15% |
| 1 | verify GREEN · E2E 125/1 failed | backend-builder (triage) | not a code bug: a second browser client used the E2E stack during the run (6+6 classification calls) |
| 1 | E2E rerun on a reused backend: 1 failed | orchestrator | the text.format fallback flag was still set from the previous run (backend lifetime, per spec) → restart backend + stub |
| 1 | E2E 128/128 PASS | — | — |
| 2 | review: R1 high (TLS hostname not verified in RawHttpGet), R2/R3 medium | backend-builder (R1, R5), tester (R2 TLS tests, R3 fallback spec runs last + global-setup restarts backend) | verify GREEN (95.2%) |
| 3 | re-review | reviewer | R1–R3 + R5 fixed; open lows R4, R6–R9; clean |
