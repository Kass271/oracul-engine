# Acceptance report — phase-02_codex-provider

Built from traceability.md, test reports and screenshots only. Every ✔ links its evidence.

| FR | Title | Result | Evidence |
|---|---|---|---|
| FR-35 | Documented authorize request | ✔ | [FR-35-first-registration-connected.png](screenshots/FR-35-first-registration-connected.png) · [FR-35-reauthorization-connected.png](screenshots/FR-35-reauthorization-connected.png) · [traceability](traceability.md) |
| FR-36 | Callback handling, nonce check and issued client id persistence | ✔ | [FR-36-connected.png](screenshots/FR-36-connected.png) · [FR-36-expired.png](screenshots/FR-36-expired.png) · [FR-36-not-completed.png](screenshots/FR-36-not-completed.png) · [FR-36-not-verified.png](screenshots/FR-36-not-verified.png) · [traceability](traceability.md) |
| FR-37 | Reset ChatGPT connection | ✔ | [FR-37-reset-dialog.png](screenshots/FR-37-reset-dialog.png) · [FR-37-reset-done.png](screenshots/FR-37-reset-done.png) · [FR-37-run-in-progress.png](screenshots/FR-37-run-in-progress.png) · [traceability](traceability.md) |
| FR-38 | Documented plan-usage Responses call | ✔ | [FR-38-model-chip.png](screenshots/FR-38-model-chip.png) · [traceability](traceability.md) |
| FR-39 | Plain-language messages for documented OpenAI errors | ✔ | [FR-39-not-eligible.png](screenshots/FR-39-not-eligible.png) · [FR-39-provider-code.png](screenshots/FR-39-provider-code.png) · [FR-39-retried-then-completed.png](screenshots/FR-39-retried-then-completed.png) · [FR-39-session-expired.png](screenshots/FR-39-session-expired.png) · [FR-39-usage-limit.png](screenshots/FR-39-usage-limit.png) · [traceability](traceability.md) |
| FR-40 | Refresh failures end the session cleanly | ✔ | [FR-40-registration-invalid.png](screenshots/FR-40-registration-invalid.png) · [FR-40-session-expired.png](screenshots/FR-40-session-expired.png) · [FR-40-unavailable.png](screenshots/FR-40-unavailable.png) · [traceability](traceability.md) |
| FR-41 | Sign-in conditions shown in the UI | ✔ | [FR-41-conditions.png](screenshots/FR-41-conditions.png) · [traceability](traceability.md) |
| FR-42 | Opt-in E2E stub with its own data | ✔ | [FR-42-stub-signin.png](screenshots/FR-42-stub-signin.png) · [traceability](traceability.md) |
| FR-43 | Comprehensive README | ✔ | [traceability](traceability.md) |
| FR-44 | Real news search within GDELT's limits | ✔ | [traceability](traceability.md) |
| FR-45 | Stop a generation and start a new one | ✔ | [FR-45-new-run-after-stop.png](screenshots/FR-45-new-run-after-stop.png) · [FR-45-recent-futures-stopped.png](screenshots/FR-45-recent-futures-stopped.png) · [FR-45-reset-after-stop.png](screenshots/FR-45-reset-after-stop.png) · [FR-45-stop-failed.png](screenshots/FR-45-stop-failed.png) · [FR-45-stopped.png](screenshots/FR-45-stopped.png) · [traceability](traceability.md) |
| FR-46 | At most 30 sources per run | ✔ | [traceability](traceability.md) |
| FR-47 | Always generate; note insufficient evidence at the end | ✔ | [FR-47-insufficient-evidence-note.png](screenshots/FR-47-insufficient-evidence-note.png) · [FR-47-lower-realism-started.png](screenshots/FR-47-lower-realism-started.png) · [FR-47-no-evidence-note.png](screenshots/FR-47-no-evidence-note.png) · [traceability](traceability.md) |
| FR-48 | Google News RSS as the main news source, GDELT as fallback | ✔ | [traceability](traceability.md) |

## Blocked / not delivered
- None blocked, none failed (traceability: 0 ✘, 0 blocked).
- FR-48 caveat: the E2E stub accepts any query, so the ✔ covers stub mode only. In real mode (see [real-check.md](../real-check.md)) Google News returned 0 articles for OR-group queries and the run ended with the no-evidence note. Review findings R1, R2 (high) and R3 (medium) are wontfix by user decision 2026-10-06; this part is rewritten in the next phase.

## Open low-severity review findings
- R5 (FR-44, low, open): the backend suite uses about 26 distinct @TestPropertySource sets, each starting its own Spring context, which slows the suite.
