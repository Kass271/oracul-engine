
## Slice size
- SLICE SIZE: 03_parallel-search rewrites 25 older tests (> 10) — consider splitting the slice
- Stopped before the tester: the user decides — split the slice (plan change, analyst) or continue with acceptSize.

## Start check failed
- INVALID  docs/phase-03_wildcard-search/02_specs [sliceSpec] — FR-52: listed test backend/src/test/java/com/oracul/app/research/GoogleQueryGroupsRulesTest.java does not exist
- No builder ran; nothing to park. Fix the named doc (02_specs → analyst Step 4a; red-evidence → red-check), then resume stage green.
- Output:

```
== artifacts 04_build / 03_parallel-search (stage red) ==
INVALID  docs/phase-03_wildcard-search/02_specs [sliceSpec] — FR-52: listed test backend/src/test/java/com/oracul/app/research/GoogleQueryGroupsRulesTest.java does not exist
PASS     api/openapi.yaml [contractParses]
PASS     docs/phase-03_wildcard-search/04_build/03_parallel-search/red-evidence.md [contains:RESULT: RED]
RESULT  FAIL (1 problem)
```

## Round 1
- Trigger: verify (related tests) RED
- To builders: no · to tester: backend/src/test/java/com/oracul/app/research/AbstractOrderIT.java, backend/src/test/java/com/oracul/app/research/ParallelSearchIT.java, backend/src/test/java/com/oracul/app/research/SourceRetrievalIT.java, backend/src/test/java/com/oracul/app/research/StubNews.java
- Output:

```
odes.spec.ts, e2e/tests/search-sources.spec.ts
RESULT  OK

SKIP     check-coverage: related tests only — the full verify (slice gate) checks coverage

== contract ==
PASS     openapi.yaml valid (19 operations)
PASS     no property is both required and nullable
PASS     backend generates interfaces from openapi.yaml before compile
PASS     frontend generates the client from openapi.yaml before build/test
PASS     backend generated code is up to date
PASS     frontend generated code is up to date
RESULT  OK

== stack ==
PASS     .oracul/stack.json: modes e2e, run
RESULT  OK

== review evidence ==
PASS     slice 01_gdelt-removal: clean (round 7, 12 finding(s), 6 open low)
PASS     slice 02_safe-fetching: clean (round 4, 11 finding(s), 9 open low)
RESULT  OK

== artifacts 00_setup ==
PASS     docs/phase-03_wildcard-search/00_setup/adr-001-stack.md [contains:Spring Boot]
PASS     docs/phase-03_wildcard-search/00_setup/environment-check.md [contains:Result: OK]
PASS     api/openapi.yaml [openapi]
PASS     docker-compose.yml [contains:services:]
PASS     backend/build.gradle.kts [contains:openApiGenerate]
PASS     frontend/package.json [contains:@angular/material]
PASS     e2e/playwright.config.ts [nonEmpty]
RESULT  OK

== artifacts 01_scope ==
PASS     docs/phase-03_wildcard-search/01_scope/idea.md [nonEmpty]
PASS     docs/phase-03_wildcard-search/01_scope/clarification-log.md [regex:^##\s*Round\s+\d+]
PASS     docs/phase-03_wildcard-search/01_scope/requirements.md [requirements]
PASS     docs/phase-03_wildcard-search/01_scope/requirements.md [approved]
RESULT  OK

== artifacts 02_specs ==
PASS     docs/phase-03_wildcard-search/02_specs/*.md [minFiles:2] 5 file(s)
PASS     docs/phase-03_wildcard-search/02_specs/contract-notes.md [nonEmpty]
PASS     docs/phase-03_wildcard-search/02_specs [specsCoverFrs]
PASS     api/openapi.yaml [openapi]
RESULT  OK

== artifacts 03_plan ==
PASS     docs/phase-03_wildcard-search/03_plan/plan.md [planCoversFrs]
PASS     docs/phase-03_wildcard-search/03_plan/plan.md [planSliceSize]
PASS     docs/phase-03_wildcard-search/03_plan/plan.md [approved]
RESULT  OK

== artifacts 04_build ==
PASS     docs/phase-03_wildcard-search/04_build/01_gdelt-removal/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/01_gdelt-removal/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/01_gdelt-removal/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/01_gdelt-removal/rounds.md [nonEmpty]
PASS     docs/phase-03_wildcard-search/04_build/02_safe-fetching/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/02_safe-fetching/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/02_safe-fetching/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/02_safe-fetching/rounds.md [nonEmpty]
RESULT  OK

verify took 2m 41s

==== VERIFY (related tests) RED: backend ====
```

## Round 2
- Trigger: slice gate: verify (stale layers in full) RED
- To builders: no · to tester: backend/src/test/java/com/oracul/app/research/GoogleNewsSearchConcurrencyTest.java
- Output:

```
, never lower the baseline
PASS     frontend 100.0% (full run of 2026-10-07T01:34:24.572Z, inputs unchanged) ≥ baseline 100.0%
RESULT  FAIL (1 problem)

== contract ==
PASS     openapi.yaml valid (19 operations)
PASS     no property is both required and nullable
PASS     backend generates interfaces from openapi.yaml before compile
PASS     frontend generates the client from openapi.yaml before build/test/start
PASS     backend generated code is up to date
PASS     frontend generated code is up to date
RESULT  OK

== stack ==
PASS     .oracul/stack.json: modes e2e, run
RESULT  OK

== review evidence ==
PASS     slice 01_gdelt-removal: clean (round 7, 12 finding(s), 6 open low)
PASS     slice 02_safe-fetching: clean (round 4, 11 finding(s), 9 open low)
RESULT  OK

== artifacts 00_setup ==
PASS     docs/phase-03_wildcard-search/00_setup/adr-001-stack.md [contains:Spring Boot]
PASS     docs/phase-03_wildcard-search/00_setup/environment-check.md [contains:Result: OK]
PASS     api/openapi.yaml [openapi]
PASS     docker-compose.yml [contains:services:]
PASS     backend/build.gradle.kts [contains:openApiGenerate]
PASS     frontend/package.json [contains:@angular/material]
PASS     e2e/playwright.config.ts [nonEmpty]
RESULT  OK

== artifacts 01_scope ==
PASS     docs/phase-03_wildcard-search/01_scope/idea.md [nonEmpty]
PASS     docs/phase-03_wildcard-search/01_scope/clarification-log.md [regex:^##\s*Round\s+\d+]
PASS     docs/phase-03_wildcard-search/01_scope/requirements.md [requirements]
PASS     docs/phase-03_wildcard-search/01_scope/requirements.md [approved]
RESULT  OK

== artifacts 02_specs ==
PASS     docs/phase-03_wildcard-search/02_specs/*.md [minFiles:2] 5 file(s)
PASS     docs/phase-03_wildcard-search/02_specs/contract-notes.md [nonEmpty]
PASS     docs/phase-03_wildcard-search/02_specs [specsCoverFrs]
PASS     api/openapi.yaml [openapi]
RESULT  OK

== artifacts 03_plan ==
PASS     docs/phase-03_wildcard-search/03_plan/plan.md [planCoversFrs]
PASS     docs/phase-03_wildcard-search/03_plan/plan.md [planSliceSize]
PASS     docs/phase-03_wildcard-search/03_plan/plan.md [approved]
RESULT  OK

== artifacts 04_build ==
PASS     docs/phase-03_wildcard-search/04_build/01_gdelt-removal/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/01_gdelt-removal/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/01_gdelt-removal/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/01_gdelt-removal/rounds.md [nonEmpty]
PASS     docs/phase-03_wildcard-search/04_build/02_safe-fetching/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/02_safe-fetching/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/02_safe-fetching/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/02_safe-fetching/rounds.md [nonEmpty]
RESULT  OK

verify took 7m 13s

==== VERIFY RED: check-coverage ====

```

## Close failed
- INVALID  docs/phase-03_wildcard-search/04_build/03_parallel-search/review-findings.json [reviewClean] — 1 open high/medium finding(s)
- Not parked: verify, E2E and review had passed. The slice stays IN_PROGRESS with its code; "continue" resumes it at stage green.
- Output:

```
-- docs syntax --
== docs (shell syntax) ==
PASS     no changed doc with shell blocks
RESULT  OK

-- review --
== review evidence ==
WARN     slice 03_parallel-search: hardening R1 open (backend/src/test/java/com/oracul/app/research/AbstractNewsSearchIT.java) — not blocking; goes to the release review
PASS     slice 03_parallel-search: clean (round 3, 6 finding(s), 5 open low)
RESULT  OK

-- E2E covers the current code --
== e2e fresh ==
PASS     all 25 E2E specs green on the current inputs (gate ledger)
RESULT  OK

-- full verify on the current inputs --
verify: reusing the full GREEN verify of 2026-10-07T01:54:50.546Z

-- artifacts --
== artifacts 04_build / 03_parallel-search (stage done) ==
WARN     docs/phase-03_wildcard-search/02_specs [sliceSpec] — SLICE SIZE: 03_parallel-search rewrites 24 older tests (> 10) — consider splitting the slice
PASS     docs/phase-03_wildcard-search/02_specs [sliceSpec]
PASS     api/openapi.yaml [contractParses]
PASS     docs/phase-03_wildcard-search/04_build/03_parallel-search/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/03_parallel-search/review-findings.json [reviewFile]
INVALID  docs/phase-03_wildcard-search/04_build/03_parallel-search/review-findings.json [reviewClean] — 1 open high/medium finding(s)
PASS     docs/phase-03_wildcard-search/04_build/03_parallel-search/rounds.md [nonEmpty]
RESULT  FAIL (1 problem)

CLOSE FAILED at "artifacts" — nothing committed, the slice stays IN_PROGRESS
```
