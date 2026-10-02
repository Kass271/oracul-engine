# Clarification log — phase-01_mvp

Each round: the questions asked in one batch and the user's answers, verbatim where possible.
Defaults the user accepted are marked "(default accepted)".

Context gathered before Round 1 (spec §48.3–4): OpenAI officially supports "Sign in with ChatGPT" for open-source /
locally hosted apps — OAuth 2.0 + PKCE at `auth.openai.com`, self-serve dynamic client registration
(`client_id=dynamic_agent_client` → issued `oaiapp_…` client id), no client secret, no API key, loopback redirect on
`127.0.0.1` only, scopes `openid profile email offline_access resource.invoke chatgpt.tokens.use.direct`,
resource `https://api.openai.com/v1`, eligible for Responses API requests on Plus/Pro plans.
Conflicts with the spec identified: (a) OpenAI docs suggest persisting the rotating refresh token in a 0600 file,
spec §6 prefers runtime-only; (b) hosted/paid deployment requires OpenAI approval (interest form).
Sources: https://developers.openai.com/siwc/token-sharing-open-source ,
https://developers.openai.com/siwc/token-sharing-open-source/sign-in

## Round 1 — 2026-10-02

| # | Question | Answer |
|---|---|---|
| 1 | Auth & credentials: official Sign in with ChatGPT; access/refresh/id tokens only in backend memory (re-sign-in after backend restart); persist only the non-secret issued client_id and host id; no API-key fallback; no ORACUL user accounts (single local user); "plan not eligible" message if `chatgpt.tokens.use.direct` is not granted. | As proposed (default accepted) |
| 2 | News source: GDELT DOC 2.0 API (no key) + short-timeout fetch of article metadata; failures keep GDELT metadata; provider pluggable. | As proposed (default accepted) |
| 3 | ChatGPT also used for ORACUL-side research steps (query expansion, normalization/dedup, semantic classification) as separate tool-less calls with untrusted content isolated; generation, guard/critic and story are separate tool-less calls; ~6–8 calls/run on one configurable model. | As proposed (default accepted) |
| 4 | Session data & limits: PostgreSQL stores runs, evidence packs, sources, scenarios keyed by anonymous browser session id, never credentials; "Recent futures" last 20; one active run per session; progress polled ~1 s; 3-minute run budget; custom wildcards ≤40 chars, intensity 1–10, max 3. | As proposed (default accepted) |
| 5 | NFR & out of scope: English only; desktop-first, mobile drawer; automated tests incl. E2E use stubbed OpenAI and GDELT; out of scope = spec §44 + Illustration (toggle disabled, "MVP+1") + hosted deployment + Presets/Replay/Future Tree. | As proposed (default accepted) |

User reply: "defaults ok"
