# Red evidence — 09_wildcard-results

Date: 2026-10-07 · FRs: FR-59, FR-60
Scope: slice (related tests only — the slice gate runs the full suite)

## Tagged tests

- FR-59 → `backend/src/test/java/com/oracul/app/reasoning/SpeculativeScenarioIT.java` (backend)
- FR-59 → `backend/src/test/java/com/oracul/app/result/InsufficientEvidenceIT.java` (backend)
- FR-59 → `backend/src/test/java/com/oracul/app/result/MissingWildcardSourcesIT.java` (backend)
- FR-59 → `backend/src/test/java/com/oracul/app/result/MissingWildcardSourcesWindowIT.java` (backend)
- FR-59 → `backend/src/test/java/com/oracul/app/result/OptionalWireFieldsAbsentIT.java` (backend)
- FR-59 → `backend/src/test/java/com/oracul/app/result/WildcardPackRunIT.java` (backend)
- FR-59 → `backend/src/test/java/com/oracul/app/runs/EvidenceNotesWildcardTest.java` (backend)
- FR-59 → `e2e/tests/events.spec.ts` (e2e)
- FR-59 → `e2e/tests/wildcard-results.spec.ts` (e2e)
- FR-59 → `frontend/src/app/runs/evidence-note.spec.ts` (frontend)
- FR-60 → `backend/src/test/java/com/oracul/app/result/FutureResultIT.java` (backend)
- FR-60 → `backend/src/test/java/com/oracul/app/result/OptionalWireFieldsAbsentIT.java` (backend)
- FR-60 → `backend/src/test/java/com/oracul/app/result/WildcardResultIT.java` (backend)
- FR-60 → `e2e/tests/wildcard-results.spec.ts` (e2e)
- FR-60 → `frontend/src/app/result/wildcard-sources.spec.ts` (frontend)
- FR-60 → `frontend/src/app/result/wildcard-why-news.spec.ts` (frontend)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```

EvidenceNotesWildcardTest > theDecisionFollowsTheTableForEveryRealismTotalAndMissingSet(int, int, int) > realism 10, total 5, missing #1 FAILED
    java.lang.AssertionError at EvidenceNotesWildcardTest.java:45

EvidenceNotesWildcardTest > theDecisionFollowsTheTableForEveryRealismTotalAndMissingSet(int, int, int) > realism 10, total 5, missing #2 FAILED
    java.lang.AssertionError at EvidenceNotesWildcardTest.java:45

EvidenceNotesWildcardTest > theDecisionFollowsTheTableForEveryRealismTotalAndMissingSet(int, int, int) > realism 10, total 6, missing #0 FAILED
    java.lang.AssertionError at EvidenceNotesWildcardTest.java:45

EvidenceNotesWildcardTest > theDecisionFollowsTheTableForEveryRealismTotalAndMissingSet(int, int, int) > realism 10, total 6, missing #1 FAILED
    java.lang.AssertionError at EvidenceNotesWildcardTest.java:45

EvidenceNotesWildcardTest > theDecisionFollowsTheTableForEveryRealismTotalAndMissingSet(int, int, int) > realism 10, total 6, missing #2 FAILED
    java.lang.AssertionError at EvidenceNotesWildcardTest.java:45

EvidenceNotesWildcardTest > theMissingListIsNeitherMutatedNorSortedAndTheResultIsUnmodifiable() FAILED
    java.lang.AssertionError at EvidenceNotesWildcardTest.java:45

EvidenceNotesWildcardTest > aRealismOutsideOneToTenIsRejected() FAILED
    java.lang.AssertionError at EvidenceNotesWildcardTest.java:246

2026-10-07T16:24:49.022+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T16:24:49.024+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2026-10-07T16:24:49.025+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.
2026-10-07T16:24:49.077+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T16:24:49.077+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-2 - Shutdown initiated...
2026-10-07T16:24:49.078+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-2 - Shutdown completed.
2026-10-07T16:24:49.092+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T16:24:49.092+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-3 - Shutdown initiated...
2026-10-07T16:24:49.093+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-3 - Shutdown completed.

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

321 tests completed, 245 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 23s

```

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```

- 0 sources kept

[36m [2m❯[22m src/app/result/wildcard-why-news.spec.ts:[2m465:28[22m[39m
    [90m463|[39m       delete counts.sourcesWithContent;
    [90m464|[39m       await openWhyNews([grp(1, many(1, 1))], counts as ResearchCounts…
    [90m465|[39m       expect(text(testid)).toBe(expected);
    [90m   |[39m                            [31m^[39m
    [90m466|[39m     },
    [90m467|[39m   );

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[61/81]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/wildcard-why-news.spec.ts[2m > [22mFR-60: WHY THESE NEWS? grouped by wildcard[2m > [22mS3 an absent sourcesKept / sourcesWithContent reads 0: summary-content
[31m[1mAssertionError[22m: expected '' to be '0 sources with content' // Object.is equality[39m

- Expected
+ Received

- 0 sources with content

[36m [2m❯[22m src/app/result/wildcard-why-news.spec.ts:[2m465:28[22m[39m
    [90m463|[39m       delete counts.sourcesWithContent;
    [90m464|[39m       await openWhyNews([grp(1, many(1, 1))], counts as ResearchCounts…
    [90m465|[39m       expect(text(testid)).toBe(expected);
    [90m   |[39m                            [31m^[39m
    [90m466|[39m     },
    [90m467|[39m   );

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[62/81]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/wildcard-why-news.spec.ts[2m > [22mFR-60: WHY THESE NEWS? grouped by wildcard[2m > [22mI1 query texts, headings and custom labels are rendered as text, never as markup
[31m[1mAssertionError[22m: expected '' to be '<img src=x onerror=alert(1)> 7/10' // Object.is equality[39m

- Expected
+ Received

- <img src=x onerror=alert(1)> 7/10

[36m [2m❯[22m src/app/result/wildcard-why-news.spec.ts:[2m482:46[22m[39m
    [90m480|[39m     expect(el().querySelector('img')).toBeNull();
    [90m481|[39m     expect(el().querySelector('b')).toBeNull();
    [90m482|[39m     expect(text('why-news-group-title-W01')).toBe(`${XSS} 7/10`);
    [90m   |[39m                                              [31m^[39m
    [90m483|[39m     expect(text('why-news-query-text-Q01')).toBe(XSS);
    [90m484|[39m     expect(text('why-news-query-text-Q02')).toBe('<b>bold</b> words');

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[63/81]⎯[22m[39m


```

RESULT: RED
