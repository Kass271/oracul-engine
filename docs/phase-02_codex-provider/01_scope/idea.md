# Idea — phase-02_codex-provider

Received: 2026-10-04 (rewritten 2026-10-04, second session — the Codex-CLI direction below is superseded)

> continue — first read @RESUME-PROMPT.md and follow it (factory was fixed; re-read SKILL.md, clarify skill and workflows, then resume phase-02 at 01_scope)

> continue to the end autonomously

The user's decision for this phase, verbatim from RESUME-PROMPT.md ("Phase-02 goal (new, smaller scope, about 1.5–3 h)" … "Order — the user insists on this"):

> **Phase-02 goal (new, smaller scope, about 1.5–3 h)**
> 1. **Fix the sign-in to match OpenAI's docs:** the `urn:uuid:` host id, a `/callback` redirect on `127.0.0.1`, and a `nonce` that is sent and checked in the ID token.
>    - Persist the issued `oaiapp_…` client id after the first registration, and send `agent_name_hint` only on the first registration.
>    - Read the docs pages first, every time: sign-in, profiles-and-sessions, errors-and-recovery.
> 2. **Real plan-usage calls:** check the Responses call against the docs and map the documented errors to clear UI messages:
>    - `subscription_sharing_user_not_eligible` → "plan not eligible";
>    - `subscription_sharing_usage_limit_exceeded` → "usage limit reached — try later", with a link to ChatGPT settings → Usage;
>    - refresh errors → "session expired — reconnect".
> 3. **Keep test and real data apart:** the E2E stub becomes opt-in (`docker-compose.e2e.yml`) with its own DB volume. A plain `docker compose up` must be real mode. The stub must follow the corrected, documented request shape.
> 4. **A comprehensive `apps/oracul-engine/README.md`:**
>    - prerequisites (a ChatGPT Plus or Pro personal account);
>    - running for real;
>    - signing in step by step, including what the "Connect your ORACUL to ChatGPT" screen is;
>    - running the tests;
>    - troubleshooting: `invalid_authorize_request`, not eligible, usage limit, ports, Docker disk, resetting the registration/DB;
>    - stopping and resetting.
> 5. **Real check:** one manual real check early in the plan (right after the sign-in fix), and a final end-to-end check after the release. The user signs in through the app and generates one future, and you confirm it is real text, not stub lorem. Report GREEN only after that.
> 
> 6. **Sign-in entirely through the UI, with no config edits (user requirement):**
>    - **Flow:** after `docker compose up -d`, the user opens http://localhost:4200 and clicks "Continue with ChatGPT". They sign in on OpenAI's page (Google login is fine), confirm "Connect your ORACUL to ChatGPT" the first time, and land back with "ChatGPT connected" before clicking GENERATE.
>    - **No configuration:** the user never edits a file, env var, DB row or API key. The host id, the issued client id and the tokens are all handled by the app.
>    - **Conditions** (write them into the README and the UI hints):
>      - a personal ChatGPT Plus or Pro account (Free or work accounts may be refused, so show "plan not eligible" clearly);
>      - the browser runs on the same computer as ORACUL, because OpenAI only redirects to `127.0.0.1` (other devices / paste-back are out of scope);
>      - a plain `docker compose up -d` starts real mode.
>    - **Add these to the scope:**
>      - a "Reset ChatGPT connection" action in the UI, behind a confirmation, that clears the stored registration/client id and lets the user reconnect. This replaces any manual DB edit, like the stale stub client id that had to be cleared by hand.
>      - plain-language error messages with a next step for every documented OpenAI error, so the user never needs logs or files;
>      - an acceptance criterion: on a fresh install, the UI sign-in alone leads to a generated real future with no config edits. Verify it in the final real check.
> 
> **Order — the user insists on this:** prove the real path first, then implement. The sign-in itself is already proven by the hand-built request. In the plan, the first slice is the sign-in fix, followed by a real check by the user (sign in through the app plus one generation) before the other slices.
> 

Note on the phase name: `phase-02_codex-provider` is historic — the Codex CLI route is dropped (fallback only, out of
scope). `state.mjs` has no phase-rename command, so the name is kept unchanged.

## Superseded first version of this idea (kept for history)

> yes. start phase-02 in the end of phase provide a comprehensive read.md in app how to run app and make it work

Context from the same conversation (user's words, verbatim, in order):

> open api key means i will spend tokens in API? i need to use subscription not api

> ok install codex by u self

> i did it. why then all u test was passed??? if the login does not work??????

> yes. start phase-02 in the end of phase provide a comprehensive read.md in app how to run app and make it work

Agreed direction (proposed by the assistant, accepted with "yes"): replace phase-01's "Continue with ChatGPT" sign-in (FR-7/FR-8 — rejected by the real OpenAI: `invalid_authorize_request`, param `ext_agent_host_id`) with generation through the OpenAI Codex CLI (`codex exec`) logged in with the user's ChatGPT subscription; the header shows the Codex login state; one real Codex check early in the plan, not only stubs.

Verified spike (2026-10-04, before this phase): codex-cli 0.160.0 installed via npm, `codex login status` → "Logged in using ChatGPT"; one-line prompt answered in 6 s; a structured evidence → JSON scenario prompt answered in 18 s with valid JSON (headline, date, paragraphs citing [E1]/[E2], whyChain).
