# Red evidence — 04_wildcard-queries

Date: 2026-10-07 · FRs: FR-50, FR-51
Scope: slice (related tests only — the slice gate runs the full suite)

## Tagged tests

- FR-50 → `backend/src/test/java/com/oracul/app/research/GoogleNewsRunIT.java` (backend)
- FR-50 → `backend/src/test/java/com/oracul/app/research/PipelineAttributionIT.java` (backend)
- FR-50 → `backend/src/test/java/com/oracul/app/research/RemovedNewsPropertiesIT.java` (backend)
- FR-50 → `backend/src/test/java/com/oracul/app/research/ResearchPlanIT.java` (backend)
- FR-50 → `backend/src/test/java/com/oracul/app/research/ResearchPlanPendingIT.java` (backend)
- FR-50 → `backend/src/test/java/com/oracul/app/research/SearchPlannerTest.java` (backend)
- FR-50 → `backend/src/test/java/com/oracul/app/research/SourceFixtureIT.java` (backend)
- FR-50 → `backend/src/test/java/com/oracul/app/research/SourceQueryTimeoutIT.java` (backend)
- FR-50 → `backend/src/test/java/com/oracul/app/research/SourceRetrievalIT.java` (backend)
- FR-50 → `backend/src/test/java/com/oracul/app/result/OptionalWireFieldsAbsentIT.java` (backend)
- FR-50 → `backend/src/test/java/com/oracul/app/runs/AlternativeRunIT.java` (backend)
- FR-50 → `backend/src/test/java/com/oracul/app/runs/CustomWildcardRunIT.java` (backend)
- FR-50 → `backend/src/test/java/com/oracul/app/runs/GetRunTerminalIT.java` (backend)
- FR-50 → `e2e/tests/alternative-future.spec.ts` (e2e)
- FR-50 → `e2e/tests/events.spec.ts` (e2e)
- FR-50 → `e2e/tests/evidence-pack.spec.ts` (e2e)
- FR-50 → `e2e/tests/insufficient-evidence.spec.ts` (e2e)
- FR-50 → `e2e/tests/search-sources.spec.ts` (e2e)
- FR-50 → `e2e/tests/why-these-news.spec.ts` (e2e)
- FR-51 → `backend/src/test/java/com/oracul/app/NewsProviderScanTest.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/reasoning/StructuredScenarioIT.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/research/ErrorClassificationIT.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/research/NewsSearchNoBudgetIT.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/research/PlanUsageTransportIT.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/research/QueryGenerationParallelIT.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/research/QueryGenerationPromptTest.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/research/QueryGenerationSerialIT.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/research/QueryGenerationWindowIT.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/research/QueryGeneratorMergeTest.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/research/QueryGeneratorStartupTest.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/research/QueryRulesTest.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/research/QueryTemplatesTest.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/research/ResearchPlanIT.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/research/ResearchPlanTimeoutIT.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/research/StreamTimeoutIT.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/runs/AlternativeRunIT.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/runs/RunDeadlineIT.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/runs/RunDeadlineQueuedIT.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/runs/RunStartupSweepIT.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/runs/StopQueuedRunIT.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/runs/StopRunIT.java` (backend)
- FR-51 → `backend/src/test/java/com/oracul/app/runs/StoppedRunDeadlineIT.java` (backend)
- FR-51 → `e2e/tests/plan-usage-fallback.spec.ts` (e2e)
- FR-51 → `e2e/tests/search-sources.spec.ts` (e2e)

## Red by startup only (reviewer: check these tests — each fails only because the context does not start)

- com.oracul.app.research.RemovedNewsPropertiesIT.allRemovedPropertiesSetAtOnceAreIgnoredAndTheRunBehavesAsWithoutThem(): the Spring context does not start because production code is missing (java.lang.IllegalStateException: oracul.research.query-budget must be between 4 and 100; named in the spec as "ResearchPipeline")

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
> Task :test

StopRunIT > aStopDuringAnOutboundRequestEndsTheRunForGood(String, boolean, boolean) > stop while the "QUERY_GENERATION" request is in flight FAILED
    org.opentest4j.AssertionFailedError at StopRunIT.java:368

2026-10-07T06:20:10.428+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T06:20:10.428+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-4 - Shutdown initiated...
2026-10-07T06:20:10.429+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-4 - Shutdown completed.
2026-10-07T06:20:10.459+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T06:20:10.459+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-5 - Shutdown initiated...
2026-10-07T06:20:10.460+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-5 - Shutdown completed.
2026-10-07T06:20:10.476+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T06:20:10.477+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-7 - Shutdown initiated...
2026-10-07T06:20:10.477+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-7 - Shutdown completed.
2026-10-07T06:20:10.492+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T06:20:10.493+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-8 - Shutdown initiated...
2026-10-07T06:20:10.493+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-8 - Shutdown completed.
2026-10-07T06:20:10.507+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T06:20:10.507+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-9 - Shutdown initiated...
2026-10-07T06:20:10.507+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-9 - Shutdown completed.
2026-10-07T06:20:10.521+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T06:20:10.521+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-10 - Shutdown initiated...
2026-10-07T06:20:10.521+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-10 - Shutdown completed.
2026-10-07T06:20:10.535+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T06:20:10.535+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-11 - Shutdown initiated...
2026-10-07T06:20:10.536+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-11 - Shutdown completed.
2026-10-07T06:20:10.550+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T06:20:10.550+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-12 - Shutdown initiated...
2026-10-07T06:20:10.551+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-12 - Shutdown completed.

> Task :test

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

1026 tests completed, 676 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 2m 20s

```

RESULT: RED
