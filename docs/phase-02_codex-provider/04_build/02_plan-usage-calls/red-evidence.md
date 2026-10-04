# Red evidence — 02_plan-usage-calls

Date: 2026-10-04 · FRs: FR-38, FR-39, FR-40, FR-44

## Tagged tests

- FR-38 → `backend/src/test/java/com/oracul/app/chatgpt/ChatGptConnectionIT.java` (backend)
- FR-38 → `backend/src/test/java/com/oracul/app/chatgpt/ChatGptPropertiesTest.java` (backend)
- FR-38 → `backend/src/test/java/com/oracul/app/reasoning/CriticIT.java` (backend)
- FR-38 → `backend/src/test/java/com/oracul/app/reasoning/ScenarioGenerationIT.java` (backend)
- FR-38 → `backend/src/test/java/com/oracul/app/reasoning/StructuredScenarioIT.java` (backend)
- FR-38 → `backend/src/test/java/com/oracul/app/research/EventClassificationIT.java` (backend)
- FR-38 → `backend/src/test/java/com/oracul/app/research/EventFailureIT.java` (backend)
- FR-38 → `backend/src/test/java/com/oracul/app/research/EventNormalizationIT.java` (backend)
- FR-38 → `backend/src/test/java/com/oracul/app/research/ModelResolutionIT.java` (backend)
- FR-38 → `backend/src/test/java/com/oracul/app/research/PlanUsageTransportIT.java` (backend)
- FR-38 → `backend/src/test/java/com/oracul/app/research/ResearchPlanIT.java` (backend)
- FR-38 → `backend/src/test/java/com/oracul/app/research/StreamTimeoutIT.java` (backend)
- FR-38 → `backend/src/test/java/com/oracul/app/result/StoryWritingIT.java` (backend)
- FR-38 → `backend/src/test/java/com/oracul/app/runs/RefreshBoundaryIT.java` (backend)
- FR-38 → `e2e/tests/plan-usage-calls.spec.ts` (e2e)
- FR-38 → `frontend/src/app/result/meta-model.spec.ts` (frontend)
- FR-39 → `backend/src/test/java/com/oracul/app/reasoning/CriticIT.java` (backend)
- FR-39 → `backend/src/test/java/com/oracul/app/reasoning/ScenarioGenerationIT.java` (backend)
- FR-39 → `backend/src/test/java/com/oracul/app/research/ErrorClassificationIT.java` (backend)
- FR-39 → `backend/src/test/java/com/oracul/app/research/EventFailureIT.java` (backend)
- FR-39 → `backend/src/test/java/com/oracul/app/research/EventNormalizationIT.java` (backend)
- FR-39 → `backend/src/test/java/com/oracul/app/research/EventRunGuardIT.java` (backend)
- FR-39 → `backend/src/test/java/com/oracul/app/research/EventTimeoutIT.java` (backend)
- FR-39 → `backend/src/test/java/com/oracul/app/research/ResearchPlanIT.java` (backend)
- FR-39 → `backend/src/test/java/com/oracul/app/result/StoryWritingIT.java` (backend)
- FR-39 → `backend/src/test/java/com/oracul/app/runs/RunFailuresTest.java` (backend)
- FR-39 → `e2e/tests/plan-usage-calls.spec.ts` (e2e)
- FR-39 → `e2e/tests/run-failures.spec.ts` (e2e)
- FR-39 → `frontend/src/app/runs/run-failure-extras.spec.ts` (frontend)
- FR-40 → `backend/src/test/java/com/oracul/app/chatgpt/ChatGptAuthServiceTest.java` (backend)
- FR-40 → `backend/src/test/java/com/oracul/app/research/RefreshFailureIT.java` (backend)
- FR-40 → `e2e/tests/plan-usage-calls.spec.ts` (e2e)
- FR-40 → `frontend/src/app/runs/run-failure-extras.spec.ts` (frontend)
- FR-40 → `frontend/src/app/runs/run-store-connection.spec.ts` (frontend)
- FR-44 → `backend/src/test/java/com/oracul/app/research/NewsSearchAttributionIT.java` (backend)
- FR-44 → `backend/src/test/java/com/oracul/app/research/NewsSearchBudgetIT.java` (backend)
- FR-44 → `backend/src/test/java/com/oracul/app/research/NewsSearchDeadlineIT.java` (backend)
- FR-44 → `backend/src/test/java/com/oracul/app/research/NewsSearchGroupingIT.java` (backend)
- FR-44 → `backend/src/test/java/com/oracul/app/research/NewsSearchGroupingMax0IT.java` (backend)
- FR-44 → `backend/src/test/java/com/oracul/app/research/NewsSearchGroupingMax10IT.java` (backend)
- FR-44 → `backend/src/test/java/com/oracul/app/research/NewsSearchGroupingMax1IT.java` (backend)
- FR-44 → `backend/src/test/java/com/oracul/app/research/NewsSearchGroupingMaxMinus1IT.java` (backend)
- FR-44 → `backend/src/test/java/com/oracul/app/research/NewsSearchNoBudgetIT.java` (backend)
- FR-44 → `backend/src/test/java/com/oracul/app/research/NewsSearchRunIT.java` (backend)
- FR-44 → `backend/src/test/java/com/oracul/app/research/NewsSearchTimingIT.java` (backend)
- FR-44 → `backend/src/test/java/com/oracul/app/research/ResearchPlanIT.java` (backend)
- FR-44 → `backend/src/test/java/com/oracul/app/research/ResearchPlanTimeoutIT.java` (backend)
- FR-44 → `backend/src/test/java/com/oracul/app/research/SourceFixtureIT.java` (backend)
- FR-44 → `backend/src/test/java/com/oracul/app/research/SourceQueryTimeoutIT.java` (backend)
- FR-44 → `backend/src/test/java/com/oracul/app/research/SourceRetrievalIT.java` (backend)
- FR-44 → `e2e/tests/search-sources.spec.ts` (e2e)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
2026-10-04T13:19:01.684+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-121 - Shutdown initiated...
2026-10-04T13:19:01.684+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-121 - Shutdown completed.
2026-10-04T13:19:01.697+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T13:19:01.697+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-122 - Shutdown initiated...
2026-10-04T13:19:01.697+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-122 - Shutdown completed.
2026-10-04T13:19:01.708+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T13:19:01.708+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-123 - Shutdown initiated...
2026-10-04T13:19:01.708+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-123 - Shutdown completed.
2026-10-04T13:19:57.164+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T13:19:57.165+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-43 - Shutdown initiated...
2026-10-04T13:19:57.166+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-43 - Shutdown completed.
2026-10-04T13:19:57.184+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T13:19:57.184+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-44 - Shutdown initiated...
2026-10-04T13:19:57.184+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-44 - Shutdown completed.
2026-10-04T13:19:57.196+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T13:19:57.196+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-45 - Shutdown initiated...
2026-10-04T13:19:57.196+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-45 - Shutdown completed.
2026-10-04T13:19:57.207+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T13:19:57.207+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-46 - Shutdown initiated...
2026-10-04T13:19:57.208+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-46 - Shutdown completed.
2026-10-04T13:19:57.219+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T13:19:57.219+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-47 - Shutdown initiated...
2026-10-04T13:19:57.220+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-47 - Shutdown completed.
2026-10-04T13:19:57.230+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T13:19:57.230+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-48 - Shutdown initiated...
2026-10-04T13:19:57.231+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-48 - Shutdown completed.
2026-10-04T13:19:57.242+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T13:19:57.243+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-49 - Shutdown initiated...
2026-10-04T13:19:57.254+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-49 - Shutdown completed.

> Task :test

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

1924 tests completed, 540 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 3m 30s

```

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```

[36m [2m❯[22m src/app/runs/run-store-connection.spec.ts:[2m77:41[22m[39m
    [90m 75|[39m     await settle();
    [90m 76|[39m     expect(snackText()).toBe('ChatGPT registration is no longer valid …
    [90m 77|[39m     expect(http.match(isConnectionGet)).toHaveLength(1);
    [90m   |[39m                                         [31m^[39m
    [90m 78|[39m   });
    [90m 79|[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[20/24]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/run-store-connection.spec.ts[2m > [22mslice 02_plan-usage-calls: starting a run reloads the connection for the new codes[2m > [22mstartRun error CHATGPT_REGISTRATION_INVALID (401): ChatGPT registration is no longer valid — use Reset ChatGPT connection, then reconnect -> connection loads: 1
[31m[1mAssertionError[22m: expected [] to have a length of 1 but got +0[39m

- Expected
+ Received

- 1
+ 0

[36m [2m❯[22m src/app/runs/run-store-connection.spec.ts:[2m121:41[22m[39m
    [90m119|[39m     await settle();
    [90m120|[39m     expect(snackText()).toBe(message);
    [90m121|[39m     expect(http.match(isConnectionGet)).toHaveLength(loads as number);
    [90m   |[39m                                         [31m^[39m
    [90m122|[39m   });
    [90m123|[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[21/24]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/run-store-connection.spec.ts[2m > [22mslice 02_plan-usage-calls: starting a run reloads the connection for the new codes[2m > [22ma FAILED run with CHATGPT_REGISTRATION_INVALID reloads the connection once when it is opened
[31m[1mAssertionError[22m: expected [] to have a length of 1 but got +0[39m

- Expected
+ Received

- 1
+ 0

[36m [2m❯[22m src/app/runs/run-store-connection.spec.ts:[2m142:41[22m[39m
    [90m140|[39m     });
    [90m141|[39m     await settle();
    [90m142|[39m     expect(http.match(isConnectionGet)).toHaveLength(1);
    [90m   |[39m                                         [31m^[39m
    [90m143|[39m   });
    [90m144|[39m });

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[22/24]⎯[22m[39m


```

RESULT: RED
