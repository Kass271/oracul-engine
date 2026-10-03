# Red evidence — 08_validated-scenario

Date: 2026-10-03 · FRs: FR-19, FR-20, FR-21

## Tagged tests

- FR-19 → `backend/src/test/java/com/oracul/app/chatgpt/HttpResponsesClientToolGuardTest.java` (backend)
- FR-19 → `backend/src/test/java/com/oracul/app/reasoning/ScenarioGenerationIT.java` (backend)
- FR-19 → `backend/src/test/java/com/oracul/app/reasoning/ScenarioGenerationPromptTest.java` (backend)
- FR-19 → `backend/src/test/java/com/oracul/app/reasoning/ScenarioStagesIT.java` (backend)
- FR-19 → `e2e/tests/validated-scenario.spec.ts` (e2e)
- FR-20 → `backend/src/test/java/com/oracul/app/reasoning/StructuredScenarioIT.java` (backend)
- FR-20 → `backend/src/test/java/com/oracul/app/reasoning/StructuredScenarioParserTest.java` (backend)
- FR-20 → `backend/src/test/java/com/oracul/app/reasoning/StructuredScenarioPendingIT.java` (backend)
- FR-20 → `e2e/tests/validated-scenario.spec.ts` (e2e)
- FR-21 → `backend/src/test/java/com/oracul/app/reasoning/EvidenceGuardIT.java` (backend)
- FR-21 → `backend/src/test/java/com/oracul/app/reasoning/EvidenceGuardTest.java` (backend)
- FR-21 → `e2e/tests/validated-scenario.spec.ts` (e2e)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
2026-10-03T14:00:01.339+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T14:00:01.339+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-73 - Shutdown initiated...
2026-10-03T14:00:01.340+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-73 - Shutdown completed.
2026-10-03T14:00:01.461+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T14:00:01.462+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-74 - Shutdown initiated...
2026-10-03T14:00:01.463+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-74 - Shutdown completed.
2026-10-03T14:00:01.607+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T14:00:01.607+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-75 - Shutdown initiated...
2026-10-03T14:00:01.608+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-75 - Shutdown completed.
2026-10-03T14:00:01.727+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T14:00:01.727+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-76 - Shutdown initiated...
2026-10-03T14:00:01.727+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-76 - Shutdown completed.
2026-10-03T14:00:01.865+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T14:00:01.866+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-77 - Shutdown initiated...
2026-10-03T14:00:01.866+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-77 - Shutdown completed.
2026-10-03T14:00:01.990+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T14:00:01.991+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-78 - Shutdown initiated...
2026-10-03T14:00:01.991+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-78 - Shutdown completed.
2026-10-03T14:00:02.105+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T14:00:02.106+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-79 - Shutdown initiated...
2026-10-03T14:00:02.107+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-79 - Shutdown completed.
2026-10-03T14:00:02.227+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T14:00:02.228+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-80 - Shutdown initiated...
2026-10-03T14:00:02.229+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-80 - Shutdown completed.
2026-10-03T14:00:02.344+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T14:00:02.345+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-81 - Shutdown initiated...
2026-10-03T14:00:02.345+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-81 - Shutdown completed.
2026-10-03T14:00:02.455+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T14:00:02.456+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-82 - Shutdown initiated...
2026-10-03T14:00:02.457+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-82 - Shutdown completed.

> Task :test

> Task :test FAILED
5 actionable tasks: 1 executed, 4 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

830 tests completed, 116 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 3m 47s

```

RESULT: RED
