# Red evidence — 04_run-start

Date: 2026-10-02 · FRs: FR-10, FR-11, FR-24

## Tagged tests

- FR-10 → `backend/src/test/java/com/oracul/app/runs/GenerationIdIT.java` (backend)
- FR-10 → `backend/src/test/java/com/oracul/app/runs/StartRunCompletedIT.java` (backend)
- FR-10 → `backend/src/test/java/com/oracul/app/runs/StartRunIT.java` (backend)
- FR-10 → `e2e/tests/run-start.spec.ts` (e2e)
- FR-10 → `frontend/src/app/runs/generate-button.spec.ts` (frontend)
- FR-10 → `frontend/src/app/runs/run.store.spec.ts` (frontend)
- FR-11 → `backend/src/test/java/com/oracul/app/research/ResearchProfileFactoryTest.java` (backend)
- FR-11 → `backend/src/test/java/com/oracul/app/research/RunResearchIT.java` (backend)
- FR-11 → `e2e/tests/run-start.spec.ts` (e2e)
- FR-24 → `backend/src/test/java/com/oracul/app/runs/GetRunIT.java` (backend)
- FR-24 → `backend/src/test/java/com/oracul/app/runs/GetRunProgressIT.java` (backend)
- FR-24 → `backend/src/test/java/com/oracul/app/runs/GetRunTerminalIT.java` (backend)
- FR-24 → `backend/src/test/java/com/oracul/app/runs/RunStageTest.java` (backend)
- FR-24 → `e2e/tests/run-start.spec.ts` (e2e)
- FR-24 → `frontend/src/app/runs/progress-view.spec.ts` (frontend)
- FR-24 → `frontend/src/app/runs/run-view.spec.ts` (frontend)
- FR-24 → `frontend/src/app/runs/run.store.spec.ts` (frontend)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
2026-10-03T00:32:55.397+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T00:32:55.398+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-3 - Shutdown initiated...
2026-10-03T00:32:55.398+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-3 - Shutdown completed.
2026-10-03T00:32:55.534+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T00:32:55.535+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-4 - Shutdown initiated...
2026-10-03T00:32:55.536+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-4 - Shutdown completed.
2026-10-03T00:32:55.653+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T00:32:55.654+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-5 - Shutdown initiated...
2026-10-03T00:32:55.660+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-5 - Shutdown completed.
2026-10-03T00:32:55.765+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T00:32:55.766+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-6 - Shutdown initiated...
2026-10-03T00:32:55.767+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-6 - Shutdown completed.
2026-10-03T00:32:55.888+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T00:32:55.889+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-7 - Shutdown initiated...
2026-10-03T00:32:55.889+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-7 - Shutdown completed.
2026-10-03T00:32:56.024+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T00:32:56.025+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-8 - Shutdown initiated...
2026-10-03T00:32:56.025+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-8 - Shutdown completed.
2026-10-03T00:32:56.134+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T00:32:56.134+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-9 - Shutdown initiated...
2026-10-03T00:32:56.134+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-9 - Shutdown completed.
2026-10-03T00:32:56.242+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T00:32:56.242+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-10 - Shutdown initiated...
2026-10-03T00:32:56.243+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-10 - Shutdown completed.
2026-10-03T00:32:56.390+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T00:32:56.390+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-15 - Shutdown initiated...
2026-10-03T00:32:56.391+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-15 - Shutdown completed.
2026-10-03T00:32:56.514+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T00:32:56.514+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-16 - Shutdown initiated...
2026-10-03T00:32:56.515+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-16 - Shutdown completed.

> Task :test

> Task :test FAILED
5 actionable tasks: 1 executed, 4 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

221 tests completed, 46 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 26s

```

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
    [90m   |[39m                      [31m^[39m
    [90m123|[39m     req.flush(run, { status: 202, statusText: 'Accepted' });
    [90m124|[39m     await tick(0);
[90m [2m❯[22m src/app/runs/run.store.spec.ts:[2m269:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[19/35]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/run.store.spec.ts[2m > [22mslice 04_run-start[2m > [22ma 5xx poll counts as a failure; 10 consecutive failures show backend-unavailable and pause polling
[31m[1mError[22m: Expected one matching request for criteria "Match by function: isRunsPost", found none.[39m
[90m [2m❯[22m HttpClientTestingBackend.expectOne ../darwin_arm64-fastbuild-ST-fdfa778d11ba/bin/packages/common/http/testing/src/backend.ts:[2m112:13[22m[39m
[36m [2m❯[22m generate src/app/runs/run.store.spec.ts:[2m122:22[22m[39m
    [90m120|[39m   async function generate(run: GenerationRun = queued()): Promise<Test…
    [90m121|[39m     click('generate-button');
    [90m122|[39m     const req = http.expectOne(isRunsPost);
    [90m   |[39m                      [31m^[39m
    [90m123|[39m     req.flush(run, { status: 202, statusText: 'Accepted' });
    [90m124|[39m     await tick(0);
[90m [2m❯[22m src/app/runs/run.store.spec.ts:[2m287:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[20/35]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/run.store.spec.ts[2m > [22mslice 04_run-start[2m > [22ma successful poll resets the failure counter
[31m[1mError[22m: Expected one matching request for criteria "Match by function: isRunsPost", found none.[39m
[90m [2m❯[22m HttpClientTestingBackend.expectOne ../darwin_arm64-fastbuild-ST-fdfa778d11ba/bin/packages/common/http/testing/src/backend.ts:[2m112:13[22m[39m
[36m [2m❯[22m generate src/app/runs/run.store.spec.ts:[2m122:22[22m[39m
    [90m120|[39m   async function generate(run: GenerationRun = queued()): Promise<Test…
    [90m121|[39m     click('generate-button');
    [90m122|[39m     const req = http.expectOne(isRunsPost);
    [90m   |[39m                      [31m^[39m
    [90m123|[39m     req.flush(run, { status: 202, statusText: 'Accepted' });
    [90m124|[39m     await tick(0);
[90m [2m❯[22m src/app/runs/run.store.spec.ts:[2m302:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[21/35]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/run.store.spec.ts[2m > [22mslice 04_run-start[2m > [22ma 404 poll shows the not-found failure view and stops polling
[31m[1mError[22m: Expected one matching request for criteria "Match by function: isRunsPost", found none.[39m
[90m [2m❯[22m HttpClientTestingBackend.expectOne ../darwin_arm64-fastbuild-ST-fdfa778d11ba/bin/packages/common/http/testing/src/backend.ts:[2m112:13[22m[39m
[36m [2m❯[22m generate src/app/runs/run.store.spec.ts:[2m122:22[22m[39m
    [90m120|[39m   async function generate(run: GenerationRun = queued()): Promise<Test…
    [90m121|[39m     click('generate-button');
    [90m122|[39m     const req = http.expectOne(isRunsPost);
    [90m   |[39m                      [31m^[39m
    [90m123|[39m     req.flush(run, { status: 202, statusText: 'Accepted' });
    [90m124|[39m     await tick(0);
[90m [2m❯[22m src/app/runs/run.store.spec.ts:[2m323:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[22/35]⎯[22m[39m


```

RESULT: RED
