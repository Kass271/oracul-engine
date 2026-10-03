# Red evidence — 07_evidence-pack

Date: 2026-10-03 · FRs: FR-16, FR-17, FR-18

## Tagged tests

- FR-16 → `backend/src/test/java/com/oracul/app/research/EventRankerTest.java` (backend)
- FR-16 → `backend/src/test/java/com/oracul/app/research/EvidenceConfigurationTest.java` (backend)
- FR-16 → `backend/src/test/java/com/oracul/app/research/RankingMinQualityIT.java` (backend)
- FR-16 → `backend/src/test/java/com/oracul/app/research/RankingSelectionIT.java` (backend)
- FR-16 → `e2e/tests/evidence-pack.spec.ts` (e2e)
- FR-17 → `backend/src/test/java/com/oracul/app/research/EvidenceConfigurationTest.java` (backend)
- FR-17 → `backend/src/test/java/com/oracul/app/research/EvidenceSelectorTest.java` (backend)
- FR-17 → `backend/src/test/java/com/oracul/app/research/RankingSelectionIT.java` (backend)
- FR-17 → `e2e/tests/evidence-pack.spec.ts` (e2e)
- FR-18 → `backend/src/test/java/com/oracul/app/research/EvidencePackAtomicIT.java` (backend)
- FR-18 → `backend/src/test/java/com/oracul/app/research/EvidencePackIT.java` (backend)
- FR-18 → `backend/src/test/java/com/oracul/app/research/EvidencePackPendingIT.java` (backend)
- FR-18 → `backend/src/test/java/com/oracul/app/research/EvidencePackRendererTest.java` (backend)
- FR-18 → `e2e/tests/evidence-pack.spec.ts` (e2e)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
2026-10-03T10:31:17.144+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T10:31:17.145+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-73 - Shutdown initiated...
2026-10-03T10:31:17.146+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-73 - Shutdown completed.
2026-10-03T10:31:17.261+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T10:31:17.262+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-74 - Shutdown initiated...
2026-10-03T10:31:17.262+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-74 - Shutdown completed.
2026-10-03T10:31:17.377+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T10:31:17.378+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-75 - Shutdown initiated...
2026-10-03T10:31:17.378+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-75 - Shutdown completed.
2026-10-03T10:31:17.505+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T10:31:17.505+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-76 - Shutdown initiated...
2026-10-03T10:31:17.506+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-76 - Shutdown completed.
2026-10-03T10:31:17.627+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T10:31:17.628+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-77 - Shutdown initiated...
2026-10-03T10:31:17.628+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-77 - Shutdown completed.
2026-10-03T10:31:17.749+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T10:31:17.750+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-78 - Shutdown initiated...
2026-10-03T10:31:17.751+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-78 - Shutdown completed.
2026-10-03T10:31:17.870+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T10:31:17.871+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-79 - Shutdown initiated...
2026-10-03T10:31:17.871+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-79 - Shutdown completed.
2026-10-03T10:31:18.008+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T10:31:18.009+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-80 - Shutdown initiated...
2026-10-03T10:31:18.009+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-80 - Shutdown completed.
2026-10-03T10:31:18.123+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T10:31:18.124+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-81 - Shutdown initiated...
2026-10-03T10:31:18.125+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-81 - Shutdown completed.
2026-10-03T10:31:18.247+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T10:31:18.248+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-82 - Shutdown initiated...
2026-10-03T10:31:18.249+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-82 - Shutdown completed.

> Task :test

> Task :test FAILED
5 actionable tasks: 1 executed, 4 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

715 tests completed, 101 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 3m 34s

```

RESULT: RED
