# Real check 1 (NFR-8) — 01_signin-fix

Date: 2026-10-04 · Stack: real mode (`docker compose -f docker-compose.yml up -d`, no stub) · Account: the user's personal ChatGPT account

| Step | Result | Evidence |
|---|---|---|
| First attempt with the stub client id left in the shared DB (`oaiapp_stub_client`, leaked by E2E — fixed by FR-42) | ✘ OpenAI: "We couldn't verify whether this account is eligible for this app." | authorize redirect showed `client_id=oaiapp_stub_client` |
| "Reset ChatGPT connection" in the UI (FR-37) | ✔ registration cleared through the UI, no DB edit | next authorize used `client_id=dynamic_agent_client` + `agent_name_hint=ORACUL` |
| Continue with ChatGPT → OpenAI consent → back to ORACUL | ✔ user: "yes! it starts working" | DB `chatgpt_client_registration`: host `aa7e2bc7-…` (sent as `urn:uuid:…`), issued `client_id=oaiapp_zk0Mv48CdvDPFwGPsEb4PAXW` at 2026-10-04 09:24:44Z |
| Authorize request shape | ✔ `urn:uuid:` host id, `redirect_uri=http://127.0.0.1:4200/callback`, nonce, S256, `resource=https://api.openai.com/v1`, six scopes | captured 302 Location |
| One generation (run 69acb851-…) | ✘ FAILED at SEARCHING, `NEWS_UNAVAILABLE` | backend log: `responses call failed: status=400` (no stream=true / fixed model — slice 02, FR-38) and GDELT timeouts + `429 "Please limit requests to one every 5 seconds"` (new FR-44, slice 02) |

Verdict: the sign-in fix (FR-35, FR-36, FR-37) is proven against the real OpenAI. Real generation is not yet proven; it is the goal of slice 02 (FR-38..FR-40, FR-44) and of real check 2.
