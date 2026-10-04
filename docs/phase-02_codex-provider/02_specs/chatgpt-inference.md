# Spec — ChatGPT inference transport, errors and refresh

Covers: FR-38, FR-39, FR-40

Delta against phase-01 `scenario-reasoning.md` / `research-pipeline.md` / `future-result.md` (request transport of
every ChatGPT call), `generation-runs.md` (run failure codes) and `chatgpt-connection.md` (refresh), and against the
current code `HttpResponsesClient`, `ChatGptCallException`, `ChatGptAuthService.refresh`, `ChatGptTokenClient`,
`RunFailures` and the prompt body builders (`research/PromptText.body`, `QueryExpansionPrompt`,
`ScenarioGenerationPrompt`, `ScenarioCriticPrompt`, `StoryWritingPrompt`). Source: developers.openai.com/siwc/
token-sharing-open-source/ models-and-inference, errors-and-recovery, profiles-and-sessions, token-reference,
preview-limitations (read 2026-10-04).

## Purpose
Every generation call follows OpenAI's documented plan-usage transport (streamed Responses call with `store:false`,
`stream:true`, a model from the account's own catalogue, success only on `response.completed`), every documented
error ends in a plain-language message with a next step, and refresh failures end the session cleanly.

## What changes against phase-01 / current code
| Area | Today (code) | After this spec |
|---|---|---|
| Request body | `store:false`, no `stream` | `store:false` **and** `stream:true` on every body; header `Accept: text/event-stream` |
| Answer | one JSON object, success iff `status == "completed"` | SSE stream; success only on event `response.completed` |
| Model | fixed `oracul.openai.model` (`gpt-5`) | per run from `GET /v1/models`; `oracul.openai.model` becomes the *preferred* model; used slug stored on the run and shown in the metadata |
| `text.format` json_schema rejected | not handled | one fallback without `text.format` + JSON instruction; remembered for the backend's lifetime |
| 401/403 from Responses | forced refresh + one retry | 401 (any) and the documented auth codes → tokens cleared, CHATGPT_SESSION_EXPIRED, no retry |
| 429 | CHATGPT_RATE_LIMITED "ChatGPT plan limit reached — try again later" | same code, message "ChatGPT usage limit reached — try again later" + link to ChatGPT Settings → Usage |
| 5xx / network / timeout | one retry after `retry-delay` → CHATGPT_UNAVAILABLE "ChatGPT is unavailable right now — try again later" | at most 2 retries (delays `retry-delay`, 2 × `retry-delay`) → CHATGPT_UNAVAILABLE "ChatGPT is temporarily unavailable — try again in a few minutes" |
| Other provider errors | CHATGPT_UNAVAILABLE | dedicated codes (table below); never a generic 500 |
| Refresh form | `grant_type`, `refresh_token`, `client_id` | + `resource=https://api.openai.com/v1` |
| Refresh trigger | `refresh-skew` PT60S | `refresh-skew` PT5M |
| Refresh failure | any failure → SESSION_EXPIRED | documented codes decide (FR-40); transient failures keep the tokens |

## Data
| Entity | Field | Type | Rules |
|---|---|---|---|
| generation_run | model | varchar(128) NULL | **new** (Flyway `V9__run_model_and_provider_code.sql`): slug resolved for this run (FR-38); NULL for runs before V9 and for runs that failed before resolution |
| generation_run | failure_provider_code | varchar(64) NULL | **new** (V9): sanitized OpenAI error code, set only with failure codes CHATGPT_REQUEST_REJECTED / CHATGPT_UNEXPECTED_ERROR |
| (memory) `HttpResponsesClient` | structuredOutputSupported | boolean | starts true; set false for the backend's lifetime after the first FR-38 fallback succeeded in getting past `subscription_sharing_unsupported_capability` on `text.format` |

### Configuration
| Property | Default | Change |
|---|---|---|
| `oracul.openai.responses-base-url` | `https://api.openai.com/v1` | unchanged; `POST <base>/responses`, `GET <base>/models` |
| `oracul.openai.model` | `gpt-5` | meaning changed: preferred model slug |
| `oracul.openai.timeout` | `PT30S` | now: connect + time to response headers |
| `oracul.openai.stream-timeout` | `PT120S` | new: whole stream of one call; the run deadline (180 s) still applies |
| `oracul.openai.retry-delay` | `PT1S` | base of the backoff (1×, 2×) |
| `oracul.chatgpt.refresh-skew` | `PT5M` | was PT60S |

### Sanitized provider code
`code` from the provider is shown to the user only if it matches `^[A-Za-z0-9_.:-]{1,64}$`; otherwise the shown and
stored value is `unknown_error`. A missing code on a non-2xx answer is shown as `http_<status>` (e.g. `http_403`).

## Behaviour

### FR-38 — Documented plan-usage Responses call
- Happy path:
  1. **Model resolution (once per run)**: right after the run's task starts (QUEUED → RUNNING for standard runs,
     QUEUED → EXPLORING_FUTURES for alternatives) and before the first ChatGPT call, `GET <base>/models` with
     `Authorization: Bearer <access token>` (after `requireUsableCredentials`, i.e. refreshed if needed). The answer
     `{"models":[{"slug","display_name","visibility",…}, …]}` is read in server order. Chosen slug = the preferred
     `oracul.openai.model` if any entry has that `slug`; otherwise the first entry with `visibility == "list"` and a
     non-empty `slug`; otherwise the first entry with a non-empty `slug`. The slug is committed to
     `generation_run.model` (conditional on the run still being active) and used as `model` of every call of the run.
  2. **Every call**: `POST <base>/responses`, headers `Authorization: Bearer <access token>`, `Content-Type:
     application/json`, `Accept: text/event-stream`; body = the stage's existing body with `model = <run model>`,
     `store: false`, `stream: true` (both always present; `HttpResponsesClient` sets them, overriding any value of
     the builder). No `tools`/`tool_choice`/`web_search*` (phase-01 NFR-3 check stays), no field listed as
     unsupported by preview-limitations (`background`, `conversation`, `max_output_tokens`, `max_tool_calls`,
     `metadata`, `moderation`, `prompt`, `prompt_cache_retention`, `safety_identifier`, `temperature`,
     `top_logprobs`, `top_p`, `truncation`, `user`, `previous_response_id`) and no input item with role `system`.
  3. **Stream**: Server-Sent Events, `data: <json>` lines; each event's `type` decides:
     - `response.output_text.delta` → append `delta`
     - `response.completed` → success; output text = concatenated `output_text` parts of `response.output` message
       items, or the concatenated deltas when `response.output` has none
     - `response.incomplete` → CHATGPT_INCOMPLETE (no partial text is used)
     - `response.failed` → classify `response.error.code` (FR-39 table)
     - `error` → classify `code` (or `error.code`) (FR-39 table)
     - other types are ignored; the stream ends without `response.completed` / is interrupted / exceeds
       `stream-timeout` → CHATGPT_INCOMPLETE
     A `200` answer with `Content-Type: application/json` is read as the final response object: `status ==
     "completed"` → success as above, `"incomplete"` → CHATGPT_INCOMPLETE, `"failed"` → classify `error.code`.
  4. **Structured output fallback**: if a call whose body has `text.format` (json_schema) is answered with code
     `subscription_sharing_unsupported_capability` and `error.param` is absent or starts with `text`, the same call
     is sent once more without `text`, with this sentence appended to `instructions`: "Answer with exactly one JSON
     object and nothing else (no Markdown fence). It must validate against this JSON Schema: <the schema JSON of the
     removed text.format>". The answer text is trimmed, one surrounding ```` ``` ```` / ```` ```json ```` fence is
     removed, and it is passed to the stage's existing parser/validator (same rules as a structured answer). After
     such a fallback `structuredOutputSupported` is false and later calls are sent without `text.format` and with
     the instruction from the start.
- Rules:
  - The run's model is resolved once; regenerations, critic and story calls of the run use the same slug.
  - `ScenarioMetadata.model` (getFutureResult) = `generation_run.model`; shown as chip `meta-model` "Model <slug>".
  - `model_call` keeps storing request bodies (now containing `stream:true`, the run model), never headers.
  - Refresh before use: `requireUsableCredentials` refreshes when `accessTokenExpiresAt <= now + refresh-skew`
    (PT5M; boundary: exactly 5 min left → refresh). Refresh form: `grant_type=refresh_token`,
    `refresh_token=<held>`, `client_id=<credentials' issued client id>` (never `dynamic_agent_client`),
    `resource=https://api.openai.com/v1`. 2xx with `access_token` → replace access token, refresh token (the new one;
    if absent, keep the old), id token if returned, expiry (`expires_in`, default 3600) and scopes (if returned)
    together. Refreshes of one session are serialized (existing per-session lock).
  - Query expansion (`createText`) keeps its phase-01 rule: one attempt, never retried (also not for FR-39 row 4),
    any failure falls back to templates — except CHATGPT_SESSION_EXPIRED, CHATGPT_REGISTRATION_INVALID and
    CHATGPT_PLAN_NOT_ELIGIBLE, which fail the run at RESEARCH_STRATEGY with their FR-39 message. A `200` answer with
    `status` `incomplete` (or an incomplete stream) is a fallback there, not a run failure.
  - Implementation anchors the tests rely on (`HttpResponsesClient`): `String resolveModel(UUID sessionId)` performs
    step 1 (throws `ChatGptCallException` with the FR-39 code) and remembers the slug for the session (one active
    run per session); every `createText*` call of that session sends `model` = the remembered slug, overriding the
    builder's value (builders keep `(model, …)` and are still called with `responses.model()` = the preferred slug,
    so their unit tests stay 5-key bodies). Without a remembered slug the body's `model` is sent unchanged. The
    4-argument constructor `(auth, baseUrl, model, timeout)` keeps meaning `stream-timeout = timeout`,
    `retry-delay = 1 s`. `refreshAfterRejection` is removed (no caller). `resetStructuredOutput()` sets
    `structuredOutputSupported` back to true (tests that trigger the fallback call it in `@AfterEach`, because the
    Spring test context — and the flag — is shared across test classes).
  - The run task resolves the model before its first ChatGPT call; this is a new asynchronous token use right after
    the 202, so a test that counts refresh requests directly after `startRun` must account for it.
- Errors:
  - `GET /models` 401 → tokens cleared, flag SESSION_EXPIRED → run FAILED `CHATGPT_SESSION_EXPIRED` → "ChatGPT
    session expired — please reconnect"
  - `GET /models` 429 / 5xx / network / timeout / body not JSON → classified as in FR-39 (5xx etc. retried at most
    2 times) → e.g. `CHATGPT_UNAVAILABLE` → "ChatGPT is temporarily unavailable — try again in a few minutes"
  - `GET /models` 2xx with no entry carrying a slug (missing/empty `models`) → run FAILED `CHATGPT_NO_MODEL` →
    "ChatGPT offers no model for this account — check your plan, then try again"
  - stream ends with `response.incomplete` / `response.failed` without a known code / without `response.completed`
    → `CHATGPT_INCOMPLETE` → "ChatGPT did not finish the answer — please try again"; no story, no partial text
  - fallback answer is not valid JSON or fails the stage's validation → handled exactly like an invalid structured
    answer of that stage today (e.g. scenario: one correction retry, then `INVALID_SCENARIO` "ORACUL could not
    construct a valid scenario"; story: `INVALID_SCENARIO`; event classification: existing malformed-batch rule)
  - fallback itself answered with `subscription_sharing_unsupported_capability` → `CHATGPT_REQUEST_REJECTED` (FR-39)
- Changes earlier behaviour: sent Responses body has exactly the keys `model`, `instructions`, `input`, `text`, `store` and header `Accept: application/json` → the sent body has exactly `model`, `instructions`, `input`, `text`, `store`, `stream` (`stream` = true, `store` = false) and header `Accept: text/event-stream`; the builders' own bodies (prompt unit tests) keep their 5 keys because `HttpResponsesClient` adds `stream` (tests: backend/src/test/java/com/oracul/app/research/EventClassificationIT.java, backend/src/test/java/com/oracul/app/research/EventNormalizationIT.java, backend/src/test/java/com/oracul/app/research/ResearchPlanIT.java, backend/src/test/java/com/oracul/app/result/StoryWritingIT.java, backend/src/test/java/com/oracul/app/reasoning/ScenarioGenerationIT.java, backend/src/test/java/com/oracul/app/reasoning/CriticIT.java)
- Changes earlier behaviour: a `200` answer `{"status":"incomplete"}` of a stage call was a content problem (normalization: two such answers → deterministic grouping, run COMPLETED; classification: `CLASSIFICATION_FAILED` events, run COMPLETED, 2 requests; scenario: "incomplete" counted as invalid answer → `INVALID_SCENARIO` after 2 requests) → run FAILED `CHATGPT_INCOMPLETE` "ChatGPT did not finish the answer — please try again" at that stage after exactly 1 request, no correction retry, no partial text; query expansion keeps the template fallback (`ResearchPlanIT` "status incomplete" unchanged) (tests: backend/src/test/java/com/oracul/app/research/EventFailureIT.java, backend/src/test/java/com/oracul/app/research/EventNormalizationIT.java, backend/src/test/java/com/oracul/app/reasoning/StructuredScenarioIT.java)
- Changes earlier behaviour: no model catalogue call; `model` = `oracul.openai.model` → every run first calls `GET <base>/models` with the Bearer token; the in-process `StubResponses` must serve `GET /v1/models` (default `{"models":[{"slug":"stub-model","display_name":"Stub model","visibility":"list"}]}`, so recorded bodies keep `model` = `stub-model`), with its own reply hook (`modelsResponder`, default the catalogue above) and its own request list (`modelRequests`, cleared by `reset()`); `/v1/models` requests are **not** passed to `responder` and **not** added to `requests` / `exchanges` / the in-flight counters, so every existing Responses request count stays (tests: backend/src/test/java/com/oracul/app/research/StubResponses.java)
- Changes earlier behaviour: `oracul.chatgpt.refresh-skew` PT60S → PT5M; refresh form `grant_type`, `refresh_token`, `client_id` → plus `resource=https://api.openai.com/v1` (no test asserts the exact refresh form or a token lifetime between 60 s and 300 s) (tests: none)
- Changes earlier behaviour: `ScenarioMetadata` had no `model`; result view had no model chip → `metadata.model` = run model (absent for runs without one), chip `meta-model` "Model <slug>" after `meta-horizon` in the Settings chip set; `ScenarioMetadataMapper.map(cfg, counts)` keeps its signature, `FutureResultService` sets the model on the returned object (tests: none)
- Ranges & invariants: Catalogue classes (preferred `P` = `oracul.openai.model`; parameterized, result = slug sent in every call of the run and stored in `generation_run.model` / `metadata.model`): `[P(list)]` → P; `[A(list), P(hide)]` → P (preferred wins at any visibility); `[A(hide), B(list), C(list)]` → B; `[A(hide), B(hide)]` → A; `[{slug:""}, {slug:null}, B(hide)]` → B; `[]`, `{"models":null}`, `{}`, `[{slug:""}]` → `CHATGPT_NO_MODEL`, 0 Responses calls; slug lengths 1 and 128 accepted, 129 → treated as no slug. One `GET /models` per run (alternative runs too), before the first `POST /responses`; every `POST /responses` of a run (all stages, regenerations, corrections, retries, fallback) carries the same `model`, `store:false`, `stream:true`, header `Accept: text/event-stream`, never `tools`/`tool_choice`/`web_search*`/a preview-unsupported field/a `system` role item. SSE sequences (parameterized; result → stage outcome): `created, delta×n, completed(output text T)` → T; `completed` with empty `output` after deltas "a","b" → "ab"; `created, delta, incomplete` → INCOMPLETE; `created, failed(error.code X)` → FR-39 row of X; `failed` without code → INCOMPLETE; `error{code X}` → row of X; stream closed after deltas without `completed` → INCOMPLETE; unknown event types interleaved → ignored; `data:` lines split over several reads, `event:` lines, comments `:` and blank keep-alive lines → parsed correctly; stream longer than `stream-timeout` → INCOMPLETE; 200 `application/json` `status` completed / incomplete / failed → success / INCOMPLETE / row of `error.code`. Refresh boundary (injected `Clock`, skew 5 min): expiry − now = 5 min 1 s → no refresh; = 5 min → refresh; = 4 min 59 s, 0, −1 s → refresh; after a refresh both tokens are replaced together (a call never mixes the old access token with the new refresh token), and `refresh_token` absent in the answer → old refresh token kept. Fallback: first `subscription_sharing_unsupported_capability` on a `text.format` body → exactly 1 repeat without `text` and with the instruction sentence + schema appended; later calls (any stage, any session) go without `text` from the start (0 rejected round trips); fenced answers ```` ```json\n{…}\n``` ```` and ```` ```\n{…}\n``` ```` parse like bare JSON; `error.param` = `tools` → no fallback, row 5.

### FR-39 — Plain-language messages for documented OpenAI errors
- Happy path: every non-success of a Responses call or `GET /models` is classified by this table (first matching row
  wins; "code" = `error.code` of a JSON error body (string) / of a `response.failed` event's `response.error.code` /
  the `code` (or `error.code`) of an `error` event; a `{"detail":…}` body, a non-JSON body and a body whose `error`
  is a plain string (e.g. `{"error":"rate_limited"}`, `{"error":"PROVIDER-SECRET-BODY"}`) have no code). The run ends
  `FAILED` at its current stage with `failure {code, message[, providerCode]}`; the failure view shows it.
  | # | Condition | Retry | Side effect | RunFailureCode | Message |
  |---|---|---|---|---|---|
  | 1 | HTTP 401 (any code), or code `subscription_sharing_invalid_user`, `chatpass_v2_scope_not_authorized`, `chatpass_v2_invalid_authorization_context` | none | session credentials dropped, flag SESSION_EXPIRED | CHATGPT_SESSION_EXPIRED | ChatGPT session expired — please reconnect |
  | 2 | code `subscription_sharing_user_not_eligible` | none, no sign-in restart | none (connection state unchanged) | CHATGPT_PLAN_NOT_ELIGIBLE | Your ChatGPT plan is not eligible for ORACUL — a personal Plus or Pro plan is needed |
  | 3 | code `subscription_sharing_usage_limit_exceeded`, or HTTP 429 without code | none | none | CHATGPT_RATE_LIMITED | ChatGPT usage limit reached — try again later |
  | 4 | code `subscription_sharing_usage_unavailable` or `subscription_sharing_user_unavailable`; or no code and (HTTP 5xx, connection error, `timeout` exceeded) | up to 2 retries, waits `retry-delay`, then 2 × `retry-delay` | credentials kept | CHATGPT_UNAVAILABLE | ChatGPT is temporarily unavailable — try again in a few minutes |
  | 5 | code `subscription_sharing_unsupported_capability` (no FR-38 fallback applicable or fallback already used) or `subscription_sharing_route_not_supported` | none | none | CHATGPT_REQUEST_REJECTED (providerCode = the code) | ChatGPT rejected ORACUL's request — please report this |
  | 6 | any other code (sanitized) or, without code, any other non-2xx status (`http_<status>`) | none | none | CHATGPT_UNEXPECTED_ERROR (providerCode = the code) | ChatGPT returned an unexpected error (<code>) — please try again |
  | 7 | stream incomplete (FR-38) | none | none | CHATGPT_INCOMPLETE | ChatGPT did not finish the answer — please try again |
  A retried call that then succeeds continues the run normally; a retry runs `beforeSend` (run guard) first.
- Rules:
  - Messages never contain a stack trace, token, URL, provider body or request id; only rows 5/6 carry the sanitized
    code (`providerCode`, and in row 6 inside the message).
  - The stage-specific phase-01 handling stays where it decided on content (malformed JSON, guard, critic); the
    transport classes above replace the phase-01 transport handling of `createTextOrThrow`.
  - When the run view shows a failure with code CHATGPT_SESSION_EXPIRED or CHATGPT_REGISTRATION_INVALID, the app
    reloads the connection once (header turns "Session expired" / "Registration invalid").
- Errors (UI of the failure view `failure-view`; `failure-message` = `failure.message`; `try-again` stays):
  - CHATGPT_RATE_LIMITED → also link `failure-usage-link` "Open ChatGPT Settings → Usage",
    `href="https://chatgpt.com/#settings/Usage"`, `target="_blank"`, `rel="noopener noreferrer"`
  - CHATGPT_SESSION_EXPIRED → also `failure-reconnect` "Continue with ChatGPT" (`<a mat-flat-button
    href="/api/auth/chatgpt/authorize">`)
  - CHATGPT_REQUEST_REJECTED / CHATGPT_UNEXPECTED_ERROR → also `failure-provider-code` "Error code: <providerCode>"
  - CHATGPT_PLAN_NOT_ELIGIBLE, CHATGPT_UNAVAILABLE, CHATGPT_INCOMPLETE, CHATGPT_NO_MODEL → message only
  - a run failure without `failure` (defensive) → phase-01 behaviour ("Something went wrong — try again")
  - `RunFailures.message(code)` keeps its one-argument form for every code with a fixed text; the
    CHATGPT_UNEXPECTED_ERROR text is built with the sanitized provider code (`RunFailures.unexpected(providerCode)`
    → "ChatGPT returned an unexpected error (<providerCode>) — please try again").
- Changes earlier behaviour: CHATGPT_RATE_LIMITED message "ChatGPT plan limit reached — try again later" → "ChatGPT usage limit reached — try again later" (same code, still no retry; a 429 without code such as the stubs' `{"error":"rate_limited"}` / `{"error":"PROVIDER-SECRET-BODY"}` stays CHATGPT_RATE_LIMITED) (tests: backend/src/test/java/com/oracul/app/research/EventFailureIT.java, backend/src/test/java/com/oracul/app/reasoning/ScenarioGenerationIT.java, backend/src/test/java/com/oracul/app/result/StoryWritingIT.java, backend/src/test/java/com/oracul/app/reasoning/CriticIT.java, backend/src/test/java/com/oracul/app/runs/AlternativeRunIT.java, backend/src/test/java/com/oracul/app/runs/RunFailureHygieneIT.java, backend/src/test/java/com/oracul/app/runs/RunFailuresTest.java, e2e/tests/run-failures.spec.ts, e2e/tests/events.spec.ts)
- Changes earlier behaviour: 5xx / connection drop / `oracul.openai.timeout` on a stage call → 1 retry (2 requests) then CHATGPT_UNAVAILABLE "ChatGPT is unavailable right now — try again later" → 2 retries after `retry-delay` and 2 × `retry-delay` (3 requests, identical bodies, still one `model_call` row) then CHATGPT_UNAVAILABLE "ChatGPT is temporarily unavailable — try again in a few minutes"; a single 503 then success stays 2 requests; `EventRunGuardIT` #34 compares against the old text and must use the new one (tests: backend/src/test/java/com/oracul/app/research/EventFailureIT.java, backend/src/test/java/com/oracul/app/research/EventTimeoutIT.java, backend/src/test/java/com/oracul/app/research/EventRunGuardIT.java, backend/src/test/java/com/oracul/app/reasoning/ScenarioGenerationIT.java, backend/src/test/java/com/oracul/app/result/StoryWritingIT.java, backend/src/test/java/com/oracul/app/reasoning/CriticIT.java, backend/src/test/java/com/oracul/app/runs/RunFailuresTest.java)
- Changes earlier behaviour: Responses 401 or 403 → one forced token refresh and one retry with the new token (`aRejectedTokenIsRefreshedOnceAndTheCallRetried` for 401/403 in normalization and query expansion; `unrecoverableSessionFailsTheRunBeforeAnySearch` for 403) → 401 (any body): no refresh, no retry, credentials dropped, flag SESSION_EXPIRED, stage call fails CHATGPT_SESSION_EXPIRED (query expansion: run FAILED at RESEARCH_STRATEGY, 0 GDELT requests); 403 without a code (`{"error":"expired"}`, `{"error":"PROVIDER-SECRET-BODY"}`) → row 6 CHATGPT_UNEXPECTED_ERROR with providerCode `http_403` (query expansion: template fallback, run continues; normalization: run FAILED at CONNECTING_SIGNALS); 0 refresh requests in both cases; `ChatGptAuthService.refreshAfterRejection` and its unit test are removed (tests: backend/src/test/java/com/oracul/app/research/EventFailureIT.java, backend/src/test/java/com/oracul/app/research/ResearchPlanIT.java, backend/src/test/java/com/oracul/app/chatgpt/ChatGptAuthServiceTest.java)
- Changes earlier behaviour: other non-2xx Responses answers on stage calls (400, 404, 403 with a documented code) were CHATGPT_UNAVAILABLE after one retry → rows 2/5/6 without retry, `failure.providerCode` set for rows 5/6 (tests: none)
- Changes earlier behaviour: failure view showed only `failure-message` and `try-again`; `RunStore` reloaded the connection only for a FAILED run with CHATGPT_SESSION_EXPIRED and for `startRun`/`startAlternativeRun` errors CHATGPT_NOT_CONNECTED / CHATGPT_SESSION_EXPIRED / CHATGPT_PLAN_NOT_ELIGIBLE → extras `failure-usage-link` / `failure-reconnect` / `failure-provider-code` per code; the reload also happens for a FAILED run with CHATGPT_REGISTRATION_INVALID and for the error CHATGPT_REGISTRATION_INVALID; 503 CHATGPT_UNAVAILABLE shows its message and does not reload (existing specs pass their own message fixtures and stay valid) (tests: none)
- Ranges & invariants: Classification (parameterized over every row, for a stage call and for `GET /models`; result = failure code, message, providerCode, number of requests, connection state after): HTTP 401 with each body (`{}`, code `invalid_token`, code `subscription_sharing_user_not_eligible`) → row 1, 1 request, state SESSION_EXPIRED; codes `subscription_sharing_invalid_user`, `chatpass_v2_scope_not_authorized`, `chatpass_v2_invalid_authorization_context` with HTTP 400 and 403 → row 1; `subscription_sharing_user_not_eligible` (403) → row 2, 1 request, state unchanged CONNECTED, no authorize/token request; `subscription_sharing_usage_limit_exceeded` (429), 429 `{}` / `{"error":"rate_limited"}` / non-JSON → row 3, 1 request; `subscription_sharing_usage_unavailable` (503), `subscription_sharing_user_unavailable` (503), 500/502/503/504 without code, connection drop, header timeout → row 4, exactly 3 requests, waits ≥ `retry-delay` then ≥ 2 × `retry-delay` (injected delay, e.g. 100 ms / 200 ms), 503 once then success → 2 requests and normal completion, 503 twice then success → 3 requests and normal completion; `subscription_sharing_route_not_supported` (400/404) → row 5 providerCode = that code; `subscription_sharing_unsupported_capability` on a body without `text` → row 5; unknown code `weird_new_code` (400) → row 6 providerCode `weird_new_code`, message "ChatGPT returned an unexpected error (weird_new_code) — please try again"; sanitizing: code of 64 chars `[A-Za-z0-9_.:-]` kept, 65 chars / with space / with `<` / with `"` → `unknown_error`; 400/404/409/422 without code → `http_400` / `http_404` / `http_409` / `http_422`. Invariants for every class: `failure.message` is exactly the table text, never contains a token, `Bearer`, `http://`/`https://`, `{`, `}`, a stack-trace marker or the provider body; `providerCode` present iff code is CHATGPT_REQUEST_REJECTED / CHATGPT_UNEXPECTED_ERROR and matches `^[A-Za-z0-9_.:-]{1,64}$`; no retry except row 4; a retry is never sent after the run deadline or after `beforeSend` refused; rows 1–7 never trigger a refresh. UI (parameterized over every `RunFailureCode`): `failure-usage-link` present only for CHATGPT_RATE_LIMITED (text "Open ChatGPT Settings → Usage", `href` exactly `https://chatgpt.com/#settings/Usage`, `target="_blank"`, `rel="noopener noreferrer"`); `failure-reconnect` only for CHATGPT_SESSION_EXPIRED (text "Continue with ChatGPT", `href="/api/auth/chatgpt/authorize"`); `failure-provider-code` only for CHATGPT_REQUEST_REJECTED / CHATGPT_UNEXPECTED_ERROR with a providerCode (text "Error code: <providerCode>"); `try-again` and `failure-message` always; connection reloaded exactly once for CHATGPT_SESSION_EXPIRED and CHATGPT_REGISTRATION_INVALID and never for the other codes.

### FR-40 — Refresh failures end the session cleanly
- Happy path: a refresh (FR-38 rule) that answers 2xx with `access_token` replaces the token set; the call proceeds.
- Rules — refresh outcome classification (`error` string of the JSON body, or `error.code`):
  | Refresh answer | Effect | HTTP at `startRun`/`startAlternativeRun` | Run failure during a run |
  |---|---|---|---|
  | `invalid_grant`, `invalid_refresh_token`, `token_expired`, `refresh_token_expired`, `refresh_token_invalidated`, `refresh_token_reused`; also no refresh token held | drop credentials, flag SESSION_EXPIRED; host id and issued client id kept | `401 CHATGPT_SESSION_EXPIRED` "ChatGPT session expired — please reconnect" | CHATGPT_SESSION_EXPIRED |
  | `invalid_client` | drop credentials, flag REGISTRATION_INVALID; client id kept (only reset clears it) | `401 CHATGPT_REGISTRATION_INVALID` "ChatGPT registration is no longer valid — use Reset ChatGPT connection, then reconnect" | CHATGPT_REGISTRATION_INVALID (same message) |
  | other 4xx / unknown code / 2xx without `access_token` | drop credentials, flag SESSION_EXPIRED (phase-01 behaviour) | `401 CHATGPT_SESSION_EXPIRED` | CHATGPT_SESSION_EXPIRED |
  | 5xx, timeout, connection error | credentials kept (no flag) | `503 CHATGPT_UNAVAILABLE` "ChatGPT is temporarily unavailable — try again in a few minutes" | CHATGPT_UNAVAILABLE |
  - No run row is created when the check at `startRun` / `startAlternativeRun` fails (phase-01 check order: body →
    connection → active run).
  - `getChatGptConnection`: flag REGISTRATION_INVALID → `{state: REGISTRATION_INVALID, canGenerate: false}`.
  - After SESSION_EXPIRED, "Continue with ChatGPT" is a reauthorization with the stored issued client id (no
    `agent_name_hint`, same `ext_agent_host_id`) — chatgpt-connection.md FR-35.
- Errors:
  - `startRun` while flag REGISTRATION_INVALID → `401 CHATGPT_REGISTRATION_INVALID` → snackbar `run-error-message`
    with that message; generate button disabled (`canGenerate=false`)
  - `startRun` / `startAlternativeRun` with transient refresh failure → `503 CHATGPT_UNAVAILABLE` → snackbar
    "ChatGPT is temporarily unavailable — try again in a few minutes"; connection stays CONNECTED
  - a needed refresh during a run (before `GET /models` or any call) fails → run FAILED with the "Run failure during a
    run" column; a transient refresh failure counts as one row-4 attempt of that call (retried with the same
    backoff, at most 3 attempts in total); query expansion follows its own rule (FR-38).
- Changes earlier behaviour: any refresh failure (incl. 5xx, timeout, connection error) → credentials dropped, flag SESSION_EXPIRED, 401 CHATGPT_SESSION_EXPIRED → 5xx / timeout / connection error keep the credentials (no flag; state stays CONNECTED) and answer 503 CHATGPT_UNAVAILABLE at `startRun` / `startAlternativeRun` (run failure CHATGPT_UNAVAILABLE during a run); `invalid_client` → flag REGISTRATION_INVALID, 401 CHATGPT_REGISTRATION_INVALID, run failure CHATGPT_REGISTRATION_INVALID; the existing refresh tests use 400 `invalid_grant`, no refresh token, or 200 without `access_token` and keep their SESSION_EXPIRED result (tests: none)
- Changes earlier behaviour: E2E stub token endpoint `refresh_error` mode → 400 `invalid_grant`, sign-in tokens always `expires_in` 3600 (a refresh never happened in E2E) → modes `refresh_error`, `refresh_invalid_client` and `refresh_unavailable` issue the sign-in tokens with `expires_in` 60 (inside the 5-minute skew, so the next `startRun` refreshes) and answer the refresh with 400 `{"error":"invalid_grant"}` / 401 `{"error":"invalid_client"}` / 503 `{"error":"temporarily_unavailable"}`; no existing E2E selects `refresh_error` (tests: none)
- Ranges & invariants: Refresh answer classes (parameterized; result = HTTP at `startRun`, `startAlternativeRun` and run failure during a run, connection state, held credentials, registration row): 400 and 401 with `{"error":X}` and with `{"error":{"code":X}}` for X in `invalid_grant`, `invalid_refresh_token`, `token_expired`, `refresh_token_expired`, `refresh_token_invalidated`, `refresh_token_reused` → 401 CHATGPT_SESSION_EXPIRED / run CHATGPT_SESSION_EXPIRED, state SESSION_EXPIRED, credentials dropped, `host_id` and `client_id` unchanged; `invalid_client` (400 and 401) → 401 CHATGPT_REGISTRATION_INVALID / run CHATGPT_REGISTRATION_INVALID, state REGISTRATION_INVALID (`canGenerate` false), credentials dropped, `client_id` unchanged; other 4xx (`invalid_request`, `unknown_x`, `{}`), 200 without `access_token`, 200 non-JSON, no refresh token held → SESSION_EXPIRED as phase-01; 500, 502, 503, 504, timeout (> `http-timeout`), connection refused → 503 CHATGPT_UNAVAILABLE / run CHATGPT_UNAVAILABLE, state CONNECTED, credentials unchanged (a later successful refresh works with the same refresh token). Invariants: no run row is created by a failing `startRun` / `startAlternativeRun`; every refresh form is exactly `grant_type=refresh_token`, `refresh_token`, `client_id` (= the stored issued id, never `dynamic_agent_client`), `resource=https://api.openai.com/v1`; concurrent token uses of one session make at most one refresh request; after SESSION_EXPIRED the next authorize request is a reauthorization (stored issued client id, no `agent_name_hint`, same `ext_agent_host_id`); after REGISTRATION_INVALID only reset makes the next authorize a first registration.

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| POST | /api/runs | startRun | ScenarioConfiguration | 202 GenerationRun · 400 · 401 CHATGPT_NOT_CONNECTED / CHATGPT_SESSION_EXPIRED / **CHATGPT_REGISTRATION_INVALID** · 403 · 409 · **503 CHATGPT_UNAVAILABLE** · 500 |
| POST | /api/runs/{runId}/alternatives | startAlternativeRun | — | 202 · 401 (as above) · 403 · 404 · 409 · **503 CHATGPT_UNAVAILABLE** · 500 |
| GET | /api/runs/{runId} | getRun | — | 200 GenerationRun (`failure.code` adds CHATGPT_PLAN_NOT_ELIGIBLE, CHATGPT_REGISTRATION_INVALID, CHATGPT_INCOMPLETE, CHATGPT_REQUEST_REJECTED, CHATGPT_UNEXPECTED_ERROR, CHATGPT_NO_MODEL; optional `failure.providerCode`) |
| GET | /api/runs/{runId}/result | getFutureResult | — | 200 FutureResult (`metadata.model` optional) |
| GET | /api/auth/chatgpt/connection | getChatGptConnection | — | 200 (state adds REGISTRATION_INVALID) |

Outbound (OpenAI, not part of our contract): `GET <base>/models`, `POST <base>/responses`, `POST token-url`
(refresh).

## UI
- Route: `/futures/:runId` failure view (`RunFailureComponent`, `src/app/runs/run-failure.ts`; inputs gain `code` and
  `providerCode`); result metadata (`src/app/result/scenario-metadata.ts`). Material: `mat-button`, `mat-chip`.
- States: loading (progress view, unchanged) · error (failure view with the extras above) · success (result view with
  `meta-model`).
- `data-testid`s (new): `failure-usage-link`, `failure-reconnect`, `failure-provider-code`, `meta-model` (chip text
  "Model <slug>", absent when `metadata.model` is absent). Existing: `failure-view`, `failure-message`, `try-again`,
  `run-error-message`, `chatgpt-status`, `generate-button`.
- Placement: `failure-usage-link`, `failure-reconnect` and `failure-provider-code` sit between `failure-message` and
  `try-again` inside `failure-view`; `meta-model` is the last chip of the "Settings" chip set (after `meta-horizon`).
  `RunView` passes `[code]="run.failure?.code ?? null"` and `[providerCode]="run.failure?.providerCode ?? null"`.

## Slice 02_plan-usage-calls — test contract (FR-38, FR-39, FR-40)

### SSE wire format (what stubs send and the client must read)
`Content-Type: text/event-stream`, events separated by a blank line, each `event: <type>` + `data: <json>`:
```
event: response.created
data: {"type":"response.created","response":{"id":"resp_1","status":"in_progress"}}

event: response.output_text.delta
data: {"type":"response.output_text.delta","delta":"{\"queries\":"}

event: response.completed
data: {"type":"response.completed","response":{"id":"resp_1","status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"<full text>"}]}]}}
```
Incomplete: `{"type":"response.incomplete","response":{"id":"resp_1","status":"incomplete","incomplete_details":{"reason":"max_output_tokens"}}}`.
Failed: `{"type":"response.failed","response":{"id":"resp_1","status":"failed","error":{"code":"<code>","message":"…"}}}`.
Error: `{"type":"error","code":"<code>","message":"…"}`. The `type` field of `data` decides; `event:` lines are optional.

### In-process stubs (backend tests)
- `StubResponses`: `/v1/models` as described under FR-38 "Changes earlier behaviour"; a new reply helper
  `StubResponses.sse(String... dataJson)` (status 200, `text/event-stream`) and `StubResponses.error(int status,
  String code)` (`{"error":{"code":"<code>","message":"stub"}}`); the existing JSON replies (`completed(...)`) stay
  valid answers (decision 12), so existing scripts need no change.
- `StubOpenAi` (token endpoint) needs no change: refresh answers are scripted through `responder` as today.
- Delays: tests of row 4 set `oracul.openai.retry-delay` small (e.g. PT0.1S) and measure gaps ≥ 1× / 2×.

### Docker E2E stub (`e2e/stubs/server.mjs`, wired by the builders in this slice)
- `GET /v1/models` → 200 `{"models":[{"slug":"gpt-5","display_name":"GPT-5","visibility":"list"},{"slug":"gpt-5-mini","display_name":"GPT-5 mini","visibility":"list"}]}`
  (the backend's default preferred `gpt-5` is chosen → result chip "Model gpt-5"). Requires `Authorization: Bearer
  at-STUBSECRET-…`, else 401 `{"error":{"code":"invalid_token","message":"stub"}}`. Recorded under
  `GET /__control/requests?kind=models` as `{ bearer: true|false }` (never the token); `kind=responses` stays Responses
  bodies only, so existing counts (`recorded(page,'responses')`) do not change.
  `POST /__control/models` `{"mode":"ok"|"no-preferred"|"empty"|"unauthorized"|"unavailable"}`:
  `no-preferred` → `[{"slug":"stub-hidden","visibility":"hide"},{"slug":"stub-listed","visibility":"list"}]`
  (chosen `stub-listed`); `empty` → `{"models":[]}`; `unauthorized` → 401 as above; `unavailable` → 503
  `{"error":{"code":"subscription_sharing_usage_unavailable","message":"stub"}}`.
- `POST /v1/responses`: if the body lacks `stream: true` or `store: false` → 400
  `{"error":{"code":"invalid_request_error","message":"stream must be true and store must be false"}}` (the FR-42 test
  of this rule belongs to slice 03; the stub enforces it from this slice on). With both set, the existing purpose
  routing produces the output text and the stub answers SSE: `response.created`, the text in 3 `response.output_text.delta`
  chunks, `response.completed` with the full text in `output` (format above). The existing 429 modes
  (`events`/`critic`/`story` `rate-limited`) keep answering 429 `{"error":"rate_limited"}` (no code → row 3).
  `POST /__control/responses` `{"mode": …}` applies to every Responses call of every purpose (default `ok`, reset by
  `/__control/reset`):
  | mode | answer |
  |---|---|
  | `ok` | normal SSE |
  | `incomplete` | `created`, 1 delta, `response.incomplete` |
  | `failed` | `created`, then `response.failed` without `error.code` (→ CHATGPT_INCOMPLETE) |
  | `no-completed` | `created`, 1 delta, then the stream ends |
  | `not-eligible` | 403 `subscription_sharing_user_not_eligible` |
  | `usage-limit` | 429 `subscription_sharing_usage_limit_exceeded` |
  | `unavailable` | every call 503 `subscription_sharing_usage_unavailable` |
  | `unavailable-twice` | the first 2 calls after the mode was set 503 `subscription_sharing_usage_unavailable`, then `ok` |
  | `route-not-supported` | 400 `subscription_sharing_route_not_supported` |
  | `unsupported-capability` | bodies with `text.format` → 400 `subscription_sharing_unsupported_capability` with `error.param` `text.format`; bodies without `text` → `ok` (the purpose output, which the fallback must parse) |
  | `invalid-user` | 401 `subscription_sharing_invalid_user` |
  | `unknown-code` | 400 `weird_new_code` |
  Error bodies are `{"error":{"code":"<code>","message":"stub"}}`. Unknown mode → 400 `{"error":"unknown_mode"}`.
- Token endpoint modes for FR-40: see FR-40 "Changes earlier behaviour" (`refresh_error`, `refresh_invalid_client`,
  `refresh_unavailable`; `MODES` gains the two new names).
- Backend env in the E2E stack: unchanged for Responses (`ORACUL_OPENAI_RESPONSES_BASE_URL: http://stub:4010/v1`);
  add `ORACUL_OPENAI_RETRY_DELAY: PT0.2S` so row-4 E2E cases finish quickly, plus the FR-44 news settings
  (`news-search.md`).

### E2E scenarios this slice adds (`// @trace` per FR)
- FR-38: acceptance run COMPLETED; every recorded Responses body has `stream: true`, `store: false`, `model: "gpt-5"`;
  `kind=models` has exactly 1 record with `bearer: true` per run; result chip `meta-model` "Model gpt-5";
  `models` mode `no-preferred` → "Model stub-listed"; `empty` → failure view "ChatGPT offers no model for this
  account — check your plan, then try again"; `responses` mode `incomplete` → "ChatGPT did not finish the answer —
  please try again"; `unsupported-capability` → run COMPLETED.
- FR-39: `usage-limit` → failure message + `failure-usage-link`; `invalid-user` → "ChatGPT session expired —
  please reconnect" + `failure-reconnect`, header "Session expired"; `unknown-code` → "ChatGPT returned an
  unexpected error (weird_new_code) — please try again" + `failure-provider-code` "Error code: weird_new_code";
  `unavailable-twice` → COMPLETED; `not-eligible` → plan message, header still "ChatGPT connected".
- FR-40: mode `refresh_error` before connecting, then generate → snackbar `run-error-message` "ChatGPT session
  expired — please reconnect", header "Session expired"; `refresh_invalid_client` → snackbar "ChatGPT registration is
  no longer valid — use Reset ChatGPT connection, then reconnect", header "Registration invalid", generate disabled;
  `refresh_unavailable` → snackbar "ChatGPT is temporarily unavailable — try again in a few minutes", header stays
  "ChatGPT connected".
