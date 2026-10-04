
## Round 1
- Trigger: verify RED
- To builders: yes · to tester: no
- Output:

```
nIT.java, backend/src/test/java/com/oracul/app/chatgpt/ChatGptStartupValidationTest.java, e2e/tests/chatgpt-connection.spec.ts
PASS     FR-36 → backend/src/test/java/com/oracul/app/chatgpt/ChatGptCallbackIT.java, backend/src/test/java/com/oracul/app/chatgpt/ChatGptIdTokenIT.java, backend/src/test/java/com/oracul/app/chatgpt/ChatGptJwksUnreachableIT.java, backend/src/test/java/com/oracul/app/chatgpt/ChatGptSignInIT.java, e2e/tests/chatgpt-connection.spec.ts, frontend/src/app/chatgpt/chatgpt-connection.spec.ts, frontend/src/app/chatgpt/connection.store.spec.ts
PASS     FR-37 → backend/src/test/java/com/oracul/app/chatgpt/ChatGptResetIT.java, backend/src/test/java/com/oracul/app/chatgpt/ChatGptRevocationTimeoutIT.java, backend/src/test/java/com/oracul/app/chatgpt/ChatGptRevocationUnreachableIT.java, e2e/tests/chatgpt-connection.spec.ts, frontend/src/app/chatgpt/chatgpt-connection.spec.ts
PASS     FR-41 → e2e/tests/chatgpt-connection.spec.ts, frontend/src/app/chatgpt/chatgpt-connection.spec.ts
SKIP     5 FR(s) belong to slices not built yet
RESULT  OK

== coverage ratchet ==
INVALID  backend 94.4% < baseline 94.6% — add tests, never lower the baseline
PASS     frontend 100.0% ≥ baseline 100.0%
RESULT  FAIL (1 problem)

== contract ==
PASS     openapi.yaml valid (18 operations)
PASS     backend generates interfaces from openapi.yaml before compile
PASS     frontend generates the client from openapi.yaml before build/test/build/start
PASS     backend generated code is up to date
PASS     frontend generated code is up to date
RESULT  OK

== review evidence ==
SKIP     no finished slices yet
RESULT  OK

== artifacts 00_setup ==
PASS     docs/phase-02_codex-provider/00_setup/adr-001-stack.md [contains:Spring Boot]
PASS     docs/phase-02_codex-provider/00_setup/environment-check.md [contains:Result: OK]
PASS     api/openapi.yaml [openapi]
PASS     docker-compose.yml [contains:services:]
PASS     backend/build.gradle.kts [contains:openApiGenerate]
PASS     frontend/package.json [contains:@angular/material]
PASS     e2e/playwright.config.ts [nonEmpty]
RESULT  OK

== artifacts 01_scope ==
PASS     docs/phase-02_codex-provider/01_scope/idea.md [nonEmpty]
PASS     docs/phase-02_codex-provider/01_scope/clarification-log.md [regex:^##\s*Round\s+\d+]
PASS     docs/phase-02_codex-provider/01_scope/requirements.md [requirements]
PASS     docs/phase-02_codex-provider/01_scope/requirements.md [approved]
RESULT  OK

== artifacts 02_specs ==
PASS     docs/phase-02_codex-provider/02_specs/*.md [minFiles:2] 4 file(s)
PASS     docs/phase-02_codex-provider/02_specs/contract-notes.md [nonEmpty]
PASS     docs/phase-02_codex-provider/02_specs [specsCoverFrs]
PASS     api/openapi.yaml [openapi]
RESULT  OK

== artifacts 03_plan ==
PASS     docs/phase-02_codex-provider/03_plan/plan.md [planCoversFrs]
PASS     docs/phase-02_codex-provider/03_plan/plan.md [approved]
RESULT  OK

== artifacts 04_build ==
SKIP     no finished slices yet
RESULT  OK

==== VERIFY RED: check-coverage ====
```

## Round 2
- Trigger: verify RED
- To builders: no · to tester: backend/src/test/java/com/oracul/app/chatgpt/ChatGptAuthServiceTest.java (new), backend/src/test/java/com/oracul/app/chatgpt/ChatGptTokenClientTest.java (new), backend/src/test/java/com/oracul/app/chatgpt/IdTokenValidatorTest.java (new), backend/src/test/java/com/oracul/app/chatgpt/ChatGptPropertiesTest.java (new, or extend ChatGptStartupValidationTest.java), backend/src/test/java/com/oracul/app/chatgpt/ChatGptCredentialStoreTest.java (new), backend/src/test/java/com/oracul/app/chatgpt/SecretTest.java (new)
- Output:

```
ava, backend/src/test/java/com/oracul/app/chatgpt/ChatGptStartupValidationTest.java, e2e/tests/chatgpt-connection.spec.ts
PASS     FR-36 → backend/src/test/java/com/oracul/app/chatgpt/ChatGptCallbackIT.java, backend/src/test/java/com/oracul/app/chatgpt/ChatGptIdTokenIT.java, backend/src/test/java/com/oracul/app/chatgpt/ChatGptJwksUnreachableIT.java, backend/src/test/java/com/oracul/app/chatgpt/ChatGptSignInIT.java, e2e/tests/chatgpt-connection.spec.ts, frontend/src/app/chatgpt/chatgpt-connection.spec.ts, frontend/src/app/chatgpt/connection.store.spec.ts
PASS     FR-37 → backend/src/test/java/com/oracul/app/chatgpt/ChatGptResetIT.java, backend/src/test/java/com/oracul/app/chatgpt/ChatGptRevocationTimeoutIT.java, backend/src/test/java/com/oracul/app/chatgpt/ChatGptRevocationUnreachableIT.java, e2e/tests/chatgpt-connection.spec.ts, frontend/src/app/chatgpt/chatgpt-connection.spec.ts
PASS     FR-41 → e2e/tests/chatgpt-connection.spec.ts, frontend/src/app/chatgpt/chatgpt-connection.spec.ts
SKIP     5 FR(s) belong to slices not built yet
RESULT  OK

== coverage ratchet ==
INVALID  backend 94.4% < baseline 94.6% — add tests, never lower the baseline
PASS     frontend 100.0% ≥ baseline 100.0%
RESULT  FAIL (1 problem)

== contract ==
PASS     openapi.yaml valid (18 operations)
PASS     backend generates interfaces from openapi.yaml before compile
PASS     frontend generates the client from openapi.yaml before build/test/build/test/start
PASS     backend generated code is up to date
PASS     frontend generated code is up to date
RESULT  OK

== review evidence ==
SKIP     no finished slices yet
RESULT  OK

== artifacts 00_setup ==
PASS     docs/phase-02_codex-provider/00_setup/adr-001-stack.md [contains:Spring Boot]
PASS     docs/phase-02_codex-provider/00_setup/environment-check.md [contains:Result: OK]
PASS     api/openapi.yaml [openapi]
PASS     docker-compose.yml [contains:services:]
PASS     backend/build.gradle.kts [contains:openApiGenerate]
PASS     frontend/package.json [contains:@angular/material]
PASS     e2e/playwright.config.ts [nonEmpty]
RESULT  OK

== artifacts 01_scope ==
PASS     docs/phase-02_codex-provider/01_scope/idea.md [nonEmpty]
PASS     docs/phase-02_codex-provider/01_scope/clarification-log.md [regex:^##\s*Round\s+\d+]
PASS     docs/phase-02_codex-provider/01_scope/requirements.md [requirements]
PASS     docs/phase-02_codex-provider/01_scope/requirements.md [approved]
RESULT  OK

== artifacts 02_specs ==
PASS     docs/phase-02_codex-provider/02_specs/*.md [minFiles:2] 4 file(s)
PASS     docs/phase-02_codex-provider/02_specs/contract-notes.md [nonEmpty]
PASS     docs/phase-02_codex-provider/02_specs [specsCoverFrs]
PASS     api/openapi.yaml [openapi]
RESULT  OK

== artifacts 03_plan ==
PASS     docs/phase-02_codex-provider/03_plan/plan.md [planCoversFrs]
PASS     docs/phase-02_codex-provider/03_plan/plan.md [approved]
RESULT  OK

== artifacts 04_build ==
SKIP     no finished slices yet
RESULT  OK

==== VERIFY RED: check-coverage ====
```

## Round 3
- Trigger: E2E RED
- To builders: yes · to tester: no
- Output:

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

## Recovery — round 4 (in place, main session, user-approved 2026-10-04)
- Cause of the BLOCK: the Docker E2E stub path was never moved to the new flow (nginx/proxy still `/auth/callback`; override lacked JWKS/issuer/revocation URLs; `e2e/stubs/server.mjs` had no RS256 id_token/nonce/JWKS/revoke) plus a 371 px header at 360 px.
- backend-builder: `e2e/stubs/server.mjs` (documented authorize validation, issued client id, RS256 id_token with nonce, /jwks, /oauth/revoke), `docker-compose.override.yml` (ORACUL_CHATGPT_JWKS_URL / ISSUER / REVOCATION_URL).
- frontend-builder: `frontend/nginx.conf` + `proxy.conf.json` route `/callback` (old `/auth/callback` removed), header wraps at 360 px.
- verify GREEN (coverage above baseline) · E2E 110/110 PASS (14.1 min) · reviewer round 4: 0 high/medium, 6 low (R1–R6, see review-findings.json).
