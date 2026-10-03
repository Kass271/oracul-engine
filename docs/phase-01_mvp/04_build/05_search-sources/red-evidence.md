# Red evidence — 05_search-sources

Date: 2026-10-02 · FRs: FR-12, FR-13

## Tagged tests

- FR-12 → `backend/src/test/java/com/oracul/app/research/QueryTemplatesTest.java` (backend)
- FR-12 → `backend/src/test/java/com/oracul/app/research/ResearchPlanIT.java` (backend)
- FR-12 → `backend/src/test/java/com/oracul/app/research/ResearchPlanPendingIT.java` (backend)
- FR-12 → `backend/src/test/java/com/oracul/app/research/ResearchPlanTimeoutIT.java` (backend)
- FR-12 → `backend/src/test/java/com/oracul/app/research/SearchPlannerTest.java` (backend)
- FR-12 → `e2e/tests/search-sources.spec.ts` (e2e)
- FR-13 → `backend/src/test/java/com/oracul/app/research/SourceFilteringIT.java` (backend)
- FR-13 → `backend/src/test/java/com/oracul/app/research/SourceFixtureIT.java` (backend)
- FR-13 → `backend/src/test/java/com/oracul/app/research/SourceMetadataIT.java` (backend)
- FR-13 → `backend/src/test/java/com/oracul/app/research/SourceQualityIT.java` (backend)
- FR-13 → `backend/src/test/java/com/oracul/app/research/SourceQueryTimeoutIT.java` (backend)
- FR-13 → `backend/src/test/java/com/oracul/app/research/SourceRetrievalIT.java` (backend)
- FR-13 → `e2e/tests/search-sources.spec.ts` (e2e)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
2026-10-03T01:02:31.447+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-12 - Shutdown initiated...
2026-10-03T01:02:31.449+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-12 - Shutdown completed.
2026-10-03T01:02:31.560+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T01:02:31.560+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-13 - Shutdown initiated...
2026-10-03T01:02:31.561+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-13 - Shutdown completed.
2026-10-03T01:02:31.676+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T01:02:31.677+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-14 - Shutdown initiated...
2026-10-03T01:02:31.678+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-14 - Shutdown completed.
2026-10-03T01:02:31.781+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T01:02:31.782+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-15 - Shutdown initiated...
2026-10-03T01:02:31.783+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-15 - Shutdown completed.
2026-10-03T01:02:31.912+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T01:02:31.913+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-16 - Shutdown initiated...
2026-10-03T01:02:31.913+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-16 - Shutdown completed.
2026-10-03T01:02:32.024+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T01:02:32.024+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-17 - Shutdown initiated...
2026-10-03T01:02:32.025+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-17 - Shutdown completed.
2026-10-03T01:02:32.152+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T01:02:32.153+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-22 - Shutdown initiated...
2026-10-03T01:02:32.154+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-22 - Shutdown completed.
2026-10-03T01:02:32.275+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T01:02:32.275+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-23 - Shutdown initiated...
2026-10-03T01:02:32.275+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-23 - Shutdown completed.
2026-10-03T01:02:32.388+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T01:02:32.389+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-24 - Shutdown initiated...
2026-10-03T01:02:32.390+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-24 - Shutdown completed.
2026-10-03T01:02:32.521+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T01:02:32.521+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-25 - Shutdown initiated...
2026-10-03T01:02:32.522+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-25 - Shutdown completed.
2026-10-03T01:02:32.626+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T01:02:32.627+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-26 - Shutdown initiated...
2026-10-03T01:02:32.627+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-26 - Shutdown completed.

> Task :test FAILED
5 actionable tasks: 1 executed, 4 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

284 tests completed, 65 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 1m 3s

```

RESULT: RED
