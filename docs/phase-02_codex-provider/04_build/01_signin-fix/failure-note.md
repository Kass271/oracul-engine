# Failure note — 01_signin-fix

**Superseded 2026-10-04 by the in-place recovery (round 4) — see rounds.md "Recovery". The slice closed DONE.**

Status: BLOCKED after 3 fix rounds · 2026-10-04

## What failed
Failing check: **e2e** (Playwright, chromium), which was still red after round 3 of 3.

Almost every E2E failure is the same assertion. The `chatgpt-status` element never reads "ChatGPT connected", so each flow that needs a connected ChatGPT account fails at its first step. One extra failure is a layout check: the page is 371 px wide on a 360 px mobile viewport.

Excerpt of the last output (the start of the excerpt is cut off in the source):

```
cted"
    attachment: e2e/test-results/chatgpt-connection-FR-7-Co-a6a12--a-snackbar-and-a-clean-URL-chromium/trace.zip
✘ critic.spec.ts › FR-22 a passing critic is called once between the scenario and the story and shows nothing · chromium
    Error: expect(locator).toHaveText(expected) failed
    Locator:  getByTestId('chatgpt-status')
    Expected: "ChatGPT connected"
    attachment: e2e/test-results/critic-FR-22-Critic-valida-e9fe4-the-story-and-shows-nothing-chromium/trace.zip
✘ custom-wildcards-output.spec.ts › connected run sends the custom wildcard and the fixed output · chromium
    Error: expect(locator).toHaveText(expected) failed
    Locator:  getByTestId('chatgpt-status')
    Expected: "ChatGPT connected"
    attachment: e2e/test-results/custom-wildcards-output-FR-bd497-ldcard-and-the-fixed-output-chromium/trace.zip
✘ events.spec.ts › the acceptance run produces 41 normalized, classified events · chromium
    Error: expect(locator).toHaveText(expected) failed
    Locator:  getByTestId('chatgpt-status')
    Expected: "ChatGPT connected"
    attachment: e2e/test-results/events-FR-14-FR-15-Event-n-1b109-ormalized-classified-events-chromium/trace.zip
✘ evidence-pack.spec.ts › mode evidence: 25 items with diversity caps, counter-signals and the exact prompt text · chromium
    Error: expect(locator).toHaveText(expected) failed
    Locator:  getByTestId('chatgpt-status')
    Expected: "ChatGPT connected"
    attachment: e2e/test-results/evidence-pack-FR-16-FR-17--6230e-s-and-the-exact-prompt-text-chromium/trace.zip
✘ future-story.spec.ts › FR-23 FR-25 the acceptance run ends in the labelled story with the metadata panel · chromium
    Error: expect(locator).toHaveText(expected) failed
    Locator:  getByTestId('chatgpt-status')
    Expected: "ChatGPT connected"
    attachment: e2e/test-results/future-story-FR-23-FR-25-F-04fb6-ory-with-the-metadata-panel-chromium/trace.zip
✘ insufficient-evidence.spec.ts › FR-31 Realism 10 with 2 core items ends in the insufficient view and LOWER REALISM starts a Realism 8 run · chromium
    Error: expect(locator).toHaveText(expected) failed
    Locator:  getByTestId('chatgpt-status')
    Expected: "ChatGPT connected"
    attachment: e2e/test-results/insufficient-evidence-FR-3-937a4-LISM-starts-a-Realism-8-run-chromium/trace.zip
✘ mobile-layout.spec.ts › 360 px is mobile · chromium
    Error: expect(received).toBeLessThanOrEqual(expected)
    Expected: <= 360
    Received:    371
    attachment: e2e/test-results/mobile-layout-FR-34-mobile-layout-360-px-is-mobile-chromium/trace.zip
✘ quick-regeneration.spec.ts › FR-29 quick DARKER starts a new run and the previous one stays in Recent futures · chromium
    Error: expect(locator).toHaveText(expected) failed
    Locator:  getByTestId('chatgpt-status')
    Expected: "ChatGPT connected"
    attachment: e2e/test-results/quick-regeneration-FR-29-q-2a86f-one-stays-in-Recent-futures-chromium/trace.zip
… 8 more failure(s) in the JSON report
==== END E2E FAILURES ====
```

Open review findings: none recorded. This slice has no `review-findings.json`.

## What each round tried
1. **Round 1 (trigger: verify RED) → builders.** FR-traceability passed for FR-35, FR-36, FR-37 and FR-41. The coverage ratchet failed with `INVALID backend 94.4% < baseline 94.6% — add tests, never lower the baseline` (frontend 100.0% ≥ 100.0%). The contract and artifact checks were OK.
2. **Round 2 (trigger: verify RED) → tester only.** The tester was asked to add unit tests to bring backend coverage back above the baseline: `ChatGptAuthServiceTest`, `ChatGptTokenClientTest`, `IdTokenValidatorTest`, `ChatGptPropertiesTest` (or an extension of `ChatGptStartupValidationTest`), `ChatGptCredentialStoreTest` and `SecretTest`, all under `backend/src/test/java/com/oracul/app/chatgpt/`. The recorded verify output still shows `INVALID backend 94.4% < baseline 94.6%`.
3. **Round 3 (trigger: E2E RED) → builders.** The E2E run failed as shown above: the `chatgpt-status` element never reached "ChatGPT connected" in the connected-flow specs, and `mobile-layout.spec.ts` measured 371 px against a 360 px limit. 8 more failures are listed only in the JSON report. No rounds were left.

## Not delivered
These requirements have no title in the sources this note may use, so each is listed with its tagged tests from `red-evidence.md`.
- FR-35: `ChatGptAuthorizeIT`, `ChatGptSignInIT`, `ChatGptStartupValidationTest`, `e2e/tests/chatgpt-connection.spec.ts`
- FR-36: `ChatGptCallbackIT`, `ChatGptIdTokenIT`, `ChatGptJwksUnreachableIT`, `ChatGptSignInIT`, `e2e/tests/chatgpt-connection.spec.ts`, `frontend/src/app/chatgpt/chatgpt-connection.spec.ts`, `frontend/src/app/chatgpt/connection.store.spec.ts`
- FR-37: `ChatGptResetIT`, `ChatGptRevocationTimeoutIT`, `ChatGptRevocationUnreachableIT`, `e2e/tests/chatgpt-connection.spec.ts`, `frontend/src/app/chatgpt/chatgpt-connection.spec.ts`
- FR-41: `e2e/tests/chatgpt-connection.spec.ts`, `frontend/src/app/chatgpt/chatgpt-connection.spec.ts`

## Impact
Dependent slices: the slice plan was not among the sources for this note, so they are not named here. The E2E output shows that a "ChatGPT connected" state comes first in the flows for FR-7, FR-14/15, FR-16/17, FR-22, FR-23/25, FR-29 and FR-31, and also in the custom-wildcards flow. Any slice that needs a working ChatGPT sign-in therefore depends on this one.
→ decision: STOP
