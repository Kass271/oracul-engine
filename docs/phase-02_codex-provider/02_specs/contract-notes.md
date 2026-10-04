# Contract notes — phase-02_codex-provider

Decisions behind the phase-02 changes to `api/openapi.yaml` (version 0.4.0 → 0.5.0). Phase-01 conventions
(`docs/phase-01_mvp/02_specs/contract-notes.md`) stay in force: ApiError `{code, message}` for every 4xx/5xx, tags =
capabilities, UUID ids, ISO-8601 UTC, no schema named `Error`. Capability specs of this phase:
`chatgpt-connection.md` (FR-35, FR-36, FR-37, FR-41; NFR-9), `chatgpt-inference.md` (FR-38, FR-39, FR-40),
`run-modes.md` (FR-42, FR-43; NFR-8).

Sources: developers.openai.com/siwc/token-sharing-open-source/ sign-in, profiles-and-sessions, errors-and-recovery,
models-and-inference, token-reference, preview-limitations, and https://auth.openai.com/.well-known/openid-configuration
(all read 2026-10-04).

## Operations
| Change | Operation | Backward compatible |
|---|---|---|
| new | `DELETE /api/auth/chatgpt/registration` `resetChatGptRegistration` (tag `chatgpt`) → 204 · 409 `RUN_IN_PROGRESS` · 500 | yes (addition) |
| description only | `startChatGptSignIn`, `completeChatGptSignIn` (new outcomes `not_verified`, `expired`), `disconnectChatGpt` (best-effort revocation) | yes — still 302 / 204 only |
| new response | `startRun`, `startAlternativeRun` gain `503` (`ChatGptUnavailable`) and the 401 example `CHATGPT_REGISTRATION_INVALID` | yes (additional status) |
| none removed | every phase-01 operation and operationId is kept | — |

Reset is a `DELETE` on the registration resource (the stored issued client id), separate from `DELETE
…/connection` (one session's tokens) because it is installation-wide and has its own conflict rule.

## Schemas
| Schema | Change |
|---|---|
| `ChatGptConnectionState` | + `REGISTRATION_INVALID` |
| `RunFailureCode` | + `CHATGPT_PLAN_NOT_ELIGIBLE`, `CHATGPT_REGISTRATION_INVALID`, `CHATGPT_INCOMPLETE`, `CHATGPT_REQUEST_REJECTED`, `CHATGPT_UNEXPECTED_ERROR`, `CHATGPT_NO_MODEL`; messages of `CHATGPT_RATE_LIMITED` and `CHATGPT_UNAVAILABLE` reworded (stored rows of old runs keep their stored text) |
| `RunFailure` | + optional `providerCode` (pattern `^[A-Za-z0-9_.:-]{1,64}$`) |
| `ScenarioMetadata` | + optional `model` (1–128 chars) |
| responses | + `ChatGptUnavailable` (503), + `RunInProgress` (409) |

Enum additions break exhaustive `switch`es in generated-model consumers (`RunFailures.message`, frontend
`Record<ChatGptConnectionState, …>`); the implementing slice updates them — they are compile errors, not runtime risks.
`CHATGPT_RATE_LIMITED` keeps its name for the usage-limit case (no rename, so stored runs stay readable).

## Error codes added
| Source | HTTP | code | message |
|---|---|---|---|
| reset while a run is QUEUED/RUNNING | 409 | RUN_IN_PROGRESS | Wait until the current run finishes |
| refresh answered `invalid_client` (startRun / startAlternativeRun) | 401 | CHATGPT_REGISTRATION_INVALID | ChatGPT registration is no longer valid — use Reset ChatGPT connection, then reconnect |
| refresh failed transiently (5xx, timeout, network) at startRun / startAlternativeRun | 503 | CHATGPT_UNAVAILABLE | ChatGPT is temporarily unavailable — try again in a few minutes |
Run failures (not HTTP errors) are listed in `chatgpt-inference.md` FR-39 and in the `RunFailureCode` description.

## Browser-redirect endpoint (OAuth callback)
- Registered redirect URI: `http://127.0.0.1:4200/callback` (was `/auth/callback`). The web server forwards
  `/callback` to `GET /api/auth/chatgpt/callback`; the API path itself is unchanged so the generated interface stays.
- New outcome values of the `chatgpt` query parameter: `not_verified` (ID token invalid) and `expired` (code exchange
  `invalid_grant`). `not_completed` covers state/error/client-id problems and other token-endpoint failures.
- OpenAI may append `scope` to the callback; unknown query parameters are ignored (granted scopes come from the token
  response, as documented).

## Analyst decisions to confirm at spec approval
1. **`/callback` vs. the docs' example.** The sign-in page's example uses `/auth/callback` and says only the port may
   vary between sign-ins. The approved requirements (FR-35, NFR-9) and the user's hand-built real request of
   2026-10-04 (accepted by OpenAI) use `/callback`; the spec follows the requirements. The path is one constant
   (`oracul.chatgpt.redirect-uri` + its validation + the nginx/dev-proxy location).
2. **`ext_agent_host_id` on reauthorization.** Phase-01 sent it only on first registration; the profiles-and-sessions
   page says "Include this host's stable ext_agent_host_id" on reauthorization and FR-35 requires the same value, so it
   is sent on every authorize request.
3. **No `id_token_hint` / `login_hint`.** Optional in the docs; tokens are runtime-only and there is one account per
   installation, so ORACUL sends neither (the user sees OpenAI's account selector on reauthorization).
4. **ID-token validation** uses the JWKS (`https://auth.openai.com/.well-known/jwks.json`, RS256 per the OpenID
   configuration). The "returning account identity must match" check of the docs is not applied (single account;
   multi-account is out of scope).
5. **Client id persisted only after a valid ID token** (phase-01 persisted right after the exchange), so an
   unverifiable sign-in never leaves state behind.
6. **Revocation.** The OpenID configuration publishes `revocation_endpoint`
   `https://auth.openai.com/api/accounts/oauth/revoke`; Reset and Disconnect call it best-effort (form `token`,
   `token_type_hint=refresh_token`, `client_id`, one attempt, outcome ignored, no user message). The docs' "retry with
   backoff and tell the user when remote revocation was not confirmed" is not implemented (not in the requirements);
   the README tells the user they can disconnect the app in ChatGPT Settings.
7. **Transient refresh failures keep the tokens** (docs: "Do not erase credentials solely because of a temporary
   network or infrastructure failure") → 503 / CHATGPT_UNAVAILABLE instead of phase-01's SESSION_EXPIRED. Undocumented
   4xx refresh answers keep the phase-01 behaviour (SESSION_EXPIRED).
8. **401 from Responses no longer triggers refresh-and-retry** (phase-01): FR-39 requires tokens cleared and
   "ChatGPT session expired — please reconnect". Proactive refresh 5 min before expiry (FR-38) avoids 401s from normal
   expiry.
9. **Plan not eligible during a run** (`subscription_sharing_user_not_eligible`) fails the run but leaves the
   connection state unchanged (FR-39: neither retry nor restart sign-in).
10. **Model choice.** Resolved once per run from `GET /v1/models` (`models[].slug`, server order). Preferred model =
    `oracul.openai.model` (default `gpt-5`) if listed (any visibility), else the first `visibility: "list"` entry, else
    the first entry. Empty catalogue → run failure `CHATGPT_NO_MODEL` (a message not worded in the requirements; needed
    so the path is not a generic error).
11. **Structured output fallback is remembered** for the backend's lifetime after the first
    `subscription_sharing_unsupported_capability` on `text.format`, so later calls do not pay a rejected round trip.
12. **Tolerant answer reading.** Requests always send `stream:true`; a `200 application/json` answer (no SSE) is
    accepted as the final response object (success only for `status: completed`). Real OpenAI streams; this keeps
    in-process test stubs simple and does not weaken FR-38 (success still only on completion).
13. **Retries.** Retry class (FR-39 row 4) gets at most 2 retries with waits `retry-delay`, 2 × `retry-delay` (1 s,
    2 s); phase-01 had one retry for all transport failures. Rejected/limit/auth errors are never retried.
14. **Stream timeout.** `oracul.openai.timeout` (PT30S) bounds connect + response headers; a new
    `oracul.openai.stream-timeout` (PT120S) bounds one streamed answer. The 180 s run deadline still ends the run.
15. **FR-41 placement.** The conditions text is shown in the welcome view under "Connect ChatGPT to generate" (the
    sign-in hint area), not in the header toolbar, which has no room on 375 px phones.
16. **Reset scope.** Reset is installation-wide (one registration row): it clears every session's tokens and is refused
    while any run of any session is active.
17. **NFR-8 evidence** files are written by the orchestrator from the user's report; they are not test artefacts.

## Factory note (no factory change made)
`factory-engine/bin/stack.mjs` runs plain `docker compose up/down` in the app folder. After FR-42 that starts the
**real** stack. Docker Compose honours `COMPOSE_FILE`, and `stack.mjs` passes its environment through, so every E2E
stack command must run with `COMPOSE_FILE=docker-compose.yml:docker-compose.e2e.yml` exported (e.g.
`COMPOSE_FILE=docker-compose.yml:docker-compose.e2e.yml node factory-engine/bin/stack.mjs e2e --detach`). The same
variable is needed for `stack.mjs down`, otherwise the `stub` container is left running as an orphan. Workflows that
call `stack.mjs` without it will run Playwright against the real stack and fail at the first stub `__control` call.
This is reported to the orchestrator; it is not patched here.

## External endpoints (all configurable — NFR-7 / NFR-9)
| Property | Default | Used by |
|---|---|---|
| `oracul.chatgpt.authorize-url` | `https://auth.openai.com/api/accounts/authorize` | FR-35 |
| `oracul.chatgpt.token-url` | `https://auth.openai.com/api/accounts/oauth/token` | FR-36, FR-38/40 refresh |
| `oracul.chatgpt.revocation-url` | `https://auth.openai.com/api/accounts/oauth/revoke` | FR-37, disconnect |
| `oracul.chatgpt.jwks-url` | `https://auth.openai.com/.well-known/jwks.json` | FR-36 |
| `oracul.chatgpt.issuer` | `https://auth.openai.com` | FR-36 |
| `oracul.chatgpt.redirect-uri` | `http://127.0.0.1:4200/callback` | FR-35, FR-36, NFR-9 |
| `oracul.chatgpt.refresh-skew` | `PT5M` | FR-38 |
| `oracul.openai.responses-base-url` | `https://api.openai.com/v1` (`/responses`, `/models`) | FR-38, FR-39 |
| `oracul.openai.model` | `gpt-5` (preferred) | FR-38 |
| `oracul.openai.stream-timeout` | `PT120S` | FR-38 |

## Database
Flyway `V9__run_model_and_provider_code.sql`: `ALTER TABLE generation_run ADD COLUMN model VARCHAR(128) NULL, ADD
COLUMN failure_provider_code VARCHAR(64) NULL`. No credential column (NFR-1 pattern check still applies).
`chatgpt_client_registration` is unchanged (the `urn:uuid:` prefix is added on the wire, not stored).
