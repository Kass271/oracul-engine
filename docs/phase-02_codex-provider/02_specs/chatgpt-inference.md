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
  - Query expansion (`createText`) keeps its phase-01 rule: any failure falls back to templates, except
    CHATGPT_SESSION_EXPIRED and CHATGPT_REGISTRATION_INVALID, which fail the run.
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

### FR-39 — Plain-language messages for documented OpenAI errors
- Happy path: every non-success of a Responses call or `GET /models` is classified by this table (first matching row
  wins; "code" = `error.code` of a JSON error body / failed event; a `{"detail":…}` body has no code). The run ends
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
  `run-error-message`.
