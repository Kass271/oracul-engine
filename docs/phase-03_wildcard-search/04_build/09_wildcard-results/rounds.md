
## Round 1
- Trigger: verify (related tests) RED
- To builders: no · to tester: backend/src/test/java/com/oracul/app/result/MissingWildcardSourcesIT.java
- Output:

```
delt-removal/review-findings.json [reviewClean]
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
PASS     docs/phase-03_wildcard-search/04_build/08_article-text/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/08_article-text/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/08_article-text/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/08_article-text/rounds.md [nonEmpty]
PASS     RESULT  OK

verify took 54s

==== VERIFY (related tests) RED: backend ====
```

## Round 2
- Trigger: E2E (related specs not green on the current inputs) RED
- To builders: no · to tester: /Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/e2e/tests/wildcard-results.spec.ts
- Output:

```
NEEDED (4 of 29 specs): events.spec.ts why-and-sources.spec.ts why-these-news.spec.ts wildcard-results.spec.ts
    attachment #2: trace (application/zip) ─────────────────────────────────────────────────────────
    test-results/wildcard-results-FR-59-a-w-3453a-h-groups-with-EMPTY-queries-chromium/trace.zip
    Usage:

        npx playwright show-trace test-results/wildcard-results-FR-59-a-w-3453a-h-groups-with-EMPTY-queries-chromium/trace.zip

    ────────────────────────────────────────────────────────────────────────────────────────────────

  1 failed
    [chromium] › tests/wildcard-results.spec.ts:334:3 › FR-59 a wildcard without sources is explicit › FR-59 mode empty: "No sources", the NO_EVIDENCE note, and WHY THESE NEWS? still lists both groups with EMPTY queries 
  1 did not run
  12 passed (3.9m)
 Container oracul-engine-backend-1  Restarting
 Container oracul-engine-backend-1  Started

Playwright took 3m 56s
E2E FAIL
==== E2E FAILURES (1) ====
✘ wildcard-results.spec.ts › FR-59 mode empty: "No sources", the NO_EVIDENCE note, and WHY THESE NEWS? still lists both groups with EMPTY queries · chromium
    Error: expect(received).toEqual(expected) // deep equality
    - Expected  - 4
    + Received  + 1
    - Array [
    -   "why-news-group-W01",
    -   "why-news-group-W02",
    - ]
    + Array []
    attachment: e2e/test-results/wildcard-results-FR-59-a-w-3453a-h-groups-with-EMPTY-queries-chromium/trace.zip
==== END E2E FAILURES ====

```

## Round 3
- Trigger: slice gate: verify (stale layers in full) RED
- To builders: no · to tester: backend/src/test/java/com/oracul/app/result/StoryWritingStageIT.java
- Output:

```
/01_gdelt-removal/review-findings.json [reviewClean]
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
PASS     docs/phase-03_wildcard-search/04_build/08_article-text/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/08_article-text/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/08_article-text/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/08_article-text/rounds.md [nonEmpty]
RESULT  OK

verify took 6m 04s

==== VERIFY RED: backend · check-coverage ====
```

## Takeover
- Not clean after 3 round(s): verify.
- Code kept, not parked. The orchestrator takes over (small targeted fix, independent delta review, bin/close-slice.mjs) or asks the user.
- Last output:

```
tching/rounds.md [nonEmpty]
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
PASS     docs/phase-03_wildcard-search/04_build/08_article-text/red-evidence.md [contains:RESULT: RED]
PASS     docs/phase-03_wildcard-search/04_build/08_article-text/review-findings.json [reviewFile]
PASS     docs/phase-03_wildcard-search/04_build/08_article-text/review-findings.json [reviewClean]
PASS     docs/phase-03_wildcard-search/04_build/08_article-text/rounds.md [nonEmpty]
RESULT  OK

verify took 6m 04s

==== VERIFY RED: backend · check-coverage ====
```
