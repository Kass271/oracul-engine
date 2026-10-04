# Spec — ChatGPT connection (documented sign-in, callback, reset)

Covers: FR-35, FR-36, FR-37, FR-41

Also realises NFR-9 (loopback redirect on `/callback`). Phase-01 spec `docs/phase-01_mvp/02_specs/chatgpt-connection.md`
stays valid except where this spec says otherwise; this spec is the delta against it and against the current code in
`backend/src/main/java/com/oracul/app/chatgpt/`.

## Purpose
Make "Continue with ChatGPT" work against OpenAI's real Sign in with ChatGPT (plan usage) flow, as documented on
developers.openai.com/siwc/token-sharing-open-source/ (sign-in, profiles-and-sessions, read 2026-10-04): the
documented authorize request (`urn:uuid:` host id, `/callback` loopback redirect, `nonce`), the documented callback
handling (state, issued client id, code exchange with `resource`, ID-token validation incl. nonce, granted-scope
check), a "Reset ChatGPT connection" action, and the sign-in conditions shown before the user starts.

## What changes against phase-01 / current code
| Area | Today (code) | After this spec |
|---|---|---|
| `ext_agent_host_id` | bare UUID, only on first registration | `urn:uuid:<host_id>` on **every** authorize request (first registration and reauthorization) |
| `redirect_uri` | `http://127.0.0.1:4200/auth/callback` | `http://127.0.0.1:4200/callback` |
| `nonce` | not sent | fresh per attempt, sent, checked against the ID token |
| `agent_name_hint` | first registration only | unchanged (first registration only) |
| Token exchange form | no `resource` | adds `resource=https://api.openai.com/v1` |
| First-registration callback without issued `client_id` | falls back to `dynamic_agent_client` for the exchange | `not_completed`, no token request, nothing persisted |
| Reauthorization callback with a different `client_id` | ignored (stored id used) | rejected → `not_completed`, no token request, stored id unchanged |
| ID token | stored, never validated | validated (RS256 signature vs JWKS, `iss`, `aud`, `exp`, `nonce`) before anything is stored |
| `invalid_grant` on code exchange | `not_completed` | new outcome `expired` |
| Client id persisted | after token exchange | after token exchange **and** ID-token validation |
| Callback outcomes | `connected`, `not_completed`, `not_eligible` | + `not_verified`, `expired` |
| Snackbar for `not_completed` | "ChatGPT connection was not completed" | "ChatGPT connection was not completed — please try again" |
| Reset | none (manual DB edit) | `DELETE /api/auth/chatgpt/registration` + header menu + confirmation dialog |
| Web-server forward | `/auth/callback` → backend callback | `/callback` → backend callback (`/auth/callback` location removed) |

## Data

### Persisted — unchanged schema (`chatgpt_client_registration`, Flyway V2)
| Entity | Field | Type | Rules |
|---|---|---|---|
| chatgpt_client_registration | host_id | uuid NOT NULL | Generated once (first `startChatGptSignIn`), never regenerated — also not by reset. Sent as `urn:uuid:` + the canonical lower-case UUID text (36 chars). A phase-01 row keeps its UUID; only the wire form changes (no migration). |
| chatgpt_client_registration | client_id | varchar(256) NULL | Issued `oaiapp_…` id. Written only after a successful code exchange **and** a valid ID token of a first-registration attempt; must match `^oaiapp_[A-Za-z0-9_-]{1,249}$`; `dynamic_agent_client` is never stored. Never overwritten while set; set to NULL only by reset (FR-37). |
| chatgpt_client_registration | updated_at | timestamptz | set on persist and on reset |

No new table, no new column; NFR-1 / phase-01 FR-9 rules stay (no credential column).

### Runtime only — `ChatGptCredentialStore`
| Entity | Field | Type | Rules |
|---|---|---|---|
| PendingAuthorization | nonce | Secret | **new**: 32 random bytes (SecureRandom) base64url without padding (43 chars); single use with the entry |
| PendingAuthorization | state, codeVerifier, sessionId, clientId, dynamicRegistration, createdAt | — | unchanged |
| SessionCredentials | idToken | Secret | now always a validated ID token |
| SessionConnectionFlag | state | PLAN_NOT_ELIGIBLE / SESSION_EXPIRED / **REGISTRATION_INVALID** | REGISTRATION_INVALID is set by chatgpt-inference.md FR-40 (`invalid_client`); cleared by disconnect, reset or the next successful sign-in |

### Configuration (`ChatGptProperties`, prefix `oracul.chatgpt`)
| Property | Default | Change |
|---|---|---|
| `redirect-uri` | `http://127.0.0.1:4200/callback` | default and validation changed |
| `issuer` | `https://auth.openai.com` | new — expected ID-token `iss` |
| `jwks-url` | `https://auth.openai.com/.well-known/jwks.json` | new (value from the OpenID configuration) |
| `revocation-url` | `https://auth.openai.com/api/accounts/oauth/revoke` | new (value from the OpenID configuration) |
| `jwks-cache-ttl` | `PT1H` | new |
| `authorize-url`, `token-url`, `scopes`, `resource`, `dynamic-client-id`, `agent-name-hint`, `required-scope`, `pending-ttl`, `http-timeout` | unchanged | — |
| `refresh-skew` | `PT5M` | changed — see chatgpt-inference.md FR-38 |

Startup validation (NFR-9, replaces phase-01 NFR-6): `ChatGptProperties.requireLoopbackRedirect(String)` accepts only an
absolute `http` URI with host exactly `127.0.0.1`, an explicit port 1–65535, path exactly `/callback`, no query and no
fragment; otherwise the context fails with `IllegalStateException("oracul.chatgpt.redirect-uri must be
http://127.0.0.1:<port>/callback")`. Examples: `http://127.0.0.1:4200/callback` ✓, `http://127.0.0.1:1455/callback` ✓,
`http://127.0.0.1:4200/auth/callback` ✗, `http://localhost:4200/callback` ✗, `https://127.0.0.1:4200/callback` ✗,
`http://127.0.0.1/callback` ✗, `http://127.0.0.1:4200/callback/` ✗, `http://127.0.0.1:4200/callback?x=1` ✗.

### Web-server routing
- `frontend/nginx.conf`: `location = /callback { proxy_pass http://backend:8080/api/auth/chatgpt/callback$is_args$args;
  proxy_set_header Host $host; }`; the `location = /auth/callback` block is removed.
- `frontend/proxy.conf.json`: `"/callback"` → `http://localhost:8080`, `pathRewrite {"^/callback": "/api/auth/chatgpt/callback"}`;
  the `/auth/callback` entry is removed.
- The exact-match location wins over the SPA fallback, so `/callback` never renders the Angular app.

## Behaviour

### FR-35 — Documented authorize request
- Happy path: `GET /api/auth/chatgpt/authorize` (browser navigation from `chatgpt-connect`) → ensure the registration
  row (create with a random `host_id` if absent) → create a PendingAuthorization with fresh `state`, `nonce`,
  `codeVerifier` → `302 Location: <authorize-url>?<query>` with exactly these parameters (any order, RFC 3986
  percent-encoding, spaces `%20`, each name once, no other parameter):
  | Parameter | First registration (no stored client id) | Reauthorization (stored `oaiapp_X`) |
  |---|---|---|
  | `client_id` | `dynamic_agent_client` | `oaiapp_X` |
  | `agent_name_hint` | `ORACUL` | absent |
  | `ext_agent_host_id` | `urn:uuid:<host_id>` | `urn:uuid:<host_id>` (same value) |
  | `response_type` | `code` | `code` |
  | `redirect_uri` | `http://127.0.0.1:4200/callback` | same |
  | `scope` | `openid profile email offline_access resource.invoke chatgpt.tokens.use.direct` | same |
  | `resource` | `https://api.openai.com/v1` | same |
  | `state` | fresh, 43 chars base64url | fresh |
  | `nonce` | fresh, 43 chars base64url | fresh |
  | `code_challenge_method` | `S256` | `S256` |
  | `code_challenge` | BASE64URL-no-padding(SHA-256(ASCII(code_verifier))), 43 chars | same rule |
  `id_token_hint` and `login_hint` are not sent (tokens are runtime-only; one account per installation).
- Rules:
  - Two starts give different `state`, `nonce` and `code_challenge`, identical `ext_agent_host_id` and `redirect_uri`;
    both pending entries stay valid until used or expired (`pending-ttl`).
  - A phase-01 registration row (bare UUID in `host_id`) is reused: the request carries `urn:uuid:<that uuid>`; no
    new host id is generated.
  - `redirect_uri` never contains `localhost`; its path is exactly `/callback` (startup validation above).
- Errors:
  - authorize URL cannot be built (e.g. `oracul.chatgpt.authorize-url=http://[bad`) → `302` to
    `<frontend-base-url>/?chatgpt=not_completed` → snackbar "ChatGPT connection was not completed — please try again"
    (never a 4xx/5xx page)
  - database unavailable while ensuring the registration → same `302 ?chatgpt=not_completed`
  - invalid `oracul.chatgpt.redirect-uri` → application does not start (`IllegalStateException`, message above)

- Changes earlier behaviour: `ext_agent_host_id` bare UUID sent on first registration only (`UUID.fromString` of the value; reauthorization asserted to carry no host id) → `urn:uuid:<host_id>` on every authorize request incl. reauthorization, stored `host_id` stays the bare UUID (tests: backend/src/test/java/com/oracul/app/chatgpt/ChatGptSignInIT.java)
- Changes earlier behaviour: `redirect_uri` `http://127.0.0.1:4200/auth/callback` and startup message `…must be http://127.0.0.1:<port>/auth/callback` → `http://127.0.0.1:4200/callback` and `…must be http://127.0.0.1:<port>/callback`; the web-server path `/auth/callback` is gone (tests: backend/src/test/java/com/oracul/app/chatgpt/AbstractChatGptIT.java, backend/src/test/java/com/oracul/app/chatgpt/ChatGptStartupValidationTest.java, e2e/tests/chatgpt-connection.spec.ts)
- Changes earlier behaviour: no `nonce` parameter → fresh 43-char `nonce` on every authorize request (tests: none)
- Ranges & invariants: `oracul.chatgpt.redirect-uri` classes (parameterized startup test, valid → context starts, invalid → `IllegalStateException` with the exact message): valid `http://127.0.0.1:4200/callback`, `http://127.0.0.1:1/callback`, `http://127.0.0.1:65535/callback`, `http://127.0.0.1:1455/callback`; invalid scheme `https://…`, host `localhost` / `127.0.0.2` / `[::1]`, no port, port `0`, port `65536`, path `/auth/callback` / `/callback/` / `/other` / empty, any query `?x=1`, any fragment `#f`. Authorize request invariants for every start (first registration and reauthorization, N ≥ 3 starts): the parameter set is exactly the 11 (first registration) / 10 (reauthorization) names of the table, each once, no `id_token_hint`/`login_hint`; `ext_agent_host_id` = `urn:uuid:` + the stored `host_id` text (45 chars, matches `^urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`) and identical across all starts and across reset; `state`, `nonce`, `code_challenge` each match `^[A-Za-z0-9_-]{43}$` and are pairwise distinct across the N starts; `code_challenge` = S256 of the verifier later sent to the token endpoint (verifier `^[A-Za-z0-9_-]{86}$`); `redirect_uri` identical in every start and equal to the one sent in the token request; `chatgpt_client_registration` has exactly one row after any number of starts; a pre-existing row with a bare UUID is reused (no new host id).

### FR-36 — Callback handling, nonce check and issued client id persistence
- Happy path: OpenAI redirects the browser to `http://127.0.0.1:4200/callback?code=…&state=…[&client_id=oaiapp_X]
  [&scope=…]` → nginx forwards to `GET /api/auth/chatgpt/callback` → `302 Location:
  <frontend-base-url>/?chatgpt=connected` → snackbar "ChatGPT connected", header "ChatGPT connected".
- Rules — evaluated in this order, the first matching rule decides (replaces phase-01 callback rules 1–11):
  1. a parameter longer than its limit (`code` 4096, `state` 512, `error` 256, `error_description` 2048, `client_id`
     256) → `not_completed`, nothing removed, no token request
  2. `state` missing/empty, unknown, or its entry expired (expired entry removed) → `not_completed`, no token request
  3. remove the pending entry (single use from here on; its code verifier and nonce are dropped after this request)
  4. `error` present (any value, e.g. `access_denied`) → `not_completed`, no token request
  5. `code` missing or empty → `not_completed`, no token request
  6. exchange client id:
     - first registration (entry `dynamicRegistration`): the callback `client_id` must match
       `^oaiapp_[A-Za-z0-9_-]{1,249}$` → that value; missing, `dynamic_agent_client` or any other value →
       `not_completed`, no token request, nothing persisted
     - reauthorization (entry carries `oaiapp_X`): callback `client_id` absent or equal to `oaiapp_X` → `oaiapp_X`;
       any other value → `not_completed`, no token request, stored id unchanged
  7. token request `POST token-url`, `Content-Type: application/x-www-form-urlencoded`, `Accept: application/json`,
     form exactly `grant_type=authorization_code`, `code`, `client_id=<exchange client id>`, `code_verifier`,
     `redirect_uri=<the redirect-uri sent in the authorize request>`, `resource=https://api.openai.com/v1`
  8. token response 4xx whose JSON `error` (string, or `error.code`) is `invalid_grant` → `expired`; nothing stored
  9. any other non-2xx, timeout (`http-timeout`), connection error, body not JSON, or no non-empty `access_token` →
     `not_completed`; nothing stored; the session's previous credentials/flag stay unchanged
  10. ID-token validation (all must hold, else `not_verified`, nothing stored, nothing persisted, previous
      credentials/flag unchanged):
      - `id_token` present, three base64url segments, header `alg` = `RS256` (any other `alg`, incl. `none`/`HS256`
        → invalid) and a `kid`
      - signature verifies with the RSA key of that `kid` from `jwks-url` (JWKS cached `jwks-cache-ttl`; an unknown
        `kid` triggers one refetch; JWKS unreachable/invalid → invalid)
      - `iss` equals `oracul.chatgpt.issuer`
      - `aud` equals the exchange client id (string) or is an array containing it
      - `exp` (seconds) is after now (injected `Clock`)
      - `nonce` equals the nonce of this pending entry
  11. first registration: persist the exchange client id with the existing host id (`persistClientIdIfAbsent`; a
      concurrently stored id is kept)
  12. granted scopes = token response `scope` split on spaces (absent → requested scopes); lacks
      `chatgpt.tokens.use.direct` → drop the session's credentials, flag PLAN_NOT_ELIGIBLE → `not_eligible`
  13. store SessionCredentials (access, refresh, validated id token, expiry, scopes, exchange client id), clear the
      session's flag → `connected`
  Every outcome answers `302 Location: <frontend-base-url>/?chatgpt=<outcome>` (exactly this URL), never 4xx/5xx,
  never echoing `code`, `state`, `nonce`, `error`, `error_description` or a token. Any unexpected exception →
  `not_completed`.
- Frontend return handling (`ConnectionStore.handleReturn`), snackbar `chatgpt-message`, then `chatgpt` query
  parameter removed (replaceUrl) and connection reloaded:
  | `?chatgpt=` | Snackbar text |
  |---|---|
  | `connected` | ChatGPT connected |
  | `not_completed` | ChatGPT connection was not completed — please try again |
  | `not_eligible` | Your ChatGPT plan is not eligible for ORACUL |
  | `not_verified` | ChatGPT sign-in could not be verified — please try again |
  | `expired` | Sign-in expired — click Continue with ChatGPT to start again |
  | anything else | no snackbar |
- Errors (callback outcome → snackbar; header stays/turns "Not connected" + "Continue with ChatGPT" unless noted):
  - state missing / wrong / expired / used → `not_completed` → "ChatGPT connection was not completed — please try again"
  - `error=access_denied` with a known state → `not_completed` → same message; the state cannot be reused
  - first-registration callback without an issued `client_id` → `not_completed` → same message; registration row
    `client_id` stays NULL
  - reauthorization callback with `client_id` ≠ stored id → `not_completed` → same message; stored id unchanged
  - code exchange `invalid_grant` → `expired` → "Sign-in expired — click Continue with ChatGPT to start again"
  - token endpoint other error / timeout / unreachable / no `access_token` → `not_completed`
  - ID token missing, bad signature, wrong `alg`, unknown `kid`, JWKS unreachable, wrong `iss`, wrong `aud`, expired,
    nonce mismatch → `not_verified` → "ChatGPT sign-in could not be verified — please try again"
  - granted scopes lack `chatgpt.tokens.use.direct` → `not_eligible` → "Your ChatGPT plan is not eligible for
    ORACUL"; header "Plan not eligible"; `canGenerate=false`
  - over-long parameter → `not_completed`, no token request

- Changes earlier behaviour: first-registration callback without issued `client_id` → exchanged with `dynamic_agent_client` and ended `connected` → `not_completed`, no token request, nothing persisted; every test sign-in helper must now pass `client_id=oaiapp_…` on a first registration (and may omit it on reauthorization) (tests: backend/src/test/java/com/oracul/app/chatgpt/AbstractChatGptIT.java, backend/src/test/java/com/oracul/app/runs/AbstractRunIT.java, backend/src/test/java/com/oracul/app/chatgpt/ChatGptSignInIT.java, backend/src/test/java/com/oracul/app/chatgpt/ChatGptConnectionIT.java, backend/src/test/java/com/oracul/app/chatgpt/ChatGptCredentialHandlingIT.java)
- Changes earlier behaviour: malformed callback `client_id` (`evil client`, `oaiapp_`, `other_abc`, `oaiapp_bad!id`) on first registration ignored → `connected` with `dynamic_agent_client` → `not_completed`, no token request, `client_id` stays NULL (tests: backend/src/test/java/com/oracul/app/chatgpt/ChatGptSignInIT.java)
- Changes earlier behaviour: reauthorization callback with a different `client_id` (`oaiapp_other`) ignored → `connected` → `not_completed`, no token request, stored id unchanged (tests: backend/src/test/java/com/oracul/app/chatgpt/ChatGptSignInIT.java)
- Changes earlier behaviour: token-response `id_token` stored unvalidated (stub issues opaque `id-STUBSECRET-<n>`) → must be an RS256 JWT verified against `jwks-url` with `iss`, `aud`, `exp`, `nonce`, else `not_verified`; the in-process stub must sign ID tokens (claims `iss`=`oracul.chatgpt.issuer`, `aud`=form `client_id`, `exp`=now+3600, `nonce`=the nonce of the attempt taken from the authorize Location, `kid` of its JWKS), serve the JWKS and register `oracul.chatgpt.jwks-url` / `oracul.chatgpt.issuer` in `registerAll` (tests: backend/src/test/java/com/oracul/app/chatgpt/StubOpenAi.java, backend/src/test/java/com/oracul/app/chatgpt/AbstractChatGptIT.java, backend/src/test/java/com/oracul/app/runs/AbstractRunIT.java, backend/src/test/java/com/oracul/app/chatgpt/ChatGptCredentialHandlingIT.java)
- Changes earlier behaviour: token endpoint 4xx with `{"error":"invalid_grant"}` → `not_completed` → `expired` (400 and 401 cases of the parameterized non-2xx test; 500 and 302 stay `not_completed`) (tests: backend/src/test/java/com/oracul/app/chatgpt/ChatGptSignInIT.java)
- Changes earlier behaviour: authorization-code token form without `resource` → adds `resource=https://api.openai.com/v1` (tests: none)
- Changes earlier behaviour: snackbar for `?chatgpt=not_completed` "ChatGPT connection was not completed" → "ChatGPT connection was not completed — please try again" (tests: frontend/src/app/chatgpt/connection.store.spec.ts, frontend/src/app/chatgpt/chatgpt-connection.spec.ts, e2e/tests/chatgpt-connection.spec.ts)
- Changes earlier behaviour: `?chatgpt=not_verified` / `?chatgpt=expired` showed no snackbar (unknown value) → messages of the table above (tests: none)
- Ranges & invariants: parameter lengths (parameterized, boundary each side): `code` 4096 → proceeds / 4097 → `not_completed`, nothing removed; `state` 512 / 513; `error` 256 / 257; `error_description` 2048 / 2049; `client_id` 256 / 257. Callback `client_id` classes on first registration: `oaiapp_a` (1 char after prefix) ✓, `oaiapp_` + 249 chars [A-Za-z0-9_-] (256 total) ✓, missing ✗, empty ✗, `oaiapp_` ✗, `dynamic_agent_client` ✗, `oaiapp_bad!id` ✗, `evil client` ✗, `OAIAPP_x` ✗ (✗ = `not_completed`, 0 token requests, `client_id` NULL); on reauthorization: absent ✓, equal ✓, any other value incl. another valid `oaiapp_…` ✗. ID-token classes (each alone, all else valid; ✗ = `not_verified`, nothing stored/persisted): `alg` RS256 ✓ / `none`, `HS256`, `RS512` ✗; missing `kid` ✗; unknown `kid` → exactly one JWKS refetch, still unknown ✗; tampered payload or signature ✗; JWKS 500 / unreachable / not JSON ✗; `iss` equal ✓ / other / with trailing `/` / missing ✗; `aud` = client id ✓ / array containing it ✓ / other string / array without it / missing ✗; `exp` = now+1 s ✓ / now ✗ / now−1 s ✗ / missing ✗ (injected `Clock`); `nonce` equal ✓ / nonce of another pending attempt / missing ✗; `id_token` missing / not 3 segments ✗. Token-response classes: 4xx with `error`=`invalid_grant` (string or `error.code`) → `expired`; 4xx with any other error, 5xx, 3xx (also with an `invalid_grant` body), timeout, non-JSON, missing/empty/null `access_token` → `not_completed`. Invariants for every callback: answer is 302 with Location exactly one of the 5 outcome URLs, never 4xx/5xx, never containing `code`, `state`, `nonce`, `error`, `error_description` or a token; rules 1–6 make 0 token requests, later rules exactly 1; a state is usable at most once (second use → `not_completed`, 0 token requests); `chatgpt_client_registration.client_id` changes only from NULL to the exchange client id of a first-registration attempt that passed rule 10 (outcomes `connected` or `not_eligible`), is never `dynamic_agent_client`, is never overwritten; on any non-`connected` outcome the session's previous credentials/flag are unchanged except `not_eligible`, which drops them.

### FR-37 — Reset ChatGPT connection
- Happy path: header menu `chatgpt-menu` → item `chatgpt-reset` "Reset ChatGPT connection" → dialog
  `chatgpt-reset-dialog` → `chatgpt-reset-confirm` "Reset" → `DELETE /api/auth/chatgpt/registration` → `204` → snackbar
  `chatgpt-message` "ChatGPT connection reset" → connection reloaded → header "Not connected" + "Continue with
  ChatGPT"; the next `startChatGptSignIn` is a first registration (`client_id=dynamic_agent_client`,
  `agent_name_hint=ORACUL`, same `ext_agent_host_id`).
- Rules:
  - Installation-wide (one registration per installation): under one lock, if **any** generation run is QUEUED or
    RUNNING → 409, nothing changed. Otherwise, in this order:
    1. best-effort revocation of every held refresh token (all sessions): `POST revocation-url`, form
       `token=<refresh token>`, `token_type_hint=refresh_token`, `client_id=<credentials' client id>`; one attempt
       each, timeout `http-timeout`; any outcome (200, non-2xx, timeout) is ignored and logged only as
       `chatgpt revocation: status=<code>` / `chatgpt revocation failed: <ExceptionSimpleName>`
    2. drop every SessionCredentials, every PendingAuthorization and every flag (incl. REGISTRATION_INVALID)
    3. `UPDATE chatgpt_client_registration SET client_id = NULL, updated_at = now` — `host_id` unchanged; no row →
       nothing to update (no row is created)
  - Idempotent: a reset without stored client id or tokens answers 204 too.
  - Disconnect (`DELETE /api/auth/chatgpt/connection`, phase-01 FR-8) gains the same best-effort revocation of the
    session's refresh token before clearing it; it still answers 204 whatever the revocation outcome.
  - Cancel (`chatgpt-reset-cancel`) or closing the dialog (Escape/backdrop) sends no request; nothing changes.
  - While the DELETE is in flight `chatgpt-reset-confirm` is disabled; the dialog closes when the request ends.
- Errors:
  - a run is QUEUED/RUNNING → `409 RUN_IN_PROGRESS` → snackbar "Wait until the current run finishes"; client id,
    tokens and flags unchanged; header unchanged
  - unexpected failure (e.g. database down) → `500 INTERNAL_ERROR` → snackbar "Something went wrong — try again";
    header keeps its state
  - network error / backend down → snackbar "Something went wrong — try again"

- Changes earlier behaviour: disconnect made no outbound call → one best-effort `POST revocation-url` per held refresh token before clearing; the in-process stub must serve the revocation endpoint, register `oracul.chatgpt.revocation-url` in `registerAll` (no test may reach the real OpenAI URL, NFR-7) and record revocation calls apart from the token `requests` list so existing token-request counts stay (tests: backend/src/test/java/com/oracul/app/chatgpt/StubOpenAi.java)
- Changes earlier behaviour: `ChatGptConnectionState` has 4 values and the header has no menu → 5 values (`REGISTRATION_INVALID` "Registration invalid", no connect) and `chatgpt-menu` in every non-LOADING state; the spec's exhaustive `STATUS_TEXT: Record<ChatGptConnectionState, string>` no longer compiles against the regenerated model (tests: frontend/src/app/chatgpt/chatgpt-connection.spec.ts)
- Ranges & invariants: run-status classes for reset (parameterized over all `RunStatus` values, any session): a run QUEUED → 409 `RUN_IN_PROGRESS`, RUNNING → 409, only COMPLETED / INSUFFICIENT_EVIDENCE / FAILED runs or no run → 204; a 409 changes nothing (client id, tokens, pending entries, flags, revocation calls = 0). Revocation outcome classes (200, 400, 500, timeout > `http-timeout`, connection refused) → reset and disconnect still 204 and the same state afterwards. Invariants after every 204 reset: `host_id` and the registration row count are unchanged (0 stays 0, 1 stays 1), `client_id` is NULL, every session (≥ 2 sessions tested) is NOT_CONNECTED, every pending state is unusable (callback → `not_completed`, 0 token requests), revocation calls = number of held refresh tokens (sessions without a refresh token add none), each with form exactly `token`, `token_type_hint=refresh_token`, `client_id`; reset is idempotent (second reset 204, 0 revocations); the next authorize is a first registration with the same `ext_agent_host_id`. Logs never contain a refresh token, only `chatgpt revocation: status=<code>` / `chatgpt revocation failed: <ExceptionSimpleName>`. UI: Cancel / Escape / backdrop → 0 DELETE requests; confirm sends exactly 1 DELETE and stays disabled until it ends; the header with `chatgpt-menu` stays within the viewport at 360, 414 and 767 px (existing mobile-layout check).

### FR-41 — Sign-in conditions shown in the UI
- Happy path: whenever the connection state is NOT_CONNECTED, PLAN_NOT_ELIGIBLE, SESSION_EXPIRED or
  REGISTRATION_INVALID, the welcome view's sign-in area (below `generate-hint` "Connect ChatGPT to generate") shows
  `chatgpt-conditions` with exactly: "Signing in needs a personal ChatGPT Plus or Pro account. Open ORACUL in a browser
  on the same computer where ORACUL runs."
- Rules:
  - Not shown while LOADING or CONNECTED.
  - No ORACUL screen has an input for a password, API key or configuration value: `input[type=password]` count 0 in
    every state; the sign-in path from `chatgpt-connect` to the return snackbar shows no ORACUL form (the only page
    between is OpenAI's).
- Errors:
  - connection load fails → state NOT_CONNECTED (phase-01) → conditions shown

- Changes earlier behaviour: none
- Ranges & invariants: connection-state classes (parameterized over all 5 `ChatGptConnectionState` values plus LOADING and a failed load): NOT_CONNECTED, PLAN_NOT_ELIGIBLE, SESSION_EXPIRED, REGISTRATION_INVALID, load 500, load network error → `chatgpt-conditions` present exactly once with the exact text; LOADING, CONNECTED → absent. In every one of these states and with the reset dialog open, `input[type=password]` count is 0 and no ORACUL element asks for an API key or configuration value.

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| GET | /api/auth/chatgpt/authorize | startChatGptSignIn | — (browser navigation) | 302 Location: OpenAI authorize URL (FR-35) or `<frontend>/?chatgpt=not_completed` |
| GET | /api/auth/chatgpt/callback | completeChatGptSignIn | query `code`, `state`, `error`, `error_description`, `client_id` (all optional; limits enforced by the service) | 302 Location: `<frontend>/?chatgpt=connected\|not_completed\|not_eligible\|not_verified\|expired` |
| GET | /api/auth/chatgpt/connection | getChatGptConnection | — | 200 ChatGptConnection (state adds REGISTRATION_INVALID) · 500 INTERNAL_ERROR |
| DELETE | /api/auth/chatgpt/connection | disconnectChatGpt | — | 204 · 500 INTERNAL_ERROR |
| DELETE | /api/auth/chatgpt/registration | resetChatGptRegistration | — | 204 · 409 RUN_IN_PROGRESS · 500 INTERNAL_ERROR |

Web-server route (not an API operation): `GET http://127.0.0.1:4200/callback?…` → backend
`/api/auth/chatgpt/callback?…` (query preserved).

## UI
- Route: header on every route (`ChatGptConnectionComponent`, `src/app/chatgpt/chatgpt-connection.ts`); conditions in
  the welcome view (`/`). Material: `mat-toolbar`, `mat-button`, `mat-icon-button`, `mat-menu`, `MatDialog`,
  `MatSnackBar`.
- Header states (phase-01 table plus one row; `chatgpt-menu` shown in every state except LOADING):
  | state | `chatgpt-status` | action |
  |---|---|---|
  | REGISTRATION_INVALID | "Registration invalid" | no `chatgpt-connect` (reset first) |
  Other rows unchanged (NOT_CONNECTED / PLAN_NOT_ELIGIBLE / SESSION_EXPIRED → `chatgpt-connect`, CONNECTED →
  `chatgpt-disconnect`).
- States: loading ("Checking…", no menu) · empty/not connected (connect + conditions) · error (snackbars above) ·
  success ("ChatGPT connected").
- `data-testid`s (new):
  - `chatgpt-menu` — `<button mat-icon-button aria-label="ChatGPT options">` with `mat-icon` `more_vert`
  - `chatgpt-reset` — `mat-menu-item` "Reset ChatGPT connection"
  - `chatgpt-reset-dialog` — dialog content; title "Reset ChatGPT connection?", text "ORACUL forgets its ChatGPT
    registration and signs you out. The next Continue with ChatGPT asks you to connect ORACUL again."
  - `chatgpt-reset-cancel` — `mat-button` "Cancel"
  - `chatgpt-reset-confirm` — `mat-flat-button` "Reset" (disabled while the DELETE is in flight)
  - `chatgpt-conditions` — `<p>` in the welcome view with the FR-41 text
  - existing: `chatgpt-status`, `chatgpt-connect`, `chatgpt-disconnect`, `chatgpt-message`

## Test fixtures (stubbed, NFR-7 — slice 01_signin-fix)
- In-process backend stub `StubOpenAi` (one per JVM): besides `POST /oauth/token` it serves `GET /jwks` (one RSA-2048
  key, `kid` = `stub-key-1`, `alg` RS256) and `POST /oauth/revoke` (default 200, controllable status/delay; calls kept
  in a separate `revocations` list). `registerAll` additionally registers `oracul.chatgpt.jwks-url` =
  `http://127.0.0.1:<port>/jwks`, `oracul.chatgpt.issuer` = `http://127.0.0.1:<port>` and
  `oracul.chatgpt.revocation-url` = `http://127.0.0.1:<port>/oauth/revoke`. `ok(...)` returns an `id_token` signed
  with that key: `iss` = the registered issuer, `aud` = the token request's `client_id`, `exp` = now + 3600 s, `iat`
  = now, `nonce` = the nonce the test helper registered for that attempt (taken from the authorize `Location`). Tests
  for invalid ID tokens build their own token (other key, other `alg`, other claims) through the same helper.
- E2E stub `e2e/stubs/server.mjs` (port 4010, still auto-merged through `docker-compose.override.yml` in this slice):
  `GET /oauth/authorize` remembers `nonce` and `client_id` per issued code and redirects to the given `redirect_uri`
  (`/callback`) with `client_id=oaiapp_stub_client` only when the request used `dynamic_agent_client`;
  `POST /oauth/token` answers with an RS256 `id_token` (`iss` `http://stub:4010`, `aud` = form `client_id`, the
  remembered `nonce`) signed by the key served at `GET /.well-known/jwks.json`; `POST /oauth/revoke` → 200.
  `docker-compose.override.yml` adds `ORACUL_CHATGPT_JWKS_URL=http://stub:4010/.well-known/jwks.json`,
  `ORACUL_CHATGPT_ISSUER=http://stub:4010`, `ORACUL_CHATGPT_REVOCATION_URL=http://stub:4010/oauth/revoke`.
- E2E reaches the callback through the web server: `http://127.0.0.1:4200/callback?…` (nginx exact location).
