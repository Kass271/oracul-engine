# Red evidence — 08_article-text

Date: 2026-10-07 · FRs: FR-54, FR-55, FR-61
Scope: slice (related tests only — the slice gate runs the full suite)

## Tagged tests

- FR-54 → `backend/src/test/java/com/oracul/app/ArticleFetchTimeoutComposeTest.java` (backend)
- FR-54 → `backend/src/test/java/com/oracul/app/research/ArticleOutcomeIT.java` (backend)
- FR-54 → `backend/src/test/java/com/oracul/app/research/ArticleRetrievalDeadlineIT.java` (backend)
- FR-54 → `backend/src/test/java/com/oracul/app/research/ArticleRetrievalRunIT.java` (backend)
- FR-54 → `backend/src/test/java/com/oracul/app/research/ArticleRetrieverTest.java` (backend)
- FR-54 → `backend/src/test/java/com/oracul/app/research/ArticleUrlDecoderTest.java` (backend)
- FR-54 → `backend/src/test/java/com/oracul/app/research/GoogleNewsSearchConcurrencyTest.java` (backend)
- FR-54 → `backend/src/test/java/com/oracul/app/research/GoogleNewsSourceIT.java` (backend)
- FR-54 → `backend/src/test/java/com/oracul/app/research/SafeFetcherSameHostTest.java` (backend)
- FR-54 → `backend/src/test/java/com/oracul/app/research/SearchBudgetTest.java` (backend)
- FR-54 → `backend/src/test/java/com/oracul/app/research/SourceMetadataIT.java` (backend)
- FR-54 → `backend/src/test/java/com/oracul/app/research/SourceRetrievalIT.java` (backend)
- FR-54 → `backend/src/test/java/com/oracul/app/research/SourceRetrievalUnitTest.java` (backend)
- FR-54 → `backend/src/test/java/com/oracul/app/result/OptionalWireFieldsAbsentIT.java` (backend)
- FR-54 → `backend/src/test/java/com/oracul/app/runs/GetRunTerminalIT.java` (backend)
- FR-54 → `backend/src/test/java/com/oracul/app/runs/StopRunIT.java` (backend)
- FR-54 → `e2e/tests/article-text.spec.ts` (e2e)
- FR-55 → `backend/src/test/java/com/oracul/app/research/ArticleRetrievalRunIT.java` (backend)
- FR-55 → `backend/src/test/java/com/oracul/app/research/EvidencePackIT.java` (backend)
- FR-55 → `backend/src/test/java/com/oracul/app/research/FragmentExtractorTest.java` (backend)
- FR-55 → `backend/src/test/java/com/oracul/app/result/OptionalWireFieldsAbsentIT.java` (backend)
- FR-55 → `backend/src/test/java/com/oracul/app/result/WildcardPackRunIT.java` (backend)
- FR-55 → `e2e/tests/article-text.spec.ts` (e2e)
- FR-55 → `e2e/tests/evidence-pack.spec.ts` (e2e)
- FR-61 → `backend/src/test/java/com/oracul/app/ArticleFetchTimeoutComposeTest.java` (backend)
- FR-61 → `backend/src/test/java/com/oracul/app/research/ArticleRetrievalRunIT.java` (backend)
- FR-61 → `backend/src/test/java/com/oracul/app/research/StubNewsTest.java` (backend)
- FR-61 → `e2e/tests/article-text.spec.ts` (e2e)
- FR-61 → `e2e/tests/run-control.spec.ts` (e2e)
- FR-61 → `e2e/tests/search-sources.spec.ts` (e2e)
- FR-61 → `e2e/tests/stub-google.spec.ts` (e2e)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
    org.opentest4j.AssertionFailedError at WildcardPackRunIT.java:208

WildcardPackRunIT > theInvariantsOfTheGroupedPackHoldForEveryDataset(String) > "V4 under body A" FAILED
    org.opentest4j.AssertionFailedError at WildcardPackRunIT.java:135

WildcardPackRunIT > theInvariantsOfTheGroupedPackHoldForEveryDataset(String) > "V4 under body B" FAILED
    org.opentest4j.AssertionFailedError at WildcardPackRunIT.java:135

WildcardPackRunIT > theInvariantsOfTheGroupedPackHoldForEveryDataset(String) > "seven sources, one shared" FAILED
    org.opentest4j.AssertionFailedError at WildcardPackRunIT.java:135

StopRunIT > aStopDuringAnOutboundRequestEndsTheRunForGood(String, boolean, boolean) > stop while the "DECODE" request is in flight FAILED
    org.opentest4j.AssertionFailedError at StopRunIT.java:381

StopRunIT > aStopDuringAnOutboundRequestEndsTheRunForGood(String, boolean, boolean) > stop while the "ARTICLE" request is in flight FAILED
    org.opentest4j.AssertionFailedError at StopRunIT.java:381

2026-10-07T13:53:24.143+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T13:53:24.144+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2026-10-07T13:53:24.145+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.
2026-10-07T13:53:24.233+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T13:53:24.233+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-2 - Shutdown initiated...
2026-10-07T13:53:24.234+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-2 - Shutdown completed.
2026-10-07T13:53:24.250+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T13:53:24.250+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-3 - Shutdown initiated...
2026-10-07T13:53:24.251+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-3 - Shutdown completed.
2026-10-07T13:53:24.266+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T13:53:24.267+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-4 - Shutdown initiated...
2026-10-07T13:53:24.267+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-4 - Shutdown completed.

> Task :test

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

676 tests completed, 491 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 1m 40s

```

RESULT: RED
