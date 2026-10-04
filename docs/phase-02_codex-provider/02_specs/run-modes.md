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

### Compose files and stack modes (slice 03 target state)
| Item | Value | Rules |
|---|---|---|
| `docker-compose.yml` | `name: oracul-engine`; services `db`, `backend`, `frontend`; volume `db-data` | unchanged; no `stub` service, no `ORACUL_*` variable → backend defaults (`https://auth.openai.com/...`, `https://api.openai.com/v1`, `https://news.google.com`, `https://api.gdeltproject.org`) |
| `docker-compose.override.yml` | — | **deleted** (Compose merged it into every plain `docker compose up`) |
| `docker-compose.e2e.yml` | new: the whole content of today's override file (service `stub`, every backend `ORACUL_*` override, `depends_on` incl. `stub`) **plus** `db.volumes: [db-e2e-data:/var/lib/postgresql]`, top-level `volumes: { db-e2e-data: {} }` and the two Google News variables below | used only with `-f docker-compose.yml -f docker-compose.e2e.yml` (or `COMPOSE_FILE=docker-compose.yml:docker-compose.e2e.yml`); Compose merges a service's `volumes` by container path, so `db-e2e-data` replaces `db-data` for `/var/lib/postgresql` (not added) |
| `.oracul/stack.json` | written in this spec step: mode `e2e` = files `docker-compose.yml`, `docker-compose.e2e.yml`; mode `run` = `docker-compose.yml`; urls frontend `http://localhost:4200`, health `http://localhost:8080/actuator/health`; no profiles; project = compose `name:` (`oracul-engine`) | `node factory-engine/bin/stack.mjs up/e2e` (default `--mode e2e`) starts db + backend + frontend + stub on `db-e2e-data`; `--mode run` starts the real stack; `down` covers both file sets (stub included). `check-stack` stays INVALID until `docker-compose.e2e.yml` exists (developer, GREEN) |
| volumes | `oracul-engine_db-data` (real), `oracul-engine_db-e2e-data` (E2E) | the real volume is never mounted in mode `e2e` |

### `docker-compose.e2e.yml` backend environment (copied from the override, plus FR-48)
| Variable | Value |
|---|---|
| `ORACUL_CHATGPT_AUTHORIZE_URL` | `http://localhost:4010/oauth/authorize` (browser-reachable) |
| `ORACUL_CHATGPT_TOKEN_URL` | `http://stub:4010/oauth/token` |
| `ORACUL_CHATGPT_JWKS_URL` | `http://stub:4010/jwks` |
| `ORACUL_CHATGPT_ISSUER` | `http://stub:4010` (the stub's `STUB_ISSUER` default) |
| `ORACUL_CHATGPT_REVOCATION_URL` | `http://stub:4010/oauth/revoke` |
| `ORACUL_OPENAI_RESPONSES_BASE_URL` | `http://stub:4010/v1` |
| `ORACUL_NEWS_GDELT_BASE_URL` | `http://stub:4010` |
| `ORACUL_NEWS_GOOGLE_BASE_URL` | `http://stub:4010` (new, FR-48) |
| `ORACUL_NEWS_GOOGLE_REQUEST_SPACING` | `PT0.2S` (new, FR-48) |
| `ORACUL_NEWS_REQUEST_SPACING`, `ORACUL_NEWS_RATE_LIMIT_WAIT` | `PT0.5S`, `PT0.5S` (unchanged) |
| `ORACUL_OPENAI_RETRY_DELAY` | `PT0.2S` (unchanged) |
| `ORACUL_RUN_PLACEHOLDER_STAGE_DELAY`, `ORACUL_RUN_MIN_STAGE_DURATION` | `PT2S`, `PT2S` (unchanged) |
| `ORACUL_EVIDENCE_MIN_CORE_MEDIUM`, `ORACUL_EVIDENCE_MIN_CORE_LOW` | `"0"`, `"0"` (unchanged) |
`ORACUL_CHATGPT_REDIRECT_URI` is not overridden: E2E uses the real `http://127.0.0.1:4200/callback`.

## Behaviour

### FR-42 — Opt-in E2E stub with its own data
- Happy path:
  - Real mode: `docker compose up -d` in `apps/oracul-engine` (no `-f`, no `COMPOSE_FILE`) → `docker compose ps`
    lists exactly `db`, `backend`, `frontend`; the backend uses the documented OpenAI / Google News / GDELT URLs;
    `db` mounts `db-data`.
  - E2E mode: `docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build` → also `stub` on port
    4010; `db` mounts `db-e2e-data`. The factory runs exactly this through `.oracul/stack.json` mode `e2e`
    (`stack.mjs up|e2e|down`); no `COMPOSE_FILE` export is needed any more (contract-notes "Stack modes").
  - `e2e/tests/global-setup.ts` keeps `docker compose restart backend` (restart re-uses the running container and
    its E2E environment; the project name comes from `name:` in `docker-compose.yml`).
- Rules — the stub (`e2e/stubs/server.mjs`, Node, no npm dependencies, port 4010) keeps every route, control and
  mode built in slices 01/02 and enforces the documented request shapes (already implemented, now proven by E2E):
  | Stub endpoint | Accepts | Rejects with |
  |---|---|---|
  | `GET /oauth/authorize` | `response_type=code`; `client_id` = `dynamic_agent_client` **with** non-empty `agent_name_hint`, or `^oaiapp_\w+$` **without** `agent_name_hint`; `ext_agent_host_id` matching `^urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`; `redirect_uri` matching `^http://127\.0\.0\.1:\d{1,5}/callback$`; `scope` containing all 6 scopes; `resource=https://api.openai.com/v1`; non-empty `state`, `nonce`, `code_challenge`; `code_challenge_method=S256` | HTTP 400 `{"error":"invalid_authorize_request"}`, no redirect, no code issued |
  | `POST /v1/responses` | JSON body with `stream === true` **and** `store === false` | HTTP 400 `{"error":{"code":"invalid_request_error","message":"stream must be true and store must be false"}}` (the body is still recorded under kind `responses`) |
  New in slice 03 (FR-48, see news-search.md): `GET /rss/search`, `GET /rss/articles/<name>`, `POST /__control/rss`,
  `GET /__control/requests?kind=rss`.
- Errors:
  - stub not running in E2E mode → backend calls fail → FR-39/FR-36 messages (no generic 500)
  - E2E run against the real-mode stack (plain `docker compose up`) → E2E fails fast at its first `__control` call
    (connection refused) — documented in the README under "Run the tests"
  - after any E2E run, the real-mode volume `db-data` contains no `oaiapp_stub_client` (it is never mounted in mode
    `e2e`)
- Changes earlier behaviour: `docker compose up -d` auto-merged `docker-compose.override.yml` (stub + stub URLs, shared volume `db-data`) and the factory needed `COMPOSE_FILE` → plain `up` is real mode (db, backend, frontend only, real URLs); the stub runs only via `docker-compose.e2e.yml` with volume `db-e2e-data`; the factory selects it through `.oracul/stack.json` mode `e2e` (tests: none)
- Ranges & invariants: none (the request-shape classes are checked by the E2E spec below in a loop; there is no unit/integration layer for the Node stub or compose files)

### FR-43 — Comprehensive README
- Happy path: `apps/oracul-engine/README.md` (new) starts with `# ORACUL` and has exactly these `##` sections, in this
  order (other `##` sections may follow after them):
  1. `## Prerequisites` — personal ChatGPT Plus or Pro account; Docker Desktop (Compose v2); free ports 4200 and 8080;
     browser on the same computer; for running the tests only: JDK 17+ (Gradle provisions Java 25) and Node.js 22+
  2. `## Run for real` — `docker compose up -d --build`, open http://localhost:4200; no `-f` flag and no
     `docker-compose.e2e.yml` in this section
  3. `## Sign in step by step` — click "Continue with ChatGPT"; sign in on OpenAI's page (Google login is fine); on the
     first time OpenAI shows "Connect your ORACUL to ChatGPT" (what it is: registering this ORACUL installation as an
     app allowed to use your ChatGPT plan; you may edit the name); confirm; you return to ORACUL with "ChatGPT
     connected"; click GENERATE THE FUTURE; STOP ends a slow generation. Later sign-ins skip the connect screen. Tokens
     live only in memory: reconnect after a restart.
  4. `## Run the tests` — backend `cd backend && ./gradlew test`; frontend `cd frontend && npm ci && npm run test:ci`;
     E2E `docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build`, then
     `cd e2e && npm ci && npx playwright install chromium && npx playwright test`, then
     `docker compose -f docker-compose.yml -f docker-compose.e2e.yml down`; states that the E2E stack uses its own
     database (`db-e2e-data`) and that E2E against the plain stack fails at the first stub call
  5. `## Troubleshooting` — one `###` entry each, each containing a line starting with `Cause:` and a line starting
     with `Fix:`: `invalid_authorize_request` on OpenAI's page; "Your ChatGPT plan is not eligible for ORACUL"; "ChatGPT
     usage limit reached"; port 4200 or 8080 already in use; Docker disk full; "ChatGPT registration is no longer
     valid" / resetting the registration (Reset ChatGPT connection in the header menu); resetting the database
     (`docker compose down -v`, deletes all stored futures)
  6. `## Stop and reset` — `docker compose down`; `docker compose down -v` (deletes futures and the registration);
     Reset ChatGPT connection in the UI
- Rules:
  - Every command is copy-paste runnable from `apps/oracul-engine` on a fresh clone with Docker running; no step edits a
    file, an env var, a database row or an API key; ORACUL never asks for an API key; the words "API key" appear only
    in a sentence saying none is needed.
  - Commands use `docker compose` (v2), never `docker-compose ` (with a space, the v1 binary).
  - URLs: app http://localhost:4200, callback http://127.0.0.1:4200/callback (explained: OpenAI only accepts the
    loopback address; do not change it).
- Errors: none at runtime (documentation). The release QA walks every command on a fresh clone.
- Changes earlier behaviour: none
- Ranges & invariants: none

### NFR-8 — Real-service checks (planning hook)
- Check 1 after the sign-in slice (done, `04_build/01_signin-fix/real-check.md`), check 2 after the release (fresh
  install, empty registration): performed by the user on their real ChatGPT account; evidence
  `docs/phase-02_codex-provider/05_release/real-check.md` with date, ORACUL commit, steps, what OpenAI showed, ORACUL
  header text after return, run id and headline of one generated future (real text, not stub lorem), model slug,
  PASS/FAIL and the user's confirmation. The phase is GREEN only after check 2 is PASS.

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| — | — | — | no API change (compose files, stack modes, stub, README) | — |

## UI
- Route: none (no UI change). `data-testid`s used by E2E for these FRs are those of chatgpt-connection.md,
  chatgpt-inference.md and run-control.md.
- States: n/a.

## Slice 03_run-modes-readme — FR-42 / FR-43 test contract
- `e2e/tests/run-modes.spec.ts` (`// @trace FR-42`), Node `fs` text checks of the app folder (`new URL('../../', import.meta.url)`),
  no YAML library:
  - `docker-compose.override.yml` does not exist; `docker-compose.yml` contains no `stub:` service, no `4010` and no
    `ORACUL_`; `docker-compose.e2e.yml` exists and contains `stub:`, `db-e2e-data:/var/lib/postgresql`, every
    variable of the table above; `.oracul/stack.json` parses with mode `e2e` files
    `["docker-compose.yml","docker-compose.e2e.yml"]` and mode `run` files `["docker-compose.yml"]`.
  - live stub: in a loop over the reject classes, each alone with all other parameters valid, `GET
    http://localhost:4010/oauth/authorize?…` answers 400 `{"error":"invalid_authorize_request"}` and no `Location`:
    bare UUID host id, host id `urn:uuid:` + upper-case hex, redirect path `/auth/callback`, redirect host
    `localhost`, missing `nonce`, `client_id=oaiapp_x` with `agent_name_hint`, `dynamic_agent_client` without
    `agent_name_hint`, missing `resource`, `code_challenge_method=plain`; the fully valid request answers 302 to
    `http://127.0.0.1:4200/callback?code=…`.
  - live stub: `POST http://localhost:4010/v1/responses` without `stream`, with `stream:false`, without `store`, with
    `store:true` → each 400.
  - real-mode isolation: the stack under test has the backend wired to the stub (a sign-in through the UI reaches
    `oaiapp_stub_client`), which together with the file checks proves the stub client id is only ever written to
    `db-e2e-data`.
- `e2e/tests/readme.spec.ts` (`// @trace FR-43`), Node `fs` text checks of `README.md`: the six `##` headings in the
  order above (first six `##` lines); the Troubleshooting section has the seven entries, each with `Cause:` and `Fix:`;
  the README contains `docker compose up -d --build`, `docker compose down -v`, `./gradlew test`, `npm run test:ci`,
  `-f docker-compose.yml -f docker-compose.e2e.yml`, `http://127.0.0.1:4200/callback`, "Connect your ORACUL to
  ChatGPT"; it never contains `docker-compose ` (v1 with a space), `.env`, `export ` or `psql`; "Run for real" contains
  no `-f`.
