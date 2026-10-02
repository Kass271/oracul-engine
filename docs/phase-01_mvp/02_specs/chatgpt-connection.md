# Spec — ChatGPT connection and credential lifecycle

Covers: FR-7, FR-8, FR-9

## Purpose
Connect ORACUL to the user's ChatGPT plan through OpenAI's official open-source "Sign in with ChatGPT" flow
(OAuth 2.0 authorization code + PKCE S256, self-serve dynamic client registration, loopback redirect on 127.0.0.1),
show the connection state in the header, let the user disconnect, and guarantee that no credential ever leaves backend
memory. ORACUL never asks for a password or API key.

## Data

### Persisted (PostgreSQL, Flyway `V2__browser_session_and_chatgpt_registration.sql`) — non-secret only
| Entity | Field | Type | Rules |
|---|---|---|---|
| browser_session | id | uuid | PK; value of the `ORACUL_SID` cookie (random UUID v4) |
| browser_session | created_at / last_seen_at | timestamptz NOT NULL | last_seen_at updated at most once per minute |
| chatgpt_client_registration | id | bigint identity | PK; at most one row per installation |
| chatgpt_client_registration | host_id | uuid NOT NULL | `ext_agent_host_id`; generated on first need (first `startChatGptSignIn`), then stable |
| chatgpt_client_registration | client_id | varchar(256) NULL | issued `oaiapp_…` id, written after the first successful token exchange of a dynamic registration; null until then; never overwritten once set |
| chatgpt_client_registration | created_at / updated_at | timestamptz NOT NULL | |

No table has a column for access token, refresh token, id token, authorization code, code verifier, state, cookie or
authorization header (FR-9, NFR-1). Forbidden column-name pattern (tests check `information_schema.columns` of schema
`public`): `(?i)(access|refresh|id)_?token|auth(orization)?_?code|verifier|password|api_?key|secret|bearer`.

### Runtime only (backend process memory) — `com.oracul.app.chatgpt.ChatGptCredentialStore`
| Entity | Field | Type | Rules |
|---|---|---|---|
| PendingAuthorization | state | Secret | 32 random bytes (SecureRandom) base64url without padding (43 chars); key of the map; single use |
| PendingAuthorization | codeVerifier | Secret | 64 random bytes base64url without padding (86 chars, RFC 7636 charset) |
| PendingAuthorization | sessionId | uuid | session that started the flow |
| PendingAuthorization | clientId | String | client_id sent in the authorize URL (`oaiapp_…` or `dynamic_agent_client`) |
| PendingAuthorization | dynamicRegistration | boolean | true when no client_id was persisted at start |
| PendingAuthorization | createdAt | Instant | from the injected `java.time.Clock`; expired when `now >= createdAt + pending-ttl` |
| SessionCredentials | sessionId | uuid | map key |
| SessionCredentials | clientId | String | client_id used for the code exchange; reused for refresh |
| SessionCredentials | accessToken / refreshToken / idToken | Secret | refresh/id token may be absent; never serialised, `toString()` = `[REDACTED]` |
| SessionCredentials | accessTokenExpiresAt | Instant | `now + expires_in` s (`expires_in` absent → 3600 s) |
| SessionCredentials | grantedScopes | Set<String> | split of the token response `scope` on spaces |
| SessionConnectionFlag | state | PLAN_NOT_ELIGIBLE / SESSION_EXPIRED | memory only; cleared by disconnect or by the next successful token exchange |

`Secret` (`com.oracul.app.chatgpt.Secret`) is a value holder whose `toString()` returns `[REDACTED]` and whose Jackson
serialisation throws (`JsonSerializer` that throws `JsonMappingException`) so it can never reach a response body.
`ChatGptCredentialStore` exposes `clearAll()` (used only to simulate a restart in tests; production restart empties it
naturally).

### Configuration (`com.oracul.app.chatgpt.ChatGptProperties`, prefix `oracul.chatgpt`; tests point URLs at stub servers — NFR-7)
| Property | Default |
|---|---|
| `oracul.chatgpt.authorize-url` | `https://auth.openai.com/api/accounts/authorize` |
| `oracul.chatgpt.token-url` | `https://auth.openai.com/api/accounts/oauth/token` |
| `oracul.chatgpt.redirect-uri` | `http://127.0.0.1:4200/auth/callback` |
| `oracul.chatgpt.scopes` | `openid profile email offline_access resource.invoke chatgpt.tokens.use.direct` |
| `oracul.chatgpt.resource` | `https://api.openai.com/v1` |
| `oracul.chatgpt.dynamic-client-id` | `dynamic_agent_client` |
| `oracul.chatgpt.agent-name-hint` | `ORACUL` |
| `oracul.chatgpt.required-scope` | `chatgpt.tokens.use.direct` |
| `oracul.chatgpt.pending-ttl` | `PT10M` (`PT0S` makes every pending entry expired at once — used by tests) |
| `oracul.chatgpt.refresh-skew` | `PT60S` (refresh when the access token expires within this window) |
| `oracul.chatgpt.http-timeout` | `PT10S` (connect + read timeout of token and refresh requests) |
| `oracul.frontend-base-url` | `http://localhost:4200` (target of the post-callback redirect; a trailing `/` is ignored) |
| `oracul.openai.responses-base-url` | `https://api.openai.com/v1` (used by scenario-reasoning.md, not in this slice) |

Startup validation (NFR-6): `redirect-uri` must parse as an absolute `http` URI with host exactly `127.0.0.1`, an
explicit port and path `/auth/callback`; otherwise the application context fails to start with
`IllegalStateException("oracul.chatgpt.redirect-uri must be http://127.0.0.1:<port>/auth/callback")`. The check is the
static method `ChatGptProperties.requireLoopbackRedirect(String redirectUri)` (returns normally when valid), called on
binding. Examples: `http://localhost:4200/auth/callback` ✗, `https://127.0.0.1:4200/auth/callback` ✗,
`http://127.0.0.1/auth/callback` ✗ (no port), `http://127.0.0.1:1455/auth/callback` ✓.

## Browser session (`com.oracul.app.session`)
- `SessionFilter` (servlet filter, runs for every `/api/**` request except `/api/auth/chatgpt/callback`): reads cookie
  `ORACUL_SID`. Missing, not a UUID, or no `browser_session` row with that id → create a new row with a new random
  UUID and answer `Set-Cookie: ORACUL_SID=<uuid>; Path=/; HttpOnly; SameSite=Lax` (no `Max-Age`/`Expires`, no
  `Secure`). Known id → no `Set-Cookie`; `last_seen_at` refreshed if older than 1 minute. The session id is available
  to controllers through `CurrentSession.id()` (request-scoped).
- The callback path is excluded: it arrives on host `127.0.0.1` (different cookie jar than `localhost`), creates no
  session and sets no cookie; it resolves the session from the pending entry.

## Credential lifecycle
1. **Start** (`GET /api/auth/chatgpt/authorize`, browser navigation): ensure the `chatgpt_client_registration` row
   exists (create with random `host_id` if absent). Create a PendingAuthorization for the current session. Build the
   authorize URL = `authorize-url` + `?` + these query parameters (any order, RFC 3986 percent-encoding, spaces as `%20`):
   | Parameter | Value |
   |---|---|
   | `response_type` | `code` |
   | `client_id` | persisted `oaiapp_…` id, else `dynamic_agent_client` |
   | `ext_agent_host_id` | `host_id` — only when `client_id=dynamic_agent_client` |
   | `agent_name_hint` | `ORACUL` — only when `client_id=dynamic_agent_client` |
   | `redirect_uri` | `redirect-uri` |
   | `scope` | the 6 scopes, space-separated, in the configured order |
   | `state` | pending state |
   | `code_challenge` | BASE64URL-no-padding(SHA-256(ASCII(code_verifier))) (43 chars) |
   | `code_challenge_method` | `S256` |
   | `resource` | `resource` |
   Answer `302` with `Location: <authorize URL>` and no body.
2. **Callback** (`GET /auth/callback` on 127.0.0.1 → forwarded to `GET /api/auth/chatgpt/callback`), evaluated in
   this order — the first matching rule decides:
   1. any parameter longer than its limit (`code` 4096, `state` 512, `error` 256, `error_description` 2048,
      `client_id` 256) → `not_completed`, nothing removed, no token request
   2. `state` missing or not in the store, or its entry expired (expired entries are removed) → `not_completed`, no
      token request
   3. remove the pending entry (single use from here on)
   4. `error` present (any value, e.g. `access_denied`) → `not_completed`, no token request
   5. `code` missing or empty → `not_completed`, no token request
   6. exchange client id = the callback `client_id` if the pending entry is a dynamic registration and the value
      matches `^oaiapp_[A-Za-z0-9_-]{1,249}$`; otherwise the pending entry's `clientId`
   7. token request: `POST token-url`, `Content-Type: application/x-www-form-urlencoded`, `Accept: application/json`,
      form `grant_type=authorization_code`, `code`, `redirect_uri`, `client_id`, `code_verifier`. Code and verifier are
      discarded after this request (success or failure).
   8. non-2xx, timeout (`http-timeout`), connection error, body not JSON, or no non-empty `access_token` →
      `not_completed`; nothing stored; the session's previous credentials/flag are left unchanged
   9. success and dynamic registration with an `oaiapp_…` exchange client id and no persisted `client_id` → persist it
   10. **Eligibility**: `scope` present and lacks `required-scope` → drop all credentials of the session, set flag
       PLAN_NOT_ELIGIBLE → `not_eligible`. `scope` absent → granted = requested (RFC 6749 §5.1).
   11. otherwise replace the session's SessionCredentials, clear its flag → `connected`
   Every outcome answers `302` with `Location: <frontend-base-url>/?chatgpt=<outcome>` (exactly this URL, e.g.
   `http://localhost:4200/?chatgpt=connected`), no body, never 4xx/5xx, never echoing `code`, `state`, `error` or
   `error_description`. Any unexpected exception in the callback → `not_completed`.
3. **Use / refresh** (`ChatGptAuthService.requireUsableCredentials(sessionId)`, called by `startRun` in this slice and
   before every ChatGPT call in later slices), in this order:
   - flag PLAN_NOT_ELIGIBLE → `ApiException(403, CHATGPT_PLAN_NOT_ELIGIBLE)`
   - flag SESSION_EXPIRED → `ApiException(401, CHATGPT_SESSION_EXPIRED)`
   - no SessionCredentials → `ApiException(401, CHATGPT_NOT_CONNECTED)`
   - `accessTokenExpiresAt <= now + refresh-skew` → refresh: `POST token-url` form `grant_type=refresh_token`,
     `refresh_token`, `client_id` (SessionCredentials.clientId). 2xx with non-empty `access_token` → replace access
     token, expiry, and refresh token if a new one is returned (rotation). No refresh token held, non-2xx, timeout,
     connection error, or no `access_token` → drop credentials, set flag SESSION_EXPIRED,
     `ApiException(401, CHATGPT_SESSION_EXPIRED)`.
   - otherwise return the credentials.
4. **End**: `DELETE /api/auth/chatgpt/connection` removes SessionCredentials, every PendingAuthorization and the flag
   of the session → `204`, no body; idempotent. Backend restart loses all memory → every session is NOT_CONNECTED;
   `browser_session` and `chatgpt_client_registration` rows survive.

Connection state (`getChatGptConnection`, never calls OpenAI, never refreshes): flag PLAN_NOT_ELIGIBLE →
`PLAN_NOT_ELIGIBLE`; flag SESSION_EXPIRED → `SESSION_EXPIRED`; SessionCredentials present (even if the access token is
past expiry) → `CONNECTED`; otherwise `NOT_CONNECTED`. `canGenerate` = (`state == CONNECTED`).

## Behaviour

### FR-7 — Continue with ChatGPT (sign-in)
- Happy path: header link "Continue with ChatGPT" (`chatgpt-connect`, `href="/api/auth/chatgpt/authorize"`) →
  302 to OpenAI → user consents → OpenAI redirects to
  `http://127.0.0.1:<port>/auth/callback?code=…&state=…[&client_id=oaiapp_…]` → 302 to
  `<frontend-base-url>/?chatgpt=connected` → the app shows snackbar "ChatGPT connected" (`chatgpt-message`), removes
  the `chatgpt` query parameter from the URL (replaceUrl; the URL becomes `/`), reloads the connection, and the
  header shows "ChatGPT connected" (`chatgpt-status`) with "Disconnect" (`chatgpt-disconnect`).
- Rules:
  - A fresh state and verifier for every start: two starts give two different `state` and `code_challenge` values;
    both stay valid until used or expired.
  - The authorize URL always contains `response_type=code`, `code_challenge_method=S256`, all 6 scopes and a
    `redirect_uri` whose host is `127.0.0.1`.
  - First sign-in (no persisted client_id): `client_id=dynamic_agent_client` + `ext_agent_host_id` + `agent_name_hint`;
    after a successful callback carrying `client_id=oaiapp_X`, the next start uses `client_id=oaiapp_X` and omits
    `ext_agent_host_id`/`agent_name_hint`; `host_id` never changes between starts.
  - No ORACUL screen contains a password or API-key input: `input[type=password]` count is 0 in every state.
- Errors (all `302` to `<frontend-base-url>/?chatgpt=not_completed`; the app shows snackbar "ChatGPT connection was
  not completed" and the header stays "Not connected" / "Continue with ChatGPT"):
  - `state` missing / unknown / already used / expired (pending-ttl) → no token request is made
  - `error=access_denied` (or any `error`) with a known state → pending entry removed, no token request (the state can
    not be reused afterwards)
  - known state, `code` missing → pending entry removed, no token request
  - token endpoint answers non-2xx, times out, is unreachable or returns no `access_token` → nothing stored
  - a parameter over its length limit → no token request
  - `startChatGptSignIn` when the authorize URL cannot be built (e.g. `oracul.chatgpt.authorize-url=http://[bad`) →
    `302` to `<frontend-base-url>/?chatgpt=not_completed`, never a 500 page
  - the callback never answers 4xx/5xx and its `Location` never contains `code`, `state`, `error` or
    `error_description` values

### FR-8 — ChatGPT connection status and sign-out
- Happy path: `GET /api/auth/chatgpt/connection` → `200 {"state": "...", "canGenerate": bool}` (exactly these two
  properties); the header renders:
  | state | `chatgpt-status` text | action shown |
  |---|---|---|
  | (loading) | "Checking…" | none |
  | NOT_CONNECTED | "Not connected" | `chatgpt-connect` "Continue with ChatGPT" |
  | CONNECTED | "ChatGPT connected" | `chatgpt-disconnect` "Disconnect" |
  | PLAN_NOT_ELIGIBLE | "Plan not eligible" | `chatgpt-connect` "Continue with ChatGPT" |
  | SESSION_EXPIRED | "Session expired" | `chatgpt-connect` "Continue with ChatGPT" |
  The app loads the state on page load, after the OAuth return, after Disconnect and (from slice 04 on) whenever an
  API call answers `CHATGPT_SESSION_EXPIRED` / `CHATGPT_NOT_CONNECTED` / `CHATGPT_PLAN_NOT_ELIGIBLE`.
- Disconnect: click `chatgpt-disconnect` → `DELETE /api/auth/chatgpt/connection` → `204` → the store reloads the
  connection → header "Not connected" + "Continue with ChatGPT"; a subsequent `GET /api/auth/chatgpt/connection` →
  `NOT_CONNECTED`; `startRun` with a valid body → `401 CHATGPT_NOT_CONNECTED`. The button is disabled while the DELETE
  is in flight. Disconnect while a run is active: the run fails at its next ChatGPT call with `CHATGPT_SESSION_EXPIRED`
  (generation-runs.md, slice 04+).
- Rules: `canGenerate` is true only for CONNECTED; the generate button stays disabled otherwise (generation-runs.md).
  Session isolation: credentials of session A are never visible to session B (B's GET answers NOT_CONNECTED).
- `startRun` connection check (delivered in this slice; check order body validation → connection, generation-runs.md
  FR-10). Errors use the shared responses `ChatGptRequired` / `PlanNotEligible`; nothing is stored:
  - not connected (no credentials, no flag) → `401 CHATGPT_NOT_CONNECTED` → "Connect ChatGPT to generate"
  - flag PLAN_NOT_ELIGIBLE → `403 CHATGPT_PLAN_NOT_ELIGIBLE` → "Your ChatGPT plan is not eligible for ORACUL"
  - flag SESSION_EXPIRED, or access token within refresh-skew and refresh fails → `401 CHATGPT_SESSION_EXPIRED` →
    "ChatGPT session expired — please reconnect"; afterwards GET connection → `SESSION_EXPIRED`
  - access token within refresh-skew and refresh succeeds → token request with `grant_type=refresh_token` was made and
    GET connection stays `CONNECTED`. Interim until slice 04: a valid body with a usable connection answers
    `501` with no body — not asserted by any slice-02 test; slice 04 replaces it with `202 GenerationRun`.
- Errors:
  - granted scopes lack `chatgpt.tokens.use.direct` → callback `302 ?chatgpt=not_eligible` → snackbar
    "Your ChatGPT plan is not eligible for ORACUL"; header "Plan not eligible"; `canGenerate=false`; `startRun` →
    `403 CHATGPT_PLAN_NOT_ELIGIBLE`
  - backend restarted (memory empty) → GET connection `NOT_CONNECTED` for the same `ORACUL_SID` → header "Not
    connected" + "Continue with ChatGPT"
  - access token expired and refresh fails at `startRun` → `401 CHATGPT_SESSION_EXPIRED` → "ChatGPT session expired —
    please reconnect"; no run row is created; state becomes SESSION_EXPIRED (UI message shown by the generate flow in
    slice 04)
  - `GET /api/auth/chatgpt/connection` fails (500 `INTERNAL_ERROR`, network error, backend down) → header "Not
    connected" + "Continue with ChatGPT", `canGenerate=false`, no snackbar
  - `DELETE /api/auth/chatgpt/connection` fails (500 `INTERNAL_ERROR` or network) → snackbar
    "Something went wrong — try again"; the header keeps its previous state

### FR-9 — Runtime-only credential handling
- Happy path: tokens, codes, verifiers and states exist only in `ChatGptCredentialStore` (ConcurrentHashMaps) for the
  minimum lifetime listed above; only `browser_session` and `chatgpt_client_registration` are persisted.
- Rules:
  - `com.oracul.app.common.logging.SecretRedactor.redact(String) : String` (null → null) applies, in this order:
    1. `(?i)bearer\s+[^\s"',;]+` → `Bearer [REDACTED]`
    2. `(?<![A-Za-z0-9_])(access_token|refresh_token|id_token|code|code_verifier|state)=[^&\s"',;]+` → `$1=[REDACTED]`
    3. `"(access_token|refresh_token|id_token|code_verifier|code|state)"\s*:\s*"[^"]*"` → `"$1":"[REDACTED]"`
    Examples: `Authorization: Bearer abc123secret` → `Authorization: Bearer [REDACTED]`;
    `code=xyz&state=abc&client_id=oaiapp_1` → `code=[REDACTED]&state=[REDACTED]&client_id=oaiapp_1`;
    `error_code=5` unchanged; `{"access_token": "t1"}` → `{"access_token":"[REDACTED]"}`.
  - Logback (`logback-spring.xml`) renders every log event's message and throwable text through `SecretRedactor`
    (custom converters replacing `%m` and the exception converter in console output). Observed by tests via Spring
    Boot `OutputCaptureExtension`: e.g. `LoggerFactory.getLogger("any").info("Authorization: Bearer abc123secret")`
    prints `Bearer [REDACTED]` and never `abc123secret`; the same holds for an exception message logged with a stack
    trace.
  - `com.oracul.app.common.RequestLogFilter` logs one INFO line per `/api/**` request after completion:
    `api request method=<M> path=<path> query=<raw query or -> status=<code> authorization=<raw header or ->` — so a
    request with `Authorization: Bearer abc123secret` produces `authorization=Bearer [REDACTED]`, and a callback
    produces `query=code=[REDACTED]&state=[REDACTED]…`. The `Authorization` header is otherwise ignored.
  - HTTP clients to OpenAI never log request/response bodies or headers; failures log only
    `chatgpt token request failed: status=<code>` or `chatgpt token request failed: <ExceptionClassSimpleName>`.
  - Error mapping (`ApiExceptionHandler`) never copies exception messages of HTTP-client or provider exceptions into
    `ApiError.message`; it uses the fixed messages of the specs. Unhandled exceptions → `500 {"code":"INTERNAL_ERROR",
    "message":"Something went wrong — try again"}`.
  - No response body (JSON or redirect `Location`) ever contains a token, code, verifier or state value — the only
    place a state appears is the authorize `Location` sent to OpenAI.
  - Frontend: nothing is written to localStorage, sessionStorage or IndexedDB (no `localStorage`/`sessionStorage`/
    `indexedDB` use anywhere in `src/app`); connection state lives in the `ConnectionStore` signal only.
  - `management.endpoints.web.exposure.include=health` only; `server.error.include-message`,
    `include-stacktrace`, `include-exception`, `include-binding-errors` = `never`/`false`.
- Errors:
  - any exception during sign-in or a ChatGPT call → the logged line contains "Bearer [REDACTED]" (when a bearer value
    was part of the text) and no token value; the user sees the fixed message of the failing flow (FR-7 / FR-32),
    never a provider body.
  - incoming `Authorization: Bearer abc123secret` on any `/api/**` request → ignored for authentication, logged only as
    `Bearer [REDACTED]`; the response body does not contain `abc123secret`.

## Test hooks (NFR-1, NFR-7)
- Backend tests: an in-process stub HTTP server (e.g. JDK `com.sun.net.httpserver.HttpServer` on a random port) serves
  `POST /oauth/token`; tests set `oracul.chatgpt.token-url` (and a dummy `authorize-url`) via
  `@DynamicPropertySource`. The flow is driven with MockMvc: `GET /api/auth/chatgpt/authorize` (cookie `ORACUL_SID`
  taken from the first response) → parse `state` from `Location` → `GET /api/auth/chatgpt/callback?code=…&state=…`
  (no cookie) → `GET /api/auth/chatgpt/connection` with the cookie. The stub records each token request's form so tests
  assert `grant_type`, `code_verifier` (SHA-256 matches the `code_challenge`), `client_id`, `redirect_uri`.
  Stub token values contain the marker `STUBSECRET` (e.g. `at-STUBSECRET-1`); NFR-1 backend test scans every
  text column of every `public` table, captured log output and every response body of the flow for `STUBSECRET`, the
  code, the verifier and the state, and finds none.
- Restart simulation: `ChatGptCredentialStore.clearAll()` then GET connection with the same cookie → `NOT_CONNECTED`.
- E2E stub service `e2e/stubs` (Node, no npm dependencies, port 4010), wired by `docker-compose.override.yml`
  (auto-merged by `docker compose up`, so `node factory-engine/bin/stack.mjs e2e` uses it; real use:
  `docker compose -f docker-compose.yml up -d`). The override adds service `stub` (ports `4010:4010`) and sets backend
  env `ORACUL_CHATGPT_AUTHORIZE_URL=http://localhost:4010/oauth/authorize` (browser-reachable),
  `ORACUL_CHATGPT_TOKEN_URL=http://stub:4010/oauth/token` (backend-reachable).
  | Stub endpoint | Behaviour |
  |---|---|
  | `GET /oauth/authorize` | mode `ok`/`not_eligible`/`token_error`: 302 to `redirect_uri?code=stub-code-<n>&state=<state>` plus `&client_id=oaiapp_stub_client` when `client_id=dynamic_agent_client`; mode `deny`: 302 to `redirect_uri?error=access_denied&state=<state>`; remembers `code_challenge` per code |
  | `POST /oauth/token` | `authorization_code`: verifies `code_verifier` against the remembered challenge (mismatch → 400 `{"error":"invalid_grant"}`); mode `token_error` → 500; else 200 `{"access_token":"at-STUBSECRET-<n>","refresh_token":"rt-STUBSECRET-<n>","id_token":"id-STUBSECRET-<n>","token_type":"Bearer","expires_in":3600,"scope":"<scopes>"}` — `scope` lacks `chatgpt.tokens.use.direct` in mode `not_eligible`. `refresh_token`: mode `refresh_error` → 400 `{"error":"invalid_grant"}`, else new tokens |
  | `POST /__control/mode` | body `{"mode":"ok"\|"not_eligible"\|"deny"\|"token_error"\|"refresh_error"}` → 204 |
  | `POST /__control/reset` | mode `ok`, forget codes → 204 |
  | `GET /__control/issued` | 200 `{"values":[...]}` every code and token issued so far (for NFR-1 scans) |
  E2E chatgpt specs run serially (`test.describe.configure({ mode: 'serial' })`) and reset the stub in `beforeEach`.
- Web-server forward: `frontend/nginx.conf` gets `location = /auth/callback { proxy_pass
  http://backend:8080/api/auth/chatgpt/callback$is_args$args; }`; `frontend/proxy.conf.json` gets
  `"/auth/callback": {"target": "http://localhost:8080", "pathRewrite": {"^/auth/callback": "/api/auth/chatgpt/callback"}}`.

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| GET | /api/auth/chatgpt/authorize | startChatGptSignIn | — (browser navigation) | 302 Location: OpenAI authorize URL (or `<frontend>/?chatgpt=not_completed` on misconfiguration) |
| GET | /api/auth/chatgpt/callback | completeChatGptSignIn | query `code`, `state`, `error`, `error_description`, `client_id` (all optional, length limits enforced by the service, not by Bean Validation) | 302 Location: `<frontend>/?chatgpt=connected\|not_completed\|not_eligible` |
| GET | /api/auth/chatgpt/connection | getChatGptConnection | — | 200 ChatGptConnection · 500 INTERNAL_ERROR |
| DELETE | /api/auth/chatgpt/connection | disconnectChatGpt | — | 204 · 500 INTERNAL_ERROR |

Generated backend interface `com.oracul.app.api.ChatgptApi`, implemented by `com.oracul.app.chatgpt.ChatGptController`;
generated frontend service `ChatgptService` (`src/app/api/services/chatgpt.service.ts`).

Web-server routing (frontend nginx and Angular dev proxy): `GET /auth/callback?…` → backend
`/api/auth/chatgpt/callback?…` (query preserved), so the registered redirect URI keeps the official
`http://127.0.0.1:<port>/auth/callback` shape.

## UI
- Route: header on every route (`/` in this slice), rendered inside the existing `mat-toolbar` (`app-header`) to the
  right of the wordmark, in every app state (loading, backend unavailable, ready). Component
  `ChatGptConnectionComponent` (selector `app-chatgpt-connection`, file `src/app/chatgpt/chatgpt-connection.ts`);
  store `ConnectionStore` (`src/app/chatgpt/connection.store.ts`, `providedIn: 'root'`) with
  `state: Signal<'LOADING' | ChatGptConnectionState>`, `canGenerate: Signal<boolean>`, `load()`, `disconnect()`,
  `handleReturn(outcome: string | null)`. Material: `mat-toolbar`, `mat-button`/`mat-flat-button`, `mat-icon`,
  `MatSnackBar`.
- States: loading ("Checking…", no action) · NOT_CONNECTED · CONNECTED · PLAN_NOT_ELIGIBLE · SESSION_EXPIRED (table in
  FR-8). Failed load → NOT_CONNECTED.
- Return from OpenAI: on start the app reads `?chatgpt=` once. `connected` → snackbar "ChatGPT connected";
  `not_completed` → "ChatGPT connection was not completed"; `not_eligible` → "Your ChatGPT plan is not eligible for
  ORACUL"; any other value → no snackbar. In every case the `chatgpt` parameter is removed with
  `router.navigate([], { queryParams: { chatgpt: null }, queryParamsHandling: 'merge', replaceUrl: true })` and the
  connection is (re)loaded. Snackbars are opened with `MatSnackBar.openFromComponent` (duration 6000 ms) whose content
  element carries `data-testid="chatgpt-message"` and contains exactly the message text.
- `data-testid`s:
  - `chatgpt-status` — `<span>` with the state text of the FR-8 table
  - `chatgpt-connect` — `<a mat-flat-button href="/api/auth/chatgpt/authorize">` text "Continue with ChatGPT" (plain
    navigation, no click handler); present only in NOT_CONNECTED / PLAN_NOT_ELIGIBLE / SESSION_EXPIRED
  - `chatgpt-disconnect` — `<button mat-stroked-button>` text "Disconnect"; present only in CONNECTED; `disabled`
    while the DELETE is in flight
  - `chatgpt-message` — snackbar text container
