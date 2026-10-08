
## Round 1
- Trigger: E2E (related specs not green on the current inputs) RED
- To builders: no · to tester: /Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/e2e/tests/story-rewrite.spec.ts
- Output:

```
NEEDED (1 of 32 specs): story-rewrite.spec.ts
    test-results/story-rewrite-FR-67-rewrit-c85f7-un-of-the-session-is-active-chromium/trace.zip
    Usage:

        npx playwright show-trace test-results/story-rewrite-FR-67-rewrit-c85f7-un-of-the-session-is-active-chromium/trace.zip

    ────────────────────────────────────────────────────────────────────────────────────────────────

  Slow test file: [chromium] › tests/story-rewrite.spec.ts (5.4m)
  Consider running tests from slow files in parallel. See: https://playwright.dev/docs/test-parallel
  1 failed
    [chromium] › tests/story-rewrite.spec.ts:265:3 › FR-67 rewrite the same future in another style or language › FR-67 a rewrite is refused while another run of the session is active 
  4 passed (5.5m)
 Container oracul-engine-backend-1  Restarting
 Container oracul-engine-backend-1  Started

Playwright took 5m 32s
E2E FAIL
==== E2E FAILURES (1) ====
✘ story-rewrite.spec.ts › FR-67 a rewrite is refused while another run of the session is active · chromium
    Test timeout of 240000ms exceeded.
    attachment: e2e/test-results/story-rewrite-FR-67-rewrit-c85f7-un-of-the-session-is-active-chromium/trace.zip
==== END E2E FAILURES ====

```

## Round 2
- Trigger: slice gate: verify (stale layers in full) RED
- To builders: no · to tester: backend/src/test/java/com/oracul/app/research/ParallelSearchWindowIT.java
- Output:

```
   backend generates interfaces from openapi.yaml before compile
PASS     frontend generates the client from openapi.yaml before build/test/start
PASS     backend generated code is up to date
PASS     frontend generated code is up to date
RESULT  OK

== stack ==
PASS     .oracul/stack.json: modes e2e, run
RESULT  OK

== review evidence ==
PASS     slice 01_style-catalogue: clean (round 1, 2 finding(s), 2 open low)
PASS     slice 02_style-selection: clean (round 4, 5 finding(s), 4 open low)
PASS     slice 03_styled-story: clean (round 1, 1 finding(s), 1 open low)
RESULT  OK

== artifacts 00_setup ==
PASS     docs/phase-04_writing-style/00_setup/adr-001-stack.md [contains:Spring Boot]
PASS     docs/phase-04_writing-style/00_setup/environment-check.md [contains:Result: OK]
PASS     api/openapi.yaml [openapi]
PASS     docker-compose.yml [contains:services:]
PASS     backend/build.gradle.kts [contains:openApiGenerate]
PASS     frontend/package.json [contains:@angular/material]
PASS     e2e/playwright.config.ts [nonEmpty]
RESULT  OK

== artifacts 01_scope ==
PASS     docs/phase-04_writing-style/01_scope/idea.md [nonEmpty]
PASS     docs/phase-04_writing-style/01_scope/clarification-log.md [regex:^##\s*Round\s+\d+]
PASS     docs/phase-04_writing-style/01_scope/requirements.md [requirements]
PASS     docs/phase-04_writing-style/01_scope/requirements.md [approved]
RESULT  OK

== artifacts 02_specs ==
PASS     docs/phase-04_writing-style/02_specs/*.md [minFiles:2] 4 file(s)
PASS     docs/phase-04_writing-style/02_specs/contract-notes.md [nonEmpty]
PASS     docs/phase-04_writing-style/02_specs [specsCoverFrs]
PASS     api/openapi.yaml [openapi]
RESULT  OK

== artifacts 03_plan ==
PASS     docs/phase-04_writing-style/03_plan/plan.md [planCoversFrs]
PASS     docs/phase-04_writing-style/03_plan/plan.md [planSliceSize]
PASS     docs/phase-04_writing-style/03_plan/plan.md [approved]
RESULT  OK

== artifacts 04_build ==
PASS     docs/phase-04_writing-style/04_build/01_style-catalogue/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-04_writing-style/04_build/01_style-catalogue/review-findings.json [reviewFile]
PASS     docs/phase-04_writing-style/04_build/01_style-catalogue/review-findings.json [reviewClean]
PASS     docs/phase-04_writing-style/04_build/02_style-selection/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-04_writing-style/04_build/02_style-selection/review-findings.json [reviewFile]
PASS     docs/phase-04_writing-style/04_build/02_style-selection/review-findings.json [reviewClean]
PASS     docs/phase-04_writing-style/04_build/02_style-selection/rounds.md [nonEmpty]
PASS     docs/phase-04_writing-style/04_build/03_styled-story/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-04_writing-style/04_build/03_styled-story/review-findings.json [reviewFile]
PASS     docs/phase-04_writing-style/04_build/03_styled-story/review-findings.json [reviewClean]
RESULT  OK

verify took 5m 39s

==== VERIFY RED: backend · check-coverage ====
```
