# Red evidence — 09_future-story

Date: 2026-10-03 · FRs: FR-23, FR-25

## Tagged tests

- FR-23 → `backend/src/test/java/com/oracul/app/result/DatelinesTest.java` (backend)
- FR-23 → `backend/src/test/java/com/oracul/app/result/FutureResultIT.java` (backend)
- FR-23 → `backend/src/test/java/com/oracul/app/result/FutureResultPendingIT.java` (backend)
- FR-23 → `backend/src/test/java/com/oracul/app/result/StoryParserTest.java` (backend)
- FR-23 → `backend/src/test/java/com/oracul/app/result/StoryWritingIT.java` (backend)
- FR-23 → `backend/src/test/java/com/oracul/app/result/StoryWritingPromptTest.java` (backend)
- FR-23 → `backend/src/test/java/com/oracul/app/result/StoryWritingStageIT.java` (backend)
- FR-23 → `e2e/tests/future-story.spec.ts` (e2e)
- FR-23 → `frontend/src/app/result/future-result.spec.ts` (frontend)
- FR-23 → `frontend/src/app/runs/run-view.spec.ts` (frontend)
- FR-25 → `backend/src/test/java/com/oracul/app/result/FutureResultIT.java` (backend)
- FR-25 → `backend/src/test/java/com/oracul/app/result/ScenarioMetadataMapperTest.java` (backend)
- FR-25 → `e2e/tests/future-story.spec.ts` (e2e)
- FR-25 → `frontend/src/app/result/scenario-metadata.spec.ts` (frontend)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
2026-10-03T15:29:57.922+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T15:29:57.923+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-76 - Shutdown initiated...
2026-10-03T15:29:57.924+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-76 - Shutdown completed.
2026-10-03T15:29:58.052+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T15:29:58.053+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-77 - Shutdown initiated...
2026-10-03T15:29:58.053+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-77 - Shutdown completed.
2026-10-03T15:29:58.181+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T15:29:58.182+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-78 - Shutdown initiated...
2026-10-03T15:29:58.183+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-78 - Shutdown completed.
2026-10-03T15:29:58.308+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T15:29:58.308+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-79 - Shutdown initiated...
2026-10-03T15:29:58.308+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-79 - Shutdown completed.
2026-10-03T15:29:58.433+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T15:29:58.433+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-80 - Shutdown initiated...
2026-10-03T15:29:58.434+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-80 - Shutdown completed.
2026-10-03T15:29:58.566+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T15:29:58.567+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-81 - Shutdown initiated...
2026-10-03T15:29:58.568+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-81 - Shutdown completed.
2026-10-03T15:29:58.707+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T15:29:58.708+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-82 - Shutdown initiated...
2026-10-03T15:29:58.708+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-82 - Shutdown completed.
2026-10-03T15:29:58.822+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T15:29:58.822+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-83 - Shutdown initiated...
2026-10-03T15:29:58.823+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-83 - Shutdown completed.
2026-10-03T15:29:58.944+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T15:29:58.945+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-84 - Shutdown initiated...
2026-10-03T15:29:58.945+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-84 - Shutdown completed.
2026-10-03T15:29:59.066+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T15:29:59.066+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-85 - Shutdown initiated...
2026-10-03T15:29:59.067+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-85 - Shutdown completed.

> Task :test

> Task :test FAILED
5 actionable tasks: 1 executed, 4 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

942 tests completed, 95 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 4m 32s

```

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
[36m [2m❯[22m show src/app/result/scenario-metadata.spec.ts:[2m79:10[22m[39m
    [90m 77|[39m     harness.detectChanges();
    [90m 78|[39m     await harness.fixture.whenStable();
    [90m 79|[39m     http.expectOne((r) => r.method === 'GET' && r.url.endsWith(`/api/r…
    [90m   |[39m          [31m^[39m
    [90m 80|[39m     harness.detectChanges();
    [90m 81|[39m     await harness.fixture.whenStable();
[90m [2m❯[22m src/app/result/scenario-metadata.spec.ts:[2m147:5[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[13/15]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/run-view.spec.ts[2m > [22mslice 09_future-story: run view switches to the result[2m > [22mCOMPLETED with a headline shows the future result and no progress view
[31m[1mAssertionError[22m: expected false to be true // Object.is equality[39m

- Expected
+ Received

- true
+ false

[36m [2m❯[22m src/app/runs/run-view.spec.ts:[2m256:38[22m[39m
    [90m254|[39m     await harness.fixture.whenStable();
    [90m255|[39m     harness.detectChanges();
    [90m256|[39m     expect(has('app-future-result')).toBe(true);
    [90m   |[39m                                      [31m^[39m
    [90m257|[39m     expect(has('[data-testid="progress-view"]')).toBe(false);
    [90m258|[39m     http.expectOne(isResultGet);

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[14/15]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/run-view.spec.ts[2m > [22mslice 09_future-story: run view switches to the result[2m > [22mpolling that sees COMPLETED with a headline replaces the progress view by the result within 2 s
[31m[1mAssertionError[22m: expected false to be true // Object.is equality[39m

- Expected
+ Received

- true
+ false

[36m [2m❯[22m src/app/runs/run-view.spec.ts:[2m296:38[22m[39m
    [90m294|[39m     await vi.advanceTimersByTimeAsync(0);
    [90m295|[39m     harness.detectChanges();
    [90m296|[39m     expect(has('app-future-result')).toBe(true);
    [90m   |[39m                                      [31m^[39m
    [90m297|[39m     expect(has('[data-testid="progress-view"]')).toBe(false);
    [90m298|[39m     http.expectOne(isResultGet);

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[15/15]⎯[22m[39m


```

RESULT: RED
