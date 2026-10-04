# Requirements — phase-02_codex-provider

Status: APPROVED ✔ 2026-10-04

Numbering continues across phases (next free ids come from `state.mjs next-fr`).
Format is parsed by the checks — keep the heading and bullet shapes exactly.

This phase makes the real "Continue with ChatGPT" path work end to end against OpenAI's documented Sign in with
ChatGPT (plan usage) flow. Sources, read 2026-10-04: developers.openai.com/siwc/token-sharing-open-source/ —
sign-in, profiles-and-sessions, errors-and-recovery, models-and-inference, token-reference, preview-limitations.
It corrects phase-01 FR-7, FR-8, FR-9 and NFR-6. Phase-01 NFR-1 (runtime-only tokens) and NFR-7 (stubbed automated
tests) stay in force.

## Functional

### FR-35 — Documented authorize request
- UI: yes
- Corrects: FR-7 (authorize request shape)
- Description: Clicking "Continue with ChatGPT" sends the browser to OpenAI's authorize endpoint with exactly the documented parameters, so OpenAI shows its "Connect your ORACUL to ChatGPT" screen instead of `invalid_authorize_request`.
- Acceptance:
  - Given no issued client id is stored, when I click "Continue with ChatGPT", then the browser goes to https://auth.openai.com/api/accounts/authorize with client_id=dynamic_agent_client, agent_name_hint=ORACUL, ext_agent_host_id=urn:uuid:<the stored host uuid>, response_type=code, redirect_uri=http://127.0.0.1:4200/callback, scope "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct", resource=https://api.openai.com/v1, a fresh state, a fresh nonce, code_challenge_method=S256 and a code_challenge
  - Given an issued client id oaiapp_X is stored, when I click "Continue with ChatGPT", then client_id=oaiapp_X is sent, agent_name_hint is absent, and ext_agent_host_id is the same urn:uuid value as before
  - Given two sign-in attempts, when I compare their requests, then state, nonce and code_challenge differ between them while ext_agent_host_id and redirect_uri are identical
  - Given the stored host id is a bare UUID from phase-01, when the authorize request is built, then it is sent as urn:uuid:<that uuid> (no new host id is generated)
  - Given any authorize request, when I inspect redirect_uri, then it never uses "localhost" and its path is exactly "/callback"

### FR-36 — Callback handling, nonce check and issued client id persistence
- UI: yes
- Corrects: FR-7 (callback), FR-9 (what is persisted)
- Description: OpenAI's redirect to http://127.0.0.1:4200/callback completes the sign-in: state is checked, the code is exchanged, the ID token is validated (including the nonce), the plan scope is checked, and the issued client id is persisted for later sign-ins.
- Acceptance:
  - Given OpenAI redirects to /callback with a code, the matching state and client_id=oaiapp_X on first registration, when the callback completes, then oaiapp_X is persisted with the host id, I land on http://localhost:4200 (or 127.0.0.1:4200) and the header shows "ChatGPT connected"
  - Given the token exchange, when it is sent, then it posts grant_type=authorization_code, code, client_id=<issued id>, code_verifier, the identical redirect_uri and resource=https://api.openai.com/v1 to https://auth.openai.com/api/accounts/oauth/token
  - Given the ID token's nonce differs from the nonce of this attempt, or its signature, iss, aud or exp is invalid, when the callback is processed, then I see "ChatGPT sign-in could not be verified — please try again" and I stay disconnected
  - Given a first-registration callback without an issued client_id, when it is processed, then nothing is persisted and I see "ChatGPT connection was not completed — please try again"
  - Given a reauthorization callback whose client_id differs from the stored issued id, when it is processed, then it is rejected with "ChatGPT connection was not completed — please try again" and the stored id is unchanged
  - Given the callback has a wrong or missing state, or error=access_denied, when it is processed, then I see "ChatGPT connection was not completed" and I stay disconnected
  - Given the code exchange returns invalid_grant, when it is processed, then the code is discarded and I see "Sign-in expired — click Continue with ChatGPT to start again"
  - Given the granted scopes lack chatgpt.tokens.use.direct, when the callback completes, then I see "Your ChatGPT plan is not eligible for ORACUL" and generation stays disabled
  - Given dynamic_agent_client, when anything is persisted, then dynamic_agent_client is never stored as the client id

### FR-37 — Reset ChatGPT connection
- UI: yes
- Description: A "Reset ChatGPT connection" action, behind a confirmation dialog, clears the stored registration (issued client id) and any runtime tokens so the user can register again — replacing any manual database edit.
- Acceptance:
  - Given a stored issued client id, when I click "Reset ChatGPT connection" and confirm, then the client id is cleared, I am disconnected, the header shows "Continue with ChatGPT" and the next sign-in is a first registration (client_id=dynamic_agent_client with agent_name_hint)
  - Given the confirmation dialog, when I click "Cancel", then nothing changes
  - Given a reset, when the next authorize request is built, then the host id is the same urn:uuid value as before the reset
  - Given a generation run is in progress, when I try to reset, then I see "Wait until the current run finishes" and nothing is cleared

### FR-38 — Documented plan-usage Responses call
- UI: no
- Corrects: FR-19/FR-20 request transport (model choice, streaming)
- Description: Every generation call uses POST https://api.openai.com/v1/responses with the access token, store=false and stream=true, a model taken from the signed-in account's GET /v1/models catalog, and counts as successful only after response.completed.
- Acceptance:
  - Given a connected user, when a run calls the model, then the request is POST /v1/responses with "Authorization: Bearer <access token>", store=false, stream=true and a model slug that GET /v1/models returned for this account
  - Given the configured preferred model is not in the account's catalog, when a run starts, then ORACUL uses an available model from the catalog and records the model actually used in the scenario metadata
  - Given the stream ends with response.incomplete or response.failed, or without response.completed, when the run processes it, then the stage fails with "ChatGPT did not finish the answer — please try again" and no partial story is shown
  - Given OpenAI rejects text.format json_schema with subscription_sharing_unsupported_capability, when the call is made, then ORACUL repeats it once without text.format, asking for JSON in the instructions, and validates the JSON on the server; invalid JSON fails the run with a clear message
  - Given the access token is within 5 minutes of expiry or expired, when a call is made, then ORACUL refreshes it first (grant_type=refresh_token with the stored client id) and replaces both tokens

### FR-39 — Plain-language messages for documented OpenAI errors
- UI: yes
- Corrects: FR-8, FR-32 (error states and messages)
- Description: Every documented OpenAI plan-usage, authorization and refresh error is shown as a plain-language message with a next step, so the user never needs logs or files.
- Acceptance:
  - Given a call returns 403 subscription_sharing_user_not_eligible, when the run fails, then I see "Your ChatGPT plan is not eligible for ORACUL — a personal Plus or Pro plan is needed" and ORACUL neither retries nor restarts sign-in
  - Given a call returns 429 subscription_sharing_usage_limit_exceeded, when the run fails, then I see "ChatGPT usage limit reached — try again later" with a link "Open ChatGPT Settings → Usage" to https://chatgpt.com/#settings/Usage
  - Given a call returns 503 subscription_sharing_usage_unavailable or subscription_sharing_user_unavailable, when the run handles it, then it retries at most 2 times with backoff and then shows "ChatGPT is temporarily unavailable — try again in a few minutes"
  - Given a call returns subscription_sharing_unsupported_capability (after the FR-38 fallback) or subscription_sharing_route_not_supported, when the run fails, then I see "ChatGPT rejected ORACUL's request — please report this" with the error code shown
  - Given a call returns 401 or subscription_sharing_invalid_user, chatpass_v2_scope_not_authorized or chatpass_v2_invalid_authorization_context, when the run fails, then tokens are cleared and I see "ChatGPT session expired — please reconnect" with a "Continue with ChatGPT" button
  - Given an unknown OpenAI error code, when the run fails, then I see "ChatGPT returned an unexpected error (<code>) — please try again" and never a stack trace or a token

### FR-40 — Refresh failures end the session cleanly
- UI: yes
- Corrects: FR-8 (session expired)
- Description: When a refresh fails with any documented refresh error, ORACUL clears the tokens (keeping the host id and issued client id) and asks the user to reconnect.
- Acceptance:
  - Given refresh returns invalid_grant, invalid_refresh_token, token_expired, refresh_token_expired, refresh_token_invalidated or refresh_token_reused, when a run needs a token, then tokens are cleared, no run is started and I see "ChatGPT session expired — please reconnect"
  - Given refresh returns invalid_client, when a run needs a token, then I see "ChatGPT registration is no longer valid — use Reset ChatGPT connection, then reconnect"
  - Given a session expired, when I click "Continue with ChatGPT", then the authorize request uses the stored issued client id (reauthorization, no agent_name_hint)

### FR-41 — Sign-in conditions shown in the UI
- UI: yes
- Description: Near "Continue with ChatGPT" the UI states the conditions for signing in, so the user knows before they start.
- Acceptance:
  - Given I am not connected, when I look at the sign-in area, then I see that a personal ChatGPT Plus or Pro account is needed and that the browser must run on the same computer as ORACUL
  - Given I am not connected, when I click "Continue with ChatGPT", then no ORACUL screen asks for a password, API key or any configuration value

### FR-42 — Opt-in E2E stub with its own data
- UI: no
- Description: A plain `docker compose up -d` starts ORACUL in real mode; the OpenAI/news stub runs only through an explicit `docker-compose.e2e.yml` with its own database volume, and the stub enforces the documented request shape.
- Acceptance:
  - Given the app folder, when `docker compose up -d` is run with no -f flag, then no stub service starts and the backend uses https://auth.openai.com and https://api.openai.com
  - Given `docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d`, when it runs, then the stub starts and the database uses a volume that is different from the real-mode volume
  - Given a stub sign-in, when the stub receives an authorize request with a bare-UUID host id, a redirect path other than /callback, a missing nonce, or agent_name_hint together with an issued client id, then it answers error=invalid_authorize_request
  - Given a stub Responses call without stream=true and store=false, when the stub receives it, then it answers HTTP 400
  - Given E2E runs, when they finish, then the real-mode database contains no stub client id

### FR-43 — Comprehensive README
- UI: no
- Description: apps/oracul-engine/README.md explains how to run ORACUL for real and how to make it work.
- Acceptance:
  - Given the README, when I read it, then it has sections: Prerequisites (personal ChatGPT Plus or Pro account, Docker), Run for real, Sign in step by step (including what the "Connect your ORACUL to ChatGPT" screen is), Run the tests, Troubleshooting, Stop and reset
  - Given the Troubleshooting section, when I look for invalid_authorize_request, plan not eligible, usage limit, ports 4200/8080 in use, Docker disk full, and resetting the registration or database, then each has a cause and a fix
  - Given the README's commands, when I run them as written on a fresh clone, then they work without editing any file, env var, database row or API key

### FR-44 — Real news search within GDELT's limits
- UI: no
- Corrects: FR-13 (search transport against the real GDELT DOC API)
- Description: The current-news search works against the real GDELT DOC 2.0 API, which allows one request every 5 seconds and often answers slowly, so a real run reaches the scenario stages instead of failing with NEWS_UNAVAILABLE. Verified 2026-10-04 with a real call: HTTP 429 "Please limit requests to one every 5 seconds", a successful answer took 18 s.
- Acceptance:
  - Given a run's search plan, when the search stage runs, then the planned queries are merged into at most 4 GDELT requests (OR-groups) and the plan's per-query attribution of sources is kept
  - Given several GDELT requests, when they are sent, then they go out one at a time with at least 5 seconds between the starts of two requests, and each waits up to 30 seconds for an answer
  - Given GDELT answers 429, when the request is handled, then ORACUL waits 5 seconds and retries that request once
  - Given some GDELT requests fail and others return sources, when the stage ends, then the run continues with the sources it got; only when no request returns any source does the run fail with "ORACUL could not reach its news sources — try again later"
  - Given the whole search, when it runs, then it finishes within the run's time budget so the later stages still have time to complete

### FR-45 — Stop a generation and start a new one
- UI: yes
- Description: While a future is being generated the user can stop it and then start a new one, instead of waiting for a slow run to end.
- Acceptance:
  - Given a run is in progress, when I look at the generate button, then it reads "STOP" instead of "GENERATE"
  - Given a run is in progress, when I click "STOP", then the run ends with status "Stopped", ORACUL sends no further ChatGPT or news requests for it, and the controls are enabled again
  - Given a stopped run, when I click "GENERATE", then a new run starts with the current settings
  - Given a stopped run, when I open Recent futures, then it is listed as "Stopped"
  - Given a run that has already finished, when a stop request for it arrives, then nothing changes and the UI shows the run's final state
  - Given a run was stopped, when I use "Reset ChatGPT connection", then the reset is allowed

### FR-46 — At most 30 sources per run
- UI: no
- Corrects: FR-13 (number of kept sources)
- Description: A run keeps at most 30 news sources, so generation is faster and the evidence is focused.
- Acceptance:
  - Given the news search returns more than 30 usable sources, when retrieval ends, then at most 30 are kept: the best-ranked ones, keeping the spread over search topics and each source's query attribution
  - Given any run, when I read its counts or the WHY THESE NEWS panel, then the number of kept sources is never above 30
  - Given the search returns 30 or fewer usable sources, when retrieval ends, then all of them are kept

### FR-47 — Always generate; note insufficient evidence at the end
- UI: yes
- Corrects: FR-31 (insufficient evidence no longer ends the run), FR-13 / FR-44 (no news found no longer ends the run with NEWS_UNAVAILABLE)
- Description: A lack of evidence never stops a run. If the evidence is below what the chosen Realism needs, or the news search finds nothing at all, ORACUL still generates the future from whatever evidence it has (never inventing evidence or sources) and shows a note at the end.
- Acceptance:
  - Given Realism 10 and only 2 core evidence items (5 needed), when the run evaluates evidence, then the run continues and ends COMPLETED with a story built only from the available evidence
  - Given such a run, when the result is shown, then I see the note "Realism 10 couldn't be fully met: only 2 core evidence items (needs 5). This future is less grounded." with a "LOWER REALISM" button
  - Given the news search returns no usable sources (including GDELT refusing or timing out on every request), when the run continues, then it ends COMPLETED with a fully speculative future, the SOURCES view is empty, and the note reads "No current news could be used — this future is speculative, not grounded in evidence."
  - Given I click "LOWER REALISM" in the note, when it applies, then Realism decreases by 2 and a new run starts
  - Given the evidence meets the chosen Realism, when the result is shown, then no note appears

### FR-48 — Google News RSS as the main news source, GDELT as fallback
- UI: no
- Corrects: FR-13 / FR-44 (news provider)
- Description: Real GDELT refused most requests on 2026-10-04 (429 "one request every 5 seconds", 10–18 s per answer), so real runs found no news. ORACUL searches Google News RSS first (no key, no configuration; a real call answered 100 items in 0.6 s) and uses GDELT only as a fallback.
- Acceptance:
  - Given a run's search plan, when the search runs, then the planned queries are merged into at most 4 OR-group requests to https://news.google.com/rss/search (q with the OR-group plus a "when:" date limit, hl=en-US, gl=US, ceid=US:en), sent one at a time at least 1 second apart, each with a 10 second timeout
  - Given an RSS item, when it becomes a source, then it keeps title, publisher (the item's source), publication date (pubDate) and link; when the article page is fetched, the redirect is followed to the publisher's URL, and if that fails the source keeps the Google link and the publisher's site URL
  - Given a Google News request fails (non-200, timeout or unparsable XML), when the search continues, then that group is tried once against GDELT under the FR-44 rules, within the remaining search budget
  - Given a query whose group was never sent (budget or run deadline), when the search ends, then its status is FAILED (never EMPTY), so the counts and the FR-47 note tell the truth
  - Given the E2E stack, when tests run, then the stub serves a Google News RSS search endpoint with control modes (ok, empty, down, malformed) and no test calls the real Google

## Non-functional

### NFR-8 — Real-service check gates GREEN
- Description: Stub tests (NFR-7) are not enough: the real OpenAI path is checked by hand twice — right after the sign-in slice and after the release — on the user's real ChatGPT account. The phase is GREEN only after the final check.
- Acceptance:
  - Check 1 (after the sign-in slice): the user signs in through the ORACUL UI, OpenAI shows "Connect your ORACUL to ChatGPT", ORACUL shows "ChatGPT connected" and one generation returns real text (not stub lorem); evidence written to `04_build/<slice>/real-check.md`
  - Check 2 (after release, fresh install): from `docker compose up -d` with an empty registration, the UI sign-in alone leads to a generated real future with no config edits; evidence in `05_release/real-check.md`

### NFR-9 — Loopback redirect on /callback
- Description: Corrects NFR-6: ORACUL runs locally via Docker Compose and the OAuth redirect is exactly http://127.0.0.1:4200/callback (scheme, host and path constant; never localhost).
- Acceptance:
  - Backend tests assert the redirect_uri of every authorize and token request; the stub rejects any other path

## Out of scope
- The Codex CLI route (installed codex-cli is only a manual fallback, not part of ORACUL)
- OpenAI API keys; providers other than OpenAI/ChatGPT
- Signing in from another device, or pasting a callback URL back by hand
- Persisting tokens across backend restarts (tokens stay runtime-only — reconnect after restart)
- Multiple ChatGPT accounts / profile switching
- Renaming the phase folder (no factory command; the name is historic)
- Fixing phase-01 low findings (request-log Basic header redaction, SSRF via article redirects) — listed for a later phase
