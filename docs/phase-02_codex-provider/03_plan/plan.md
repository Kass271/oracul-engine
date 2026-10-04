# Plan — phase-02_codex-provider

Status: APPROVED ✔ 2026-10-04

Slices are built in this order. A slice starts only when every slice in "Depends on" is DONE.
Every FR of this phase is in exactly one slice.

Ordering principle (user-set order): fix the real sign-in first, prove it by hand on the user's real ChatGPT account,
then make the real plan-usage calls, and only then change how the stack is run and document it. Each slice is backend +
frontend (where the FRs have UI) + tests, built test-first against `api/openapi.yaml` (0.5.0) and the specs in
`02_specs/` (`chatgpt-connection.md`, `chatgpt-inference.md`, `run-modes.md`, `contract-notes.md`). Automated tests stay
stubbed (phase-01 NFR-7); phase-01 NFR-1 (runtime-only tokens) stays in force in every slice.

| Slice | FRs | Depends on | Scope |
|---|---|---|---|
| 01_signin-fix | FR-35, FR-36, FR-37, FR-41 | — | NFR-9 included. Backend: authorize request with exactly the documented parameters (`dynamic_agent_client` + `agent_name_hint=ORACUL` on first registration, issued `oaiapp_…` id without hint on reauthorization, `ext_agent_host_id=urn:uuid:<stored host uuid>` on every request incl. phase-01 bare UUIDs, fresh state/nonce/PKCE, `resource=https://api.openai.com/v1`, redirect `http://127.0.0.1:4200/callback` validated at startup); callback: state check, code exchange with `resource`, ID-token validation (JWKS RS256, iss/aud/exp, nonce), issued client id persisted only after a valid ID token and never `dynamic_agent_client`, reauthorization client-id mismatch rejected, outcomes `not_completed` / `not_verified` / `expired` / `not_eligible`; new `resetChatGptRegistration` (`DELETE /api/auth/chatgpt/registration`, 204 / 409 `RUN_IN_PROGRESS`, installation-wide, keeps the host id, best-effort revocation also on disconnect); configurable `oracul.chatgpt.revocation-url/jwks-url/issuer`. Infra: nginx.conf and Angular proxy.conf.json forward `/callback` → `/api/auth/chatgpt/callback`. Frontend: "Reset ChatGPT connection" with confirmation dialog, new callback outcome messages, sign-in conditions text (personal Plus/Pro, same computer) in the welcome sign-in hint area. Stub: the existing auto-merged stub (`docker-compose.override.yml`, `e2e/stubs/server.mjs`) is updated to the corrected documented request shape (urn:uuid host id, `/callback`, nonce, issued id without hint, ID token with nonce signed by a stub JWKS, revocation endpoint) so the existing E2E stays green; it stays auto-merged in this slice. |
| 02_plan-usage-calls | FR-38, FR-39, FR-40 | 01_signin-fix | Backend: every Responses call is `POST /v1/responses` with Bearer token, `store=false`, `stream=true`; SSE reading with success only on `response.completed` (`response.incomplete` / `response.failed` / no completion → `CHATGPT_INCOMPLETE`); model resolved once per run from `GET /v1/models` (preferred `oracul.openai.model`, else first listed, empty → `CHATGPT_NO_MODEL`), model stored on the run and shown in `ScenarioMetadata.model` (Flyway V9: `model`, `failure_provider_code`); `text.format` json_schema fallback on `subscription_sharing_unsupported_capability` (one repeat, remembered, server-side JSON validation); proactive refresh within `oracul.chatgpt.refresh-skew` (5 min) replacing both tokens; documented error mapping (not eligible, usage limit with Settings → Usage link, 503 retry ×2 with backoff, rejected with provider code, 401/auth-context → tokens cleared + reconnect, unknown code); refresh failures (session-expired codes → tokens cleared, `invalid_client` → `REGISTRATION_INVALID` / 401 `CHATGPT_REGISTRATION_INVALID`, transient → 503 `CHATGPT_UNAVAILABLE`, tokens kept). Frontend: new run-failure messages incl. provider code and usage link, "Continue with ChatGPT" on session-expired failures, `REGISTRATION_INVALID` connection state pointing to Reset. Stub: serves `GET /v1/models` and answers `stream=true` Responses calls as SSE (`response.completed`, plus control-selected incomplete/failed/error scenarios). |
| 03_run-modes-readme | FR-42, FR-43 | 02_plan-usage-calls | Infra: plain `docker compose up -d` starts real mode (no stub, real OpenAI URLs); the stub moves from the auto-merged `docker-compose.override.yml` to an explicit `docker-compose.e2e.yml` with its own database volume; the stub enforces the documented request shape (rejects bare-UUID host id, non-`/callback` redirect, missing nonce, `agent_name_hint` with an issued id → `invalid_authorize_request`; Responses without `stream=true` and `store=false` → 400); E2E leaves no stub client id in the real-mode database. Docs: `apps/oracul-engine/README.md` with Prerequisites, Run for real, Sign in step by step, Run the tests, Troubleshooting (each listed problem with cause and fix), Stop and reset — commands work on a fresh clone without edits. **Blocked by an open item, see Gates.** |

## Dependency graph

```
01_signin-fix ──► [GATE: NFR-8 check 1, manual] ──► 02_plan-usage-calls ──► [GATE: open item resolved] ──► 03_run-modes-readme
```

## Gates and open items
- **After 01_signin-fix — NFR-8 check 1 (manual, user).** Slice 02 does not start until the user has signed in
  through the ORACUL UI on their real ChatGPT account: OpenAI shows "Connect your ORACUL to ChatGPT", ORACUL shows
  "ChatGPT connected" and one generation returns real text (not stub lorem). Evidence goes to
  `04_build/01_signin-fix/real-check.md`, written by the orchestrator from the user's report. A failed check reopens
  01_signin-fix.
- **Before 03_run-modes-readme — open item, user decision required.** `factory-engine/bin/stack.mjs` runs plain
  `docker compose` in the app folder. Once the stub becomes opt-in (FR-42), the factory E2E run would start the real
  stack instead of the stubbed one. Slice 03 may not start until the user decides how this is handled (factory change
  or another approach). This plan does not choose a workaround.
- **After release — NFR-8 check 2 (manual, user)** from a fresh install; evidence in `05_release/real-check.md`. The
  phase is GREEN only after it.

## Risks
- The stub is the only automated check of the documented shape; if it drifts from OpenAI's real behaviour the stubbed
  E2E stays green while the real path fails — the NFR-8 checks are the guard.
- `/callback` deviates from the docs' `/auth/callback` example (contract-notes decision 1); if OpenAI rejects it in
  check 1, only the redirect constant and the web-server/dev-proxy location change.
- Slice 01 calls the model through the phase-01 transport; check 1 needs one real generation, so if the real Responses
  endpoint rejects the phase-01 request shape (no `stream`/`store`, fixed model) the check may only partly pass before
  slice 02 — the user decides at the gate whether sign-in alone is enough to continue.
- Enum additions (`ChatGptConnectionState`, `RunFailureCode`) break exhaustive switches in backend and frontend; the
  implementing slice must update them.
- SSE parsing, retries with backoff and the 120 s stream timeout must fit inside the 180 s run deadline; tests use an
  injected clock / short delays.
- Moving the stub to `docker-compose.e2e.yml` in slice 03 changes how every existing E2E test is started (see the open
  item above).
