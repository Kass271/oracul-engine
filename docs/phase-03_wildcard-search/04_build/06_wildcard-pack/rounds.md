
## Round 1
- Trigger: slice gate: verify (stale layers in full) RED
- To builders: yes · to tester: no
- Output:

```
.config.ts [nonEmpty]
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
PASS     docs/phase-03_wildcard-search/04_build/03_parallel-search/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/03_parallel-search/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/03_parallel-search/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/03_parallel-search/rounds.md [nonEmpty]
PASS     docs/phase-03_wildcard-search/04_build/04_wildcard-queries/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/04_wildcard-queries/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/04_wildcard-queries/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/04_wildcard-queries/rounds.md [nonEmpty]
PASS     docs/phase-03_wildcard-search/04_build/05_wildcard-selection/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/05_wildcard-selection/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/05_wildcard-selection/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/05_wildcard-selection/rounds.md [nonEmpty]
RESULT  OK

verify took 4m 40s

==== VERIFY RED: check-coverage ====

```

## Round 2
- Trigger: review not clean
- To builders: none · to tester: R5, R6, R7
- INVALID  slice 06_wildcard-pack: 3 open blocking finding(s) (high, or medium defect): R5, R6, R7

## Round 3
- Trigger: slice gate: verify (stale layers in full) RED
- To builders: no · to tester: backend/src/test/java/com/oracul/app/research/ParallelSearchWindowIT.java
- Output:

```
s [nonEmpty]
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
PASS     docs/phase-03_wildcard-search/04_build/03_parallel-search/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/03_parallel-search/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/03_parallel-search/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/03_parallel-search/rounds.md [nonEmpty]
PASS     docs/phase-03_wildcard-search/04_build/04_wildcard-queries/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/04_wildcard-queries/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/04_wildcard-queries/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/04_wildcard-queries/rounds.md [nonEmpty]
PASS     docs/phase-03_wildcard-search/04_build/05_wildcard-selection/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/05_wildcard-selection/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/05_wildcard-selection/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/05_wildcard-selection/rounds.md [nonEmpty]
RESULT  OK

verify took 4m 41s

==== VERIFY RED: backend · check-coverage ====
```

## Takeover
- Not clean after 3 round(s): verify.
- Code kept, not parked. The orchestrator takes over (small targeted fix, independent delta review, bin/close-slice.mjs) or asks the user.
- Last output:

```
3_wildcard-search/02_specs/contract-notes.md [nonEmpty]
PASS     docs/phase-03_wildcard-search/02_specs [specsCoverFrs]
PASS     docs/phase-03_wildcard-search/02_specs [planCoversFrs]
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
PASS     docs/phase-03_wildcard-search/04_build/03_parallel-search/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/03_parallel-search/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/03_parallel-search/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/03_parallel-search/rounds.md [nonEmpty]
PASS     docs/phase-03_wildcard-search/04_build/04_wildcard-queries/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/04_wildcard-queries/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/04_wildcard-queries/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/04_wildcard-queries/rounds.md [nonEmpty]
PASS     docs/phase-03_wildcard-search/04_build/05_wildcard-selection/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/05_wildcard-selection/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/05_wildcard-selection/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/05_wildcard-selection/rounds.md [nonEmpty]
RESULT  OK

verify took 4m 41s

==== VERIFY RED: backend · check-coverage ====
```
