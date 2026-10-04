# Contract notes — phase-02_codex-provider

Decisions behind the phase-02 changes to `api/openapi.yaml` (version 0.4.0 → 0.5.0 in the spec step, 0.5.0 → 0.6.0
in the slice-03 spec delta). Phase-01 conventions
(`docs/phase-01_mvp/02_specs/contract-notes.md`) stay in force: ApiError `{code, message}` for every 4xx/5xx, tags =
capabilities, UUID ids, ISO-8601 UTC, no schema named `Error`. Capability specs of this phase:
`chatgpt-connection.md` (FR-35, FR-36, FR-37, FR-41; NFR-9), `chatgpt-inference.md` (FR-38, FR-39, FR-40),
`run-modes.md` (FR-42, FR-43; NFR-8), `news-search.md` (FR-44, FR-46, FR-48 — added by approved scope changes),
`run-control.md` (FR-45, FR-47 — added by the approved scope change for slice 03_run-modes-readme).

Sources: developers.openai.com/siwc/token-sharing-open-source/ sign-in, profiles-and-sessions, errors-and-recovery,
models-and-inference, token-reference, preview-limitations, and https://auth.openai.com/.well-known/openid-configuration
(all read 2026-10-04).

## Operations
| Change | Operation | Backward compatible |
|---|---|---|
| new | `DELETE /api/auth/chatgpt/registration` `resetChatGptRegistration` (tag `chatgpt`) → 204 · 409 `RUN_IN_PROGRESS` · 500 | yes (addition) |
| description only | `startChatGptSignIn`, `completeChatGptSignIn` (new outcomes `not_verified`, `expired`), `disconnectChatGpt` (best-effort revocation) | yes — still 302 / 204 only |
| new response | `startRun`, `startAlternativeRun` gain `503` (`ChatGptUnavailable`) and the 401 example `CHATGPT_REGISTRATION_INVALID` | yes (additional status) |
| new (0.6.0) | `POST /api/runs/{runId}/stop` `stopRun` (tag `runs`) → 200 `GenerationRun` · 404 `RUN_NOT_FOUND` · 500 | yes (addition) |
| description only (0.6.0) | `listRecentRuns` (also STOPPED runs), `startAlternativeRun` (STOPPED parent → 409; note copied), `listRunSources` (≤ 30), `getEvidencePack`, `getStructuredScenario`, `getFutureResult` (STOPPED / empty-pack wording) | yes — same statuses |
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
| `RunStatus` (0.6.0) | + `STOPPED` (terminal); `INSUFFICIENT_EVIDENCE` kept for stored runs, no longer produced |
| `GenerationRun` (0.6.0) | + optional `evidenceNote` (`EvidenceNote`); `suggestedRealism` also set for an INSUFFICIENT_EVIDENCE note |
| `EvidenceNote`, `EvidenceNoteKind` (0.6.0) | new: `{kind: INSUFFICIENT_EVIDENCE \| NO_EVIDENCE, message, coreItems, coreNeeded}`, all required |
| `RecentRunSummary` (0.6.0) | + optional `status` (always sent: COMPLETED or STOPPED); `headline` no longer required (absent for STOPPED) |
| `Source` (0.6.0) | + optional `publisherUrl` (uri; Google News sources) |
| `RunFailureCode` (0.6.0) | description only: `NEWS_UNAVAILABLE`, `INSUFFICIENT_EVIDENCE` no longer produced (kept for stored runs) |
| `ResearchCounts`, `SearchQueryStatus` (0.6.0) | description only: ≤ 30 kept sources; Google News RSS first, GDELT fallback |

Enum additions break exhaustive `switch`es in generated-model consumers (`RunFailures.message`, frontend
`Record<ChatGptConnectionState, …>`); the implementing slice updates them — they are compile errors, not runtime risks.
`CHATGPT_RATE_LIMITED` keeps its name for the usage-limit case (no rename, so stored runs stay readable).
`STOPPED` breaks no exhaustive switch today (no `Record<RunStatus, …>` / `switch` over `RunStatus`); the
`RecentRunSummary.headline` relaxation keeps every existing fixture valid (`status` is optional in the schema for the
same reason, like `hasOpenCriticIssues`). No schema, property or enum value is renamed or removed in 0.6.0.

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
18. **Model resolution is remembered per session** in `HttpResponsesClient` (one active run per session), so stage
    code and prompt builders keep their signatures; the slug is still stored on the run (`generation_run.model`).
19. **Query expansion never retries** (phase-01 rule kept): its template fallback is the recovery. It fails the run
    only for CHATGPT_SESSION_EXPIRED, CHATGPT_REGISTRATION_INVALID and CHATGPT_PLAN_NOT_ELIGIBLE.
20. **GDELT OR-groups (FR-44).** Queries are merged into at most 4 requests `(<e1> OR <e2> …) sourcelang:english`,
    multi-word queries as quoted phrases (GDELT's documented OR syntax allows keywords or phrases and no nesting, so
    an AND of words cannot sit inside an OR). This narrows multi-word queries to exact phrases; it could not be
    tried live (every analyst call on 2026-10-04 was answered 429), so NFR-8 check 2 is the proof. GDELT does not say
    which OR element matched, so per-query attribution is derived from the article title (news-search.md step 7).
21. **NEWS_UNAVAILABLE (FR-44)** — superseded in slice 03 by FR-47 (no news never ends a run; the code is no longer produced). = every GDELT request failed or could not be sent. A request answered with zero
    articles counts as "news source reached"; an all-empty search continues as in phase-01 (FR-31).
22. **Search time budget** `oracul.news.search-budget` PT75S inside the 180 s run deadline; spacing, 429 wait,
    timeout and budget are configurable (`ORACUL_NEWS_*`), the E2E stack shortens spacing and 429 wait to 0.5 s.
    No contract change: `counts.searches` keeps counting planned queries; `SearchQueryStatus` gains a description.

## Stack modes (slice 03, replaces the earlier "Factory note")
The factory now reads `.oracul/stack.json` (`factory-engine/checks/lib/stack.mjs`), so the open item of the plan
("Before 03_run-modes-readme — user decision required") is resolved by the user's factory change of 2026-10-04: the
analyst wrote `.oracul/stack.json` with mode `e2e` = `docker-compose.yml` + `docker-compose.e2e.yml` (the stub stack
on volume `db-e2e-data`) and mode `run` = `docker-compose.yml` (real mode). `stack.mjs up|e2e` default to mode `e2e`,
`down` takes both modes down; no `COMPOSE_FILE` / `COMPOSE_PROJECT_NAME` export is needed any more. Until the
developer creates `docker-compose.e2e.yml` and deletes `docker-compose.override.yml` (GREEN of slice 03),
`check-stack` reports the missing file and `stack.mjs` refuses — E2E runs only after GREEN anyway.

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
| `oracul.news.gdelt.base-url` | `https://api.gdeltproject.org` (spacing / timeout / 429 wait / budget see news-search.md) | FR-44, FR-48 fallback |
| `oracul.news.google.base-url` | `https://news.google.com` (`/rss/search`; spacing `PT1S`, timeout `PT10S`) | FR-48 |

## Database
Flyway `V9__run_model_and_provider_code.sql`: `ALTER TABLE generation_run ADD COLUMN model VARCHAR(128) NULL, ADD
COLUMN failure_provider_code VARCHAR(64) NULL`. No credential column (NFR-1 pattern check still applies).
`chatgpt_client_registration` is unchanged (the `urn:uuid:` prefix is added on the wire, not stored).
Flyway `V10__run_control_and_publisher_url.sql` (slice 03): `ALTER TABLE generation_run ADD COLUMN evidence_note_kind
VARCHAR(32) NULL, ADD COLUMN evidence_core_items INTEGER NULL, ADD COLUMN evidence_core_needed INTEGER NULL`;
`ALTER TABLE source ADD COLUMN publisher_url TEXT NULL`. `status` stays VARCHAR(32) without a check constraint, so
`STOPPED` needs no DDL; the partial unique index `one_active_run_per_session` (QUEUED, RUNNING) already treats it as
inactive.

## Analyst decisions of the slice-03 spec delta (to confirm with the slice)
23. **Stop is an action sub-resource** `POST /api/runs/{runId}/stop` (like `/alternatives`), idempotent: a terminal
    run answers 200 with its unchanged body, so a stop that races the run's end never shows an error (FR-45 #5).
24. **STOPPED is a status, not a failure** (no `RunFailure`), so the failure view and its "Try again" are not shown;
    the stopped view carries its own generate button.
25. **The generate button moves into the progress and stopped views** as well (the progress view had no button, so
    "the generate button reads STOP" needs it there).
26. **Article-page fetches check the run guard** before each fetch, so "no further news requests" also covers
    READING_SOURCES after a STOP.
27. **Evidence note is persisted at RANKING** (three columns) instead of being recomputed from the configured
    thresholds at read time, so a later threshold change cannot change an old run's note; alternatives copy it.
28. **NO_EVIDENCE wins** over INSUFFICIENT_EVIDENCE (an empty pack at realism 10 shows only the speculative note,
    without LOWER REALISM — lowering realism cannot create news).
29. **Speculative mode** = empty pack (core + supporting + counter-signals = 0), not "zero sources": a run whose
    sources all became excluded events is just as ungrounded.
30. **The 30-source cap is applied before article fetching** with a topic round robin ranked by source quality then
    provider order (the only ranking available before events exist); the constant is not configurable so FR-46's
    "never above 30" holds for every run.
31. **Google News sources**: the article fetch follows HTTP redirects from the Google link; real Google usually
    answers its own page (JavaScript redirect), so real sources usually keep the Google link with the RSS `source`
    name and the new `publisherUrl` (FR-48 #2 fallback). A resolved URL already used by an earlier source keeps the
    Google link (unique `(run_id, url)`).
32. **Google failures fall back per group, empty answers do not**: a Google answer with zero items is a valid "no
    news" answer (EMPTY), only a failed request goes to GDELT.
33. **In-process and E2E stubs answer Google with a failure / mirror by default**: ITs default to Google 503 so every
    existing GDELT IT keeps its behaviour through the fallback; the E2E stub mirrors the GDELT stub's answers so the
    acceptance counts stay 20 / 100 (and 81 usable → 30 kept).
