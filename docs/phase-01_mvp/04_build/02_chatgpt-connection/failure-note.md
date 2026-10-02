> **Superseded:** the slice was recovered in place and closed DONE after round 6. See rounds.md, "Recovery (round 6)".

# Failure note — 02_chatgpt-connection

Status: BLOCKED after 5 fix rounds · 2026-10-02

## What failed
The independent review is still not clean after round 5 of 5. One high finding is still open and blocks the slice.

```
== review evidence ==
INVALID  slice 02_chatgpt-connection: 1 open high/medium finding(s): R2
RESULT  FAIL (1 problem)
```

- **R2 (high, tests, FR-7; open since round 1).** File: `e2e/tests/chatgpt-connection.spec.ts:4`.
  - The E2E spec needs a stub service at `http://localhost:4010`, with the endpoints `/__control/mode`, `/__control/reset`, `/__control/issued`, `/oauth/authorize` and `/oauth/token`.
  - The spec requires two files for this: `e2e/stubs/` (a Node stub) and `docker-compose.override.yml`. The override wires in the stub and sets `ORACUL_CHATGPT_AUTHORIZE_URL` and `ORACUL_CHATGPT_TOKEN_URL` on the backend.
  - Neither file exists. `e2e/` contains only `tests/`, the Playwright config and the package files, and nothing in the app listens on port 4010.
  - As a result, the `POST /__control/reset` in each test's `beforeEach` fails, and none of the FR-7, FR-8 or FR-9 E2E acceptance tests can run.
  - Without the override, the backend would also call the real auth.openai.com, which breaks NFR-7.
  - Fix still needed: add `e2e/stubs/` (no npm dependencies, port 4010, endpoints and modes exactly as in the spec table). Add `docker-compose.override.yml` with:
    - a service named `stub` that maps `4010:4010`
    - backend env `ORACUL_CHATGPT_AUTHORIZE_URL=http://localhost:4010/oauth/authorize`
    - backend env `ORACUL_CHATGPT_TOKEN_URL=http://stub:4010/oauth/token`
- **R8 (low, tests, FR-7; still open, does not block).** No unit test covers the startup branch in `App.ngOnInit` that reads `window.location.search` when `router.navigated` is false. That is the branch the real app uses when it opens with `?chatgpt=` in the URL. Right now only the E2E spec covers it, and the E2E spec cannot run until R2 is fixed.

Findings file: `review-findings.json` in this folder.

## What each round tried
1. Review found 5 open high/medium findings: R1, R2, R3, R4, R5. The fixes for this round closed four of them:
   - R1: the `/auth/callback` forward was added to nginx and to the dev proxy.
   - R3: the `?chatgpt=` value is now read after the initial navigation, or from `window.location.search` at startup.
   - R4: expired pending authorizations are now purged.
   - R5: `SessionFilter` errors are now handled.
   - R2 was not fixed.
2. Review: 1 open high/medium finding (R2). The stub and the override were still missing. The low findings R6 (disconnect racing with refresh) and R7 (the `locks` map grows without limit) are marked fixed. R8 (low) was raised about the new startup branch from the R3 fix.
3. Review: 1 open high/medium finding (R2). `e2e/stubs/` and `docker-compose.override.yml` were still missing, and nothing served port 4010. R8 was still open.
4. Review: 1 open high/medium finding (R2). Still missing; only `docker-compose.yml` exists. R8 was still open.
5. Review: 1 open high/medium finding (R2). No fix had been made since round 4: `app.ts` was unchanged, and `e2e/stubs/` and `docker-compose.override.yml` still did not exist. R8 was still open.

## Not delivered
- FR-7: ChatGPT sign-in through OpenAI. The end-to-end acceptance tests cannot run, and the startup return path is not covered by a unit test (R8).
- FR-8: ChatGPT connection state per browser session, including disconnect. The end-to-end acceptance tests cannot run.
- FR-9: ChatGPT credential handling. The end-to-end acceptance tests cannot run, and NFR-7 (no calls to the real auth.openai.com during tests) is not ensured without the override.

## Impact
Dependent slices: the provided evidence does not list them. Any slice that needs a usable ChatGPT connection (`canGenerate`) or the E2E stub infrastructure depends on this slice. → decision: STOP
