# Red evidence — 01_gdelt-removal

Date: 2026-10-06 · FRs: FR-49
Scope: slice (related tests only — the slice gate runs the full suite)

## Tagged tests

- FR-49 → `backend/src/test/java/com/oracul/app/NewsProviderScanTest.java` (backend)
- FR-49 → `backend/src/test/java/com/oracul/app/research/GoogleNewsBudgetIT.java` (backend)
- FR-49 → `backend/src/test/java/com/oracul/app/research/GoogleNewsRunIT.java` (backend)
- FR-49 → `backend/src/test/java/com/oracul/app/research/GoogleNewsSearchIT.java` (backend)
- FR-49 → `backend/src/test/java/com/oracul/app/research/GoogleNewsTimingIT.java` (backend)
- FR-49 → `backend/src/test/java/com/oracul/app/research/NewsSearchNoBudgetIT.java` (backend)
- FR-49 → `backend/src/test/java/com/oracul/app/research/NoFallbackSearchIT.java` (backend)
- FR-49 → `backend/src/test/java/com/oracul/app/research/RemovedNewsPropertiesIT.java` (backend)
- FR-49 → `backend/src/test/java/com/oracul/app/research/SourceRetrievalIT.java` (backend)
- FR-49 → `e2e/tests/run-modes.spec.ts` (e2e)
- FR-49 → `e2e/tests/search-sources.spec.ts` (e2e)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
2026-10-06T23:00:20.855+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-24 - Shutdown initiated...
2026-10-06T23:00:20.855+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-24 - Shutdown completed.
2026-10-06T23:00:20.871+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-06T23:00:20.871+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-25 - Shutdown initiated...
2026-10-06T23:00:20.872+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-25 - Shutdown completed.
2026-10-06T23:01:56.832+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-06T23:01:56.832+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-14 - Shutdown initiated...
2026-10-06T23:01:56.833+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-14 - Shutdown completed.
2026-10-06T23:01:56.859+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-06T23:01:56.859+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-15 - Shutdown initiated...
2026-10-06T23:01:56.860+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-15 - Shutdown completed.
2026-10-06T23:01:56.874+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-06T23:01:56.874+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-16 - Shutdown initiated...
2026-10-06T23:01:56.875+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-16 - Shutdown completed.
2026-10-06T23:01:56.887+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-06T23:01:56.887+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-17 - Shutdown initiated...
2026-10-06T23:01:56.887+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-17 - Shutdown completed.
2026-10-06T23:01:56.900+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-06T23:01:56.900+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-18 - Shutdown initiated...
2026-10-06T23:01:56.900+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-18 - Shutdown completed.
2026-10-06T23:01:56.912+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-06T23:01:56.913+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-19 - Shutdown initiated...
2026-10-06T23:01:56.913+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-19 - Shutdown completed.
2026-10-06T23:01:56.925+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-06T23:01:56.925+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-20 - Shutdown initiated...
2026-10-06T23:01:56.925+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-20 - Shutdown completed.
2026-10-06T23:01:56.937+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-06T23:01:56.937+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-21 - Shutdown initiated...
2026-10-06T23:01:56.938+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-21 - Shutdown completed.

> Task :test

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

758 tests completed, 47 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 6m 51s

```

RESULT: RED
