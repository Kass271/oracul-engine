# Red evidence — 06_events

Date: 2026-10-03 · FRs: FR-14, FR-15

## Tagged tests

- FR-14 → `backend/src/test/java/com/oracul/app/research/EventBatchingIT.java` (backend)
- FR-14 → `backend/src/test/java/com/oracul/app/research/EventConfigurationTest.java` (backend)
- FR-14 → `backend/src/test/java/com/oracul/app/research/EventCrossBatchIT.java` (backend)
- FR-14 → `backend/src/test/java/com/oracul/app/research/EventFailureIT.java` (backend)
- FR-14 → `backend/src/test/java/com/oracul/app/research/EventNormalizationIT.java` (backend)
- FR-14 → `backend/src/test/java/com/oracul/app/research/EventNormalizerRulesIT.java` (backend)
- FR-14 → `backend/src/test/java/com/oracul/app/research/EventPendingIT.java` (backend)
- FR-14 → `backend/src/test/java/com/oracul/app/research/EventPromptsTest.java` (backend)
- FR-14 → `backend/src/test/java/com/oracul/app/research/EventTimeoutIT.java` (backend)
- FR-14 → `backend/src/test/java/com/oracul/app/research/TokenSimilarityTest.java` (backend)
- FR-14 → `e2e/tests/events.spec.ts` (e2e)
- FR-15 → `backend/src/test/java/com/oracul/app/research/EventBatchingIT.java` (backend)
- FR-15 → `backend/src/test/java/com/oracul/app/research/EventClassificationBatchIT.java` (backend)
- FR-15 → `backend/src/test/java/com/oracul/app/research/EventClassificationIT.java` (backend)
- FR-15 → `backend/src/test/java/com/oracul/app/research/EventClassificationRulesIT.java` (backend)
- FR-15 → `backend/src/test/java/com/oracul/app/research/EventConfigurationTest.java` (backend)
- FR-15 → `backend/src/test/java/com/oracul/app/research/EventFailureIT.java` (backend)
- FR-15 → `backend/src/test/java/com/oracul/app/research/EventPromptsTest.java` (backend)
- FR-15 → `backend/src/test/java/com/oracul/app/research/EventTimeoutIT.java` (backend)
- FR-15 → `e2e/tests/events.spec.ts` (e2e)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
2026-10-03T04:11:13.344+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T04:11:13.344+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-29 - Shutdown initiated...
2026-10-03T04:11:13.345+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-29 - Shutdown completed.
2026-10-03T04:11:13.484+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T04:11:13.484+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-30 - Shutdown initiated...
2026-10-03T04:11:13.485+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-30 - Shutdown completed.
2026-10-03T04:11:13.597+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T04:11:13.597+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-31 - Shutdown initiated...
2026-10-03T04:11:13.598+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-31 - Shutdown completed.
2026-10-03T04:11:13.713+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T04:11:13.714+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-32 - Shutdown initiated...
2026-10-03T04:11:13.714+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-32 - Shutdown completed.
2026-10-03T04:11:13.860+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T04:11:13.861+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-33 - Shutdown initiated...
2026-10-03T04:11:13.862+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-33 - Shutdown completed.
2026-10-03T04:11:14.005+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T04:11:14.006+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-34 - Shutdown initiated...
2026-10-03T04:11:14.006+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-34 - Shutdown completed.
2026-10-03T04:11:14.142+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T04:11:14.142+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-35 - Shutdown initiated...
2026-10-03T04:11:14.143+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-35 - Shutdown completed.
2026-10-03T04:11:14.278+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T04:11:14.278+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-36 - Shutdown initiated...
2026-10-03T04:11:14.279+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-36 - Shutdown completed.
2026-10-03T04:11:14.401+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T04:11:14.402+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-37 - Shutdown initiated...
2026-10-03T04:11:14.403+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-37 - Shutdown completed.
2026-10-03T04:11:14.528+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T04:11:14.529+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-38 - Shutdown initiated...
2026-10-03T04:11:14.530+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-38 - Shutdown completed.

> Task :test

> Task :test FAILED
5 actionable tasks: 1 executed, 4 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

522 tests completed, 115 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 1m 59s

```

RESULT: RED
