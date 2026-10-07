
## Round 1
- Trigger: verify (related tests) RED
- To builders: no · to tester: backend/src/test/java/com/oracul/app/research/ArticleRetrievalRunIT.java, e2e/tests/article-text.spec.ts, backend/src/test/java/com/oracul/app/runs/StopRunIT.java, backend/src/test/java/com/oracul/app/result/OptionalWireFieldsAbsentIT.java
- Output:

```
/03_plan/plan.md [planSliceSize]
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
PASS     docs/phase-03_wildcard-search/04_build/06_wildcard-pack/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/06_wildcard-pack/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/06_wildcard-pack/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/06_wildcard-pack/rounds.md [nonEmpty]
PASS     docs/phase-03_wildcard-search/04_build/07_starting-conditions/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/07_starting-conditions/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/07_starting-conditions/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/07_starting-conditions/rounds.md [nonEmpty]
RESULT  OK

verify took 1m 42s

==== VERIFY (related tests) RED: backend ====
```

## Round 2
- Trigger: review not clean
- To builders: R1 · to tester: R2, R3
- INVALID  slice 08_article-text: 3 open blocking finding(s) (high, or medium defect): R1, R2, R3

## Round 3
- Trigger: slice gate: verify (stale layers in full) RED
- To builders: no · to tester: apps/oracul-engine/backend/src/test/java/com/oracul/app/research/ArticleRetrieverTest.java, apps/oracul-engine/backend/src/test/java/com/oracul/app/research/ArticleUrlDecoderTest.java, apps/oracul-engine/backend/src/test/java/com/oracul/app/research/FragmentExtractorTest.java, apps/oracul-engine/backend/src/test/java/com/oracul/app/research/SourceRetrievalUnitTest.java
- Output:

```
rd-search/03_plan/plan.md [planSliceSize]
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
PASS     docs/phase-03_wildcard-search/04_build/06_wildcard-pack/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/06_wildcard-pack/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/06_wildcard-pack/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/06_wildcard-pack/rounds.md [nonEmpty]
PASS     docs/phase-03_wildcard-search/04_build/07_starting-conditions/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/07_starting-conditions/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/07_starting-conditions/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/07_starting-conditions/rounds.md [nonEmpty]
RESULT  OK

verify took 5m 27s

==== VERIFY RED: check-coverage ====
```

## Takeover
- Not clean after 3 round(s): verify.
- Code kept, not parked. The orchestrator takes over (small targeted fix, independent delta review, bin/close-slice.mjs) or asks the user.
- Last output:

```
delt-removal/rounds.md [nonEmpty]
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
PASS     docs/phase-03_wildcard-search/04_build/06_wildcard-pack/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/06_wildcard-pack/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/06_wildcard-pack/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/06_wildcard-pack/rounds.md [nonEmpty]
PASS     docs/phase-03_wildcard-search/04_build/07_starting-conditions/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/07_starting-conditions/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/07_starting-conditions/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/07_starting-conditions/rounds.md [nonEmpty]
RESULT  OK

verify took 5m 27s

==== VERIFY RED: check-coverage ====
```
