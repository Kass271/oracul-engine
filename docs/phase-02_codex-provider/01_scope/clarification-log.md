# Clarification log — phase-02_codex-provider

Each round: the questions asked in one batch and the user's answers, verbatim where possible.
Defaults the user accepted are marked "(default accepted)".

Sources: the user's phase-02 decisions recorded in `oracul/RESUME-PROMPT.md` (answers quoted from there), and the
user's instruction this session "continue to the end autonomously" — remaining open points take the proposed default
and are marked "(default accepted)". Official docs read 2026-10-04: SIWC sign-in, profiles-and-sessions,
errors-and-recovery, models-and-inference, token-reference, preview-limitations.

## Round 1 — 2026-10-04

| # | Question | Answer |
|---|---|---|
| 1 | Users and roles — still one local user on their own computer, no ORACUL accounts? | Yes. "a personal ChatGPT Plus or Pro account"; "the browser runs on the same computer as ORACUL" (RESUME-PROMPT) |
| 2 | Main flow — how does the user get connected? | "after `docker compose up -d`, the user opens http://localhost:4200 and clicks "Continue with ChatGPT". They sign in on OpenAI's page (Google login is fine), confirm "Connect your ORACUL to ChatGPT" the first time, and land back with "ChatGPT connected" before clicking GENERATE." |
| 3 | Authorize request shape? | Match OpenAI's docs: "the `urn:uuid:` host id, a `/callback` redirect on `127.0.0.1`, and a `nonce` that is sent and checked in the ID token"; "Persist the issued `oaiapp_…` client id after the first registration, and send `agent_name_hint` only on the first registration." |
| 4 | Data — what is stored? | Host id (`urn:uuid:` form) and the issued client id are persisted; tokens stay runtime-only as in phase-01 FR-9/NFR-1 (default accepted). "The host id, the issued client id and the tokens are all handled by the app." |
| 5 | Redirect URI port — keep the frontend port 4200, i.e. `http://127.0.0.1:4200/callback`? | `http://127.0.0.1:4200/callback` (default accepted) |
| 6 | Reset — how does the user recover from a bad/stale registration? | "a "Reset ChatGPT connection" action in the UI, behind a confirmation, that clears the stored registration/client id and lets the user reconnect." |
| 7 | Error cases — which messages? | "`subscription_sharing_user_not_eligible` → "plan not eligible"; `subscription_sharing_usage_limit_exceeded` → "usage limit reached — try later", with a link to ChatGPT settings → Usage; refresh errors → "session expired — reconnect"." and "plain-language error messages with a next step for every documented OpenAI error, so the user never needs logs or files" |
| 8 | Responses call — follow the models-and-inference doc (`stream: true`, `store: false`, success only on `response.completed`, model chosen from `GET /v1/models` of the signed-in account)? | Yes — "check the Responses call against the docs" (default accepted for the concrete shape) |
| 9 | Structured output (`text.format` json_schema) is not documented for plan usage — if OpenAI rejects it as unsupported, fall back to JSON-by-instruction plus server-side validation? | Yes (default accepted) |
| 10 | Test vs real data? | "the E2E stub becomes opt-in (`docker-compose.e2e.yml`) with its own DB volume. A plain `docker compose up` must be real mode. The stub must follow the corrected, documented request shape." |
| 11 | Documentation? | "A comprehensive `apps/oracul-engine/README.md`": prerequisites, running for real, signing in step by step incl. the "Connect your ORACUL to ChatGPT" screen, running the tests, troubleshooting (`invalid_authorize_request`, not eligible, usage limit, ports, Docker disk, resetting registration/DB), stopping and resetting. |
| 12 | Real check? | "one manual real check early in the plan (right after the sign-in fix), and a final end-to-end check after the release … Report GREEN only after that." Acceptance: "on a fresh install, the UI sign-in alone leads to a generated real future with no config edits." |
| 13 | Plan order? | "prove the real path first, then implement … the first slice is the sign-in fix, followed by a real check by the user (sign in through the app plus one generation) before the other slices." |
| 14 | Out of scope? | "the Codex CLI route, API keys, other providers"; other devices / paste-back sign-in; persisting tokens across restarts (default accepted); phase rename (no factory command — name kept, default accepted). |

## Round 2 — 2026-10-04

| # | Question | Answer |
|---|---|---|
| 1 | Approve the phase-02 scope (FR-35..FR-43, NFR-8, NFR-9), or tell me what to change? | Approve scope |
| 2 | Pre-approve the plan if it follows the required order (sign-in slice first → real check by the user → remaining slices, about 2–3 slices)? | Pre-approve (Recommended) |

## Round 3 — 2026-10-04 (after real check 1)

| # | Question | Answer |
|---|---|---|
| 1 | Real check 1 showed real GDELT answering 429 "Please limit requests to one every 5 seconds" (and an 18 s answer), so every real run fails with NEWS_UNAVAILABLE. Add FR-44 "Real news search within GDELT's limits" (≤4 merged OR-group requests, one at a time ≥5 s apart, 30 s timeout, one retry after 429, partial results kept, inside the run budget), built in slice 02? | Add FR-44 to slice 02 (Rec.) |

## Round 4 — 2026-10-04 (before slice 03)

| # | Question | Answer |
|---|---|---|
| 1 | User request: "User should be able stop process of creating future and run new one. Also limit the sources not more than 30. Even though realism is make it impossible do it, but with notification in the end about realsim". Approve FR-45 (STOP + new run), FR-46 (≤30 sources), FR-47 (generate despite insufficient evidence for the Realism, end notice with LOWER REALISM; corrects FR-31), built in slice 03? | Approve as written (Rec.) |
| 2 | FR-47 edge case: zero usable evidence? | Still fail clearly (Rec.) — "No current news found — try again or change the scenario" |

## Round 5 — 2026-10-04

| # | Question | Answer |
|---|---|---|
| 1 | Correction of Round 4 #2 by the user: "if insufficent evedence it just do it!!! not stopped, just in the end make note about this" | FR-47 rewritten: a lack of evidence never stops a run — insufficient evidence for the Realism, zero evidence and no news at all (GDELT down) all still generate the future from what exists, with a note at the end. Corrects FR-31 and the NEWS_UNAVAILABLE run failure (FR-13/FR-44). |
