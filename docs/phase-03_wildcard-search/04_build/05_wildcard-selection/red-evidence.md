# Red evidence — 05_wildcard-selection

Date: 2026-10-07 · FRs: FR-53
Scope: slice (related tests only — the slice gate runs the full suite)

## Tagged tests

- FR-53 → `backend/src/test/java/com/oracul/app/research/EventBatchingIT.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/research/EventClassificationBatchIT.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/research/EventConcurrencyIT.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/research/EventConcurrencyOneIT.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/research/EventFailureIT.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/research/EventSourceCapIT.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/research/GoogleNewsProviderLocalTest.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/research/GoogleNewsRunIT.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/research/ParallelSearchRunIT.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/research/SourceFilteringIT.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/research/SourceFixtureIT.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/research/SourceMetadataIT.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/research/SourceQualityIT.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/research/SourceReadSeamIT.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/research/SourceRetrievalIT.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/research/WildcardSelectionIT.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/research/WildcardSelectorTest.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/result/InsufficientEvidenceIT.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/result/OptionalWireFieldsAbsentIT.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/runs/GetRunTerminalIT.java` (backend)
- FR-53 → `backend/src/test/java/com/oracul/app/runs/StopRunIT.java` (backend)
- FR-53 → `e2e/tests/events.spec.ts` (e2e)
- FR-53 → `e2e/tests/evidence-pack.spec.ts` (e2e)
- FR-53 → `e2e/tests/search-sources.spec.ts` (e2e)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
2026-10-07T08:21:05.236+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-8 - Shutdown initiated...
2026-10-07T08:21:05.237+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-8 - Shutdown completed.
2026-10-07T08:21:05.249+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T08:21:05.249+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-9 - Shutdown initiated...
2026-10-07T08:21:05.249+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-9 - Shutdown completed.
2026-10-07T08:21:23.253+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T08:21:23.253+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-2 - Shutdown initiated...
2026-10-07T08:21:23.254+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-2 - Shutdown completed.
2026-10-07T08:21:23.274+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T08:21:23.275+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-3 - Shutdown initiated...
2026-10-07T08:21:23.275+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-3 - Shutdown completed.
2026-10-07T08:21:23.288+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T08:21:23.289+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-4 - Shutdown initiated...
2026-10-07T08:21:23.289+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-4 - Shutdown completed.
2026-10-07T08:21:23.302+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T08:21:23.303+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-5 - Shutdown initiated...
2026-10-07T08:21:23.303+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-5 - Shutdown completed.
2026-10-07T08:21:23.315+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T08:21:23.315+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-6 - Shutdown initiated...
2026-10-07T08:21:23.316+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-6 - Shutdown completed.
2026-10-07T08:21:23.328+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T08:21:23.328+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-7 - Shutdown initiated...
2026-10-07T08:21:23.328+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-7 - Shutdown completed.
2026-10-07T08:21:23.341+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T08:21:23.341+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-8 - Shutdown initiated...
2026-10-07T08:21:23.341+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-8 - Shutdown completed.
2026-10-07T08:21:23.352+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T08:21:23.353+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-9 - Shutdown initiated...
2026-10-07T08:21:23.353+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-9 - Shutdown completed.

> Task :test

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

704 tests completed, 570 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 48s

```

RESULT: RED
