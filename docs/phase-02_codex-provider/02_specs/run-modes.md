# Spec — Run modes (real vs. E2E stub), real-service checks and README

Covers: FR-42, FR-43

Also realises NFR-8 (real-service checks gate GREEN). Replaces phase-01 analyst decision 9 (stubs wired through the
auto-merged `docker-compose.override.yml`) and the "E2E stub service" part of phase-01 `chatgpt-connection.md`.

## Purpose
A plain `docker compose up -d` runs ORACUL against the real OpenAI and news services with its real database; the
OpenAI/news stub runs only when explicitly selected, with its own database volume, and enforces the documented request
shapes so stub-green tests cannot hide a malformed real request again. A README lets the user run, sign in, test,
troubleshoot and reset ORACUL without editing anything.

## Data
| Item | Value | Rules |
|---|---|---|
| `docker-compose.yml` | services `db`, `backend`, `frontend`; volume `db-data` | no stub service, no `ORACUL_*` URL overrides → backend defaults (`https://auth.openai.com…`, `https://api.openai.com/v1`, GDELT) |
| `docker-compose.override.yml` | — | **deleted** (it was auto-merged into every `docker compose up`) |
| `docker-compose.e2e.yml` | service `stub`; backend env overrides; `db` volume `db-e2e-data` | only used with `-f docker-compose.yml -f docker-compose.e2e.yml` or `COMPOSE_FILE` |
| volumes | `db-data` (real), `db-e2e-data` (E2E) | the E2E file maps `db-e2e-data:/var/lib/postgresql` for `db`; Compose merges service volumes by container path, so the real `db-data` mapping is replaced, not added |

### `docker-compose.e2e.yml` backend environment
| Variable | Value |
|---|---|
| `ORACUL_CHATGPT_AUTHORIZE_URL` | `http://localhost:4010/api/accounts/authorize` (browser-reachable) |
| `ORACUL_CHATGPT_TOKEN_URL` | `http://stub:4010/api/accounts/oauth/token` |
| `ORACUL_CHATGPT_REVOCATION_URL` | `http://stub:4010/api/accounts/oauth/revoke` |
| `ORACUL_CHATGPT_JWKS_URL` | `http://stub:4010/.well-known/jwks.json` |
| `ORACUL_OPENAI_RESPONSES_BASE_URL` | `http://stub:4010/v1` |
| `ORACUL_NEWS_GDELT_BASE_URL` | `http://stub:4010` |
| `ORACUL_RUN_PLACEHOLDER_STAGE_DELAY`, `ORACUL_RUN_MIN_STAGE_DURATION`, `ORACUL_EVIDENCE_MIN_CORE_MEDIUM`, `ORACUL_EVIDENCE_MIN_CORE_LOW` | unchanged values from the old override (`PT2S`, `PT2S`, `0`, `0`) |
`ORACUL_CHATGPT_REDIRECT_URI` is not overridden: E2E uses the real `http://127.0.0.1:4200/callback`. The stub mints ID
tokens with `iss = https://auth.openai.com`, so `oracul.chatgpt.issuer` keeps its default.

## Behaviour

### FR-42 — Opt-in E2E stub with its own data
- Happy path:
  - Real mode: `docker compose up -d` in `apps/oracul-engine` (no `-f`, no `COMPOSE_FILE`) → `docker compose ps`
    lists exactly `db`, `backend`, `frontend`; the backend uses the documented OpenAI endpoints; `db` mounts
    `db-data`.
  - E2E mode: `docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build` (equivalently
    `COMPOSE_FILE=docker-compose.yml:docker-compose.e2e.yml`) → also `stub` on port 4010; `db` mounts `db-e2e-data`.
  - Factory runs: `node factory-engine/bin/stack.mjs up|e2e|down` runs plain `docker compose …` and inherits the
    environment, so every E2E stack command is run with `COMPOSE_FILE=docker-compose.yml:docker-compose.e2e.yml`
    exported (see contract-notes "Factory note").
- Rules — stub (`e2e/stubs/server.mjs`, Node, no npm dependencies, port 4010) serves the documented paths and
  rejects malformed requests:
  | Stub endpoint | Accepts | Rejects with |
  |---|---|---|
  | `GET /api/accounts/authorize` | `response_type=code`; `client_id` = `dynamic_agent_client` **with** `agent_name_hint` (non-empty) or an `oaiapp_…` id **without** `agent_name_hint`; `ext_agent_host_id` matching `^urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`; `redirect_uri` matching `^http://127\.0\.0\.1:\d{1,5}/callback$`; `scope` containing all 6 scopes; `resource=https://api.openai.com/v1`; non-empty `state`, `nonce`, `code_challenge`; `code_challenge_method=S256` | `400 {"error":"invalid_authorize_request"}` (no redirect) for any violation (e.g. bare-UUID host id, path `/auth/callback`, `localhost`, missing nonce, `agent_name_hint` with an issued id); request recorded |
  | ↳ on success | 302 to `redirect_uri?code=stub-code-<n>&state=<state>` plus `&client_id=oaiapp_stub_client` for `dynamic_agent_client`; remembers per code: challenge, nonce, client id, redirect_uri | mode `deny` → `?error=access_denied&state=…`; mode `no_client_id` → no `client_id` on a first registration |
  | `POST /api/accounts/oauth/token` `authorization_code` | form `code` known and unused, `client_id` = the id issued for that code (never `dynamic_agent_client`), `code_verifier` matching the challenge, `redirect_uri` identical to the authorize one, `resource=https://api.openai.com/v1` | `400 {"error":"invalid_grant"}` (bad code/verifier/redirect, mode `invalid_grant`) · `400 {"error":"invalid_request"}` (missing/wrong `resource` or `client_id`) · mode `token_error` → 500 |
  | ↳ on success | `200 {access_token, refresh_token, id_token, token_type:"Bearer", expires_in:3600, scope}`; `id_token` RS256-signed with the stub key (`kid` `stub-key-1`), claims `iss=https://auth.openai.com`, `aud=<client id>`, `sub`, `email`, `nonce=<remembered nonce>`, `iat`, `exp=now+3600`; mode `not_eligible` drops `chatgpt.tokens.use.direct` from `scope`; mode `bad_nonce` signs a different nonce | — |
  | `POST /api/accounts/oauth/token` `refresh_token` | `client_id` = issued id, `refresh_token` issued and not yet used (rotation), `resource` | `400 {"error":"refresh_token_reused"}` for a used token · mode `refresh_error` → `400 {"error":"invalid_grant"}` · mode `refresh_invalid_client` → `401 {"error":"invalid_client"}` · missing `resource` → `400 invalid_request` |
  | `POST /api/accounts/oauth/revoke` | form `token`, `token_type_hint=refresh_token`, `client_id` | always empty `200`; request recorded |
  | `GET /.well-known/jwks.json` | — | `200 {"keys":[{kty:"RSA",kid:"stub-key-1",alg:"RS256",use:"sig",n,e}]}` (key generated at stub start) |
  | `GET /v1/models` | `Authorization: Bearer <issued, unrevoked access token>` | `401` otherwise; answers `{"models":[{"slug":"gpt-5","display_name":"GPT-5","visibility":"list"},{"slug":"stub-model-2","display_name":"Stub 2","visibility":"list"}]}` (control `POST /__control/models` `{"models":[…]}` replaces it until reset) |
  | `POST /v1/responses` | bearer as above; JSON body with `stream === true` **and** `store === false`; no input item with role `system`; `model` in the current catalogue | missing/foreign bearer → `401 {"error":{"code":"subscription_sharing_invalid_user"}}`; `stream`/`store` wrong or missing → `400 {"error":{"code":"invalid_request","param":"stream"\|"store"}}`; model not in catalogue → `400 {"error":{"code":"subscription_sharing_unsupported_capability","param":"model"}}` |
  | ↳ on success | `200 text/event-stream`: `response.created`, ≥ 1 `response.output_text.delta`, `response.completed` (with `response.output` message containing the same text); routing by `ORACUL REQUEST <PURPOSE>` as today | modes below |
  | `POST /__control/openai` | `{"mode": …}` → 204 | modes: `ok`, `not_eligible` (403 `subscription_sharing_user_not_eligible`), `usage_limit` (429 `subscription_sharing_usage_limit_exceeded`), `unavailable` (503 `subscription_sharing_usage_unavailable`), `unavailable_once` (first call 503, then ok), `unsupported_format` (400 `subscription_sharing_unsupported_capability` `param:"text.format"` for bodies with `text.format`, else ok), `route_not_supported` (403), `invalid_user` (401 `subscription_sharing_invalid_user`), `unknown_error` (400 code `stub_mystery_error`), `incomplete` (stream ends with `response.incomplete`), `failed_usage_limit` (stream ends with `response.failed`, code `subscription_sharing_usage_limit_exceeded`), `truncated` (stream ends without a terminal event) |
  | `POST /__control/mode` | existing control, modes `ok`, `not_eligible`, `deny`, `token_error`, `refresh_error`, plus `invalid_grant`, `no_client_id`, `bad_nonce`, `refresh_invalid_client` | `400 unknown_mode` |
  | `GET /__control/requests?kind=` | kinds `responses`, `gdelt`, plus `authorize`, `token`, `revoke`, `models` (each with the received query/form/body, rejected ones flagged `rejected: true`) | `400 unknown_kind` |
  Existing stub controls (`/__control/reset` — also resets the new modes and the catalogue —, `/__control/issued`,
  `news`, `events`, `scenario`, `critic`, `story`) and fixtures keep working; their 429 answers gain the code
  `subscription_sharing_usage_limit_exceeded`.
- Errors:
  - stub not running in E2E mode → backend calls fail → FR-39/FR-36 messages (no generic 500)
  - E2E run with plain `docker compose up` (no E2E file) → the real-mode stack starts; E2E fails fast at its first
    `__control` call (connection refused) — documented in the README under "Run the tests"
  - after any E2E run, the real-mode volume `db-data` contains no `oaiapp_stub_client` (it is never mounted in E2E
    mode)

### FR-43 — Comprehensive README
- Happy path: `apps/oracul-engine/README.md` with these `##` sections, in this order:
  1. `## Prerequisites` — personal ChatGPT Plus or Pro account; Docker Desktop (Compose v2); free ports 4200 and 8080;
     browser on the same computer
  2. `## Run for real` — `docker compose up -d --build`, open http://localhost:4200
  3. `## Sign in step by step` — click "Continue with ChatGPT"; sign in on OpenAI's page (Google login is fine); on the
     first time OpenAI shows "Connect your ORACUL to ChatGPT" (what it is: registering this ORACUL installation as an
     app allowed to use your ChatGPT plan; you may edit the name); confirm; you return to ORACUL with "ChatGPT
     connected"; click GENERATE THE FUTURE. Later sign-ins skip the connect screen. Tokens live only in memory:
     reconnect after a restart.
  4. `## Run the tests` — backend `cd backend && ./gradlew test`; frontend `cd frontend && npm ci && npm run test:ci`;
     E2E `docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build`, then
     `cd e2e && npm ci && npx playwright test`, then `docker compose -f docker-compose.yml -f docker-compose.e2e.yml
     down`; the E2E stack uses its own database
  5. `## Troubleshooting` — one entry each, each with "Cause" and "Fix": `invalid_authorize_request` on OpenAI's page;
     "Your ChatGPT plan is not eligible for ORACUL"; "ChatGPT usage limit reached"; port 4200 or 8080 already in use;
     Docker disk full; "ChatGPT registration is no longer valid" / resetting the registration (Reset ChatGPT
     connection in the header menu); resetting the database (`docker compose down -v`, note: deletes all stored
     futures)
  6. `## Stop and reset` — `docker compose down`; `docker compose down -v` (deletes futures and the registration);
     Reset ChatGPT connection in the UI
- Rules:
  - Every command is copy-paste runnable from `apps/oracul-engine` on a fresh clone with Docker running; no step edits a
    file, an env var, a database row or an API key; ORACUL never asks for an API key.
  - Commands use `docker compose` (v2), not `docker-compose`.
  - URLs: app http://localhost:4200, callback http://127.0.0.1:4200/callback (explained: OpenAI only accepts the
    loopback address; do not change it).
- Errors: none at runtime (documentation). A README check in the release QA walks every command on a fresh clone.

### NFR-8 — Real-service checks (planning hook)
- Check 1 after the sign-in slice, check 2 after the release (fresh install, empty registration): performed by the
  user on their real ChatGPT account; evidence files `docs/phase-02_codex-provider/04_build/<sign-in slice>/real-check.md`
  and `docs/phase-02_codex-provider/05_release/real-check.md` with: date, ORACUL commit/state, steps done, what OpenAI
  showed ("Connect your ORACUL to ChatGPT" or the reauthorization selector), ORACUL header text after return, run id
  and headline of one generated future (real text, not stub lorem), model slug shown in the metadata, result
  PASS/FAIL, and the user's confirmation. The phase is GREEN only after check 2 is PASS.

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| — | — | — | no API change (compose files, stub, README) | — |

## UI
- Route: none (no UI change). `data-testid`s used by E2E for these FRs are those of chatgpt-connection.md and
  chatgpt-inference.md.
- States: n/a.
