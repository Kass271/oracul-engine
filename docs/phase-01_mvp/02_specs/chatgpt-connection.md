# Spec — ChatGPT connection and credential lifecycle

Covers: FR-7, FR-8, FR-9

## Purpose
Connect ORACUL to the user's ChatGPT plan through OpenAI's official open-source "Sign in with ChatGPT" flow
(OAuth 2.0 authorization code + PKCE S256, self-serve dynamic client registration, loopback redirect on 127.0.0.1),
show the connection state in the header, let the user disconnect, and guarantee that no credential ever leaves backend
memory. ORACUL never asks for a password or API key.

## Data

### Persisted (PostgreSQL) — non-secret only
| Entity | Field | Type | Rules |
|---|---|---|---|
| browser_session | id | uuid | PK; value of the `ORACUL_SID` cookie (random UUID v4, HttpOnly, SameSite=Lax, Path=/, no Max-Age = browser session) |
| browser_session | created_at / last_seen_at | timestamptz | last_seen_at updated at most once per minute |
| chatgpt_client_registration | id | bigint identity | single row per installation |
| chatgpt_client_registration | host_id | uuid | `ext_agent_host_id`, generated once, stable |
| chatgpt_client_registration | client_id | varchar(256) | issued `oaiapp_…` id from the first successful callback; null until then |
| chatgpt_client_registration | created_at / updated_at | timestamptz | |

No table has a column for access token, refresh token, id token, authorization code, code verifier, state, cookie or
authorization header (FR-9, NFR-1).

### Runtime only (backend process memory) — `ChatGptCredentialStore`
| Entity | Field | Type | Rules |
|---|---|---|---|
| PendingAuthorization | state | Secret | 32 random bytes base64url; key of the map; single use |
| PendingAuthorization | codeVerifier | Secret | 64 random bytes base64url (RFC 7636) |
| PendingAuthorization | sessionId | uuid | session that started the flow |
| PendingAuthorization | dynamicRegistration | boolean | true when no client_id was persisted at start |
| PendingAuthorization | createdAt | Instant | expires after 10 min (configurable `oracul.chatgpt.pending-ttl`) |
| SessionCredentials | sessionId | uuid | map key |
| SessionCredentials | accessToken / refreshToken / idToken | Secret | never serialised, `toString()` = `[REDACTED]` |
| SessionCredentials | accessTokenExpiresAt | Instant | now + `expires_in` |
| SessionCredentials | grantedScopes | Set<String> | from the token response `scope` |
| SessionConnectionFlag | state | PLAN_NOT_ELIGIBLE / SESSION_EXPIRED | memory only, cleared by disconnect or a new sign-in |

`Secret` is a dedicated type (char[]/String holder) without Jackson serialisation (`@JsonIgnore`-equivalent: a
serializer that throws) and with a redacting `toString()`.

### Configuration (application properties; tests point them at stub servers — NFR-7)
| Property | Default |
|---|---|
| `oracul.chatgpt.authorize-url` | `https://auth.openai.com/api/accounts/authorize` |
| `oracul.chatgpt.token-url` | `https://auth.openai.com/api/accounts/oauth/token` |
| `oracul.chatgpt.redirect-uri` | `http://127.0.0.1:4200/auth/callback` (must use host 127.0.0.1, never `localhost` — validated at startup) |
| `oracul.chatgpt.scopes` | `openid profile email offline_access resource.invoke chatgpt.tokens.use.direct` |
| `oracul.chatgpt.resource` | `https://api.openai.com/v1` |
| `oracul.chatgpt.dynamic-client-id` | `dynamic_agent_client` |
| `oracul.chatgpt.agent-name-hint` | `ORACUL` |
| `oracul.chatgpt.required-scope` | `chatgpt.tokens.use.direct` |
| `oracul.frontend-base-url` | `http://localhost:4200` (target of the post-callback redirect) |
| `oracul.openai.responses-base-url` | `https://api.openai.com/v1` (used by scenario-reasoning.md) |

## Credential lifecycle
1. **Start** (`GET /api/auth/chatgpt/authorize`, browser navigation from the header button): create
   PendingAuthorization; build the authorize URL:
   `response_type=code`, `client_id` = persisted `oaiapp_…` id, or `dynamic_agent_client` plus
   `ext_agent_host_id=<host_id>` and `agent_name_hint=ORACUL` when none is persisted; `redirect_uri`; `scope`
   (space-separated, URL-encoded); `state`; `code_challenge` = BASE64URL(SHA-256(verifier)); `code_challenge_method=S256`;
   `resource`. Answer 302 `Location: <authorize URL>`.
2. **Callback** (`GET /auth/callback` on 127.0.0.1 → forwarded to `GET /api/auth/chatgpt/callback`): look up and
   remove the PendingAuthorization by `state` (the callback host differs from the app host, so the session is taken
   from the pending entry, not from the cookie). If `client_id` (oaiapp_…) is present on a dynamic registration,
   persist it. Exchange the code: `POST token-url` form `grant_type=authorization_code`, `code`, `redirect_uri`,
   `client_id`, `code_verifier`. Discard code and verifier immediately after the exchange (success or failure).
3. **Eligibility**: if `scope` of the token response lacks `chatgpt.tokens.use.direct` → drop all tokens, set the
   session flag PLAN_NOT_ELIGIBLE, redirect `?chatgpt=not_eligible`. Otherwise store SessionCredentials, clear flags,
   redirect `?chatgpt=connected`.
4. **Use**: before every ChatGPT call (and synchronously in `startRun`), if the access token expires within 60 s,
   refresh: `POST token-url` form `grant_type=refresh_token`, `refresh_token`, `client_id`; replace access and (rotated)
   refresh token in memory. Refresh failure (4xx, network) → drop tokens, flag SESSION_EXPIRED.
5. **End**: `DELETE /api/auth/chatgpt/connection` drops SessionCredentials, pending entries and flags of the session.
   Backend restart loses all memory → every session is NOT_CONNECTED. Nothing to clean in the database.

## Behaviour

### FR-7 — Continue with ChatGPT (sign-in)
- Happy path: header button "Continue with ChatGPT" (`chatgpt-connect`) is a plain navigation
  (`window.location.href = '/api/auth/chatgpt/authorize'`) → 302 to OpenAI → user consents → OpenAI redirects to
  `http://127.0.0.1:<port>/auth/callback?code=…&state=…[&client_id=oaiapp_…]` → 302 to
  `<frontend-base-url>/?chatgpt=connected` → the app reads the query parameter, removes it from the URL
  (`replaceUrl`), reloads the connection, and the header shows "ChatGPT connected" (`chatgpt-status`).
- Rules: a fresh state and verifier for every start (two starts → two different states, both valid until used or
  expired). The authorize URL always contains `response_type=code`, `code_challenge_method=S256`, the 6 scopes and a
  `redirect_uri` whose host is `127.0.0.1`. No ORACUL screen contains a password or API-key input (no
  `input[type=password]` anywhere in the app).
- Errors (all are 302 to `<frontend-base-url>/?chatgpt=not_completed`; the app shows snackbar
  "ChatGPT connection was not completed" and the header stays "Continue with ChatGPT"):
  - `state` missing, unknown, already used or expired → no token request is made
  - `error=access_denied` (or any `error` value) → pending entry removed, no token request
  - `code` missing with a valid state → pending entry removed
  - token endpoint answers non-2xx, times out (10 s) or returns no `access_token` → nothing stored
  - the callback never answers 4xx/5xx and never echoes `code`, `state` or `error_description` into the redirect
  - `startChatGptSignIn` when the authorize URL cannot be built (misconfiguration) → 302 to
    `?chatgpt=not_completed` (never a 500 page)

### FR-8 — ChatGPT connection status and sign-out
- Happy path: `GET /api/auth/chatgpt/connection` → `{state, canGenerate}`; the header renders:
  | state | header text (`chatgpt-status`) | button |
  |---|---|---|
  | NOT_CONNECTED | "Not connected" | "Continue with ChatGPT" (`chatgpt-connect`) |
  | CONNECTED | "ChatGPT connected" | "Disconnect" (`chatgpt-disconnect`) |
  | PLAN_NOT_ELIGIBLE | "Plan not eligible" | "Continue with ChatGPT" |
  | SESSION_EXPIRED | "Session expired" | "Continue with ChatGPT" |
  The app reloads the state on page load, after the OAuth return, after Disconnect and whenever an API call answers
  `CHATGPT_SESSION_EXPIRED` / `CHATGPT_NOT_CONNECTED` / `CHATGPT_PLAN_NOT_ELIGIBLE`.
- Disconnect: `DELETE /api/auth/chatgpt/connection` → 204 → header shows "Continue with ChatGPT"; a subsequent
  `startRun` answers 401 `CHATGPT_NOT_CONNECTED`. Disconnect while a run is active: the run fails at its next ChatGPT
  call with `CHATGPT_SESSION_EXPIRED` (generation-runs.md).
- Rules: `canGenerate` is true only for CONNECTED; the generate button is disabled otherwise (generation-runs.md).
- Errors:
  - granted scopes lack `chatgpt.tokens.use.direct` → callback 302 `?chatgpt=not_eligible` → snackbar
    "Your ChatGPT plan is not eligible for ORACUL"; state PLAN_NOT_ELIGIBLE; `startRun` → 403
    `CHATGPT_PLAN_NOT_ELIGIBLE` → "Your ChatGPT plan is not eligible for ORACUL"
  - backend restarted → state NOT_CONNECTED (memory empty) → header "Continue with ChatGPT"
  - access token expired and refresh fails at `startRun` → 401 `CHATGPT_SESSION_EXPIRED` →
    "ChatGPT session expired — please reconnect"; no run row is created; state becomes SESSION_EXPIRED
  - connection endpoint unexpected failure → 500 `INTERNAL_ERROR` → header shows "Not connected" and generation stays
    disabled

### FR-9 — Runtime-only credential handling
- Happy path: tokens, codes, verifiers and states exist only in `ChatGptCredentialStore` (ConcurrentHashMap) for
  the minimum lifetime listed above; only `browser_session` and `chatgpt_client_registration` are persisted.
- Rules:
  - Logging: a Logback `MessageConverter`/`TurboFilter`-level redactor (`SecretRedactor`) applied to every log event
    message and throwable text replaces: `Bearer <anything up to whitespace/quote>` → `Bearer [REDACTED]`;
    `(access_token|refresh_token|id_token|code|code_verifier|state)=<value>` → `$1=[REDACTED]`;
    JSON `"(access_token|refresh_token|id_token|code_verifier)"\s*:\s*"<value>"` → `"$1":"[REDACTED]"`.
  - HTTP clients to OpenAI never log request/response bodies or headers; access/request logs mask the callback query.
  - Error mapping (`@RestControllerAdvice`) never copies exception messages of HTTP-client or provider exceptions into
    `ApiError.message`; it uses the fixed messages of the specs.
  - Generation runs store only `session_id`; prompts stored for transparency (scenario-reasoning.md) never contain a
    header or token.
  - Frontend: no credential, code or state is ever in a response body, so nothing can reach localStorage,
    sessionStorage or IndexedDB; the frontend writes nothing to web storage at all (panel state lives in memory).
  - Actuator/env endpoints are not exposed; Spring Boot error attributes `include-message/stacktrace/exception` =
    never.
- Errors:
  - any exception during sign-in or a ChatGPT call → the logged line contains "Bearer [REDACTED]" and no token value;
    the user sees the fixed message of the failing flow (FR-7 / FR-32), never a provider body.
  - `Authorization` header of an incoming request (should a client send one) → ignored and redacted in logs.

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| GET | /api/auth/chatgpt/authorize | startChatGptSignIn | — (browser navigation) | 302 Location: OpenAI authorize URL |
| GET | /api/auth/chatgpt/callback | completeChatGptSignIn | query `code`, `state`, `error`, `error_description`, `client_id` (all optional) | 302 Location: `<frontend>/?chatgpt=connected\|not_completed\|not_eligible` |
| GET | /api/auth/chatgpt/connection | getChatGptConnection | — | 200 ChatGptConnection · 500 INTERNAL_ERROR |
| DELETE | /api/auth/chatgpt/connection | disconnectChatGpt | — | 204 · 500 INTERNAL_ERROR |

Web-server routing (frontend nginx and Angular dev proxy): `GET /auth/callback?…` → backend
`/api/auth/chatgpt/callback?…` (query preserved), so the registered redirect URI keeps the official
`http://127.0.0.1:<port>/auth/callback` shape.

## UI
- Route: header on every route · component `ChatGptConnectionComponent` in `src/app/chatgpt/` · Material:
  `mat-toolbar`, `mat-button`, `mat-icon`, `MatSnackBar`.
- States: loading (`chatgpt-status` "Checking…") · NOT_CONNECTED · CONNECTED · PLAN_NOT_ELIGIBLE · SESSION_EXPIRED.
- Snackbars on return from OpenAI (`?chatgpt=`): connected → "ChatGPT connected"; not_completed → "ChatGPT connection
  was not completed"; not_eligible → "Your ChatGPT plan is not eligible for ORACUL".
- `data-testid`s: `chatgpt-status`, `chatgpt-connect` ("Continue with ChatGPT"), `chatgpt-disconnect` ("Disconnect"),
  `chatgpt-message` (snackbar text container).
