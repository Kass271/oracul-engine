# Red evidence — 03_parallel-search

Date: 2026-10-07 · FRs: FR-52
Scope: slice (related tests only — the slice gate runs the full suite)

## Tagged tests

- FR-52 → `backend/src/test/java/com/oracul/app/NewsProviderScanTest.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/AbstractOrderIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/GoogleNewsBudgetIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/GoogleNewsRunIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/GoogleNewsSearchConcurrencyTest.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/GoogleNewsSearchIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/GoogleNewsSearchTextTest.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/GoogleNewsSourceIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/GoogleNewsTimingIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/NewsSearchNoBudgetIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/NoFallbackSearchIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/ParallelSearchIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/ParallelSearchRunIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/ParallelSearchSerialIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/ParallelSearchWindowIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/RemovedNewsPropertiesIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/ResearchPlanIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/ResearchPlanTimeoutIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/SearchBudgetTest.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/SourceCapIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/SourceFixtureIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/SourceQueryTimeoutIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/SourceRetrievalIT.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/research/SourceRetrievalUnitTest.java` (backend)
- FR-52 → `backend/src/test/java/com/oracul/app/runs/StopQueuedRunIT.java` (backend)
- FR-52 → `e2e/tests/alternative-future.spec.ts` (e2e)
- FR-52 → `e2e/tests/insufficient-evidence.spec.ts` (e2e)
- FR-52 → `e2e/tests/run-modes.spec.ts` (e2e)
- FR-52 → `e2e/tests/search-sources.spec.ts` (e2e)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
    java.lang.AssertionError at SourceRetrievalIT.java:200

StopQueuedRunIT > aQueuedRunThatIsStoppedNeverStarts() FAILED
    java.lang.AssertionError at StopQueuedRunIT.java:52

2026-10-07T03:45:11.237+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T03:45:11.238+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2026-10-07T03:45:11.239+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.
2026-10-07T03:45:11.288+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T03:45:11.288+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-4 - Shutdown initiated...
2026-10-07T03:45:11.288+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-4 - Shutdown completed.
2026-10-07T03:45:11.310+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T03:45:11.311+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-5 - Shutdown initiated...
2026-10-07T03:45:11.311+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-5 - Shutdown completed.
2026-10-07T03:45:11.329+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T03:45:11.330+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-6 - Shutdown initiated...
2026-10-07T03:45:11.330+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-6 - Shutdown completed.
2026-10-07T03:45:11.349+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T03:45:11.349+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-7 - Shutdown initiated...
2026-10-07T03:45:11.349+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-7 - Shutdown completed.
2026-10-07T03:45:11.364+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T03:45:11.365+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-8 - Shutdown initiated...
2026-10-07T03:45:11.365+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-8 - Shutdown completed.
2026-10-07T03:45:11.381+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T03:45:11.381+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-9 - Shutdown initiated...
2026-10-07T03:45:11.381+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-9 - Shutdown completed.
2026-10-07T03:45:11.395+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T03:45:11.395+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-10 - Shutdown initiated...
2026-10-07T03:45:11.396+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-10 - Shutdown completed.

> Task :test

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

328 tests completed, 211 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 7m 30s

```

RESULT: RED
