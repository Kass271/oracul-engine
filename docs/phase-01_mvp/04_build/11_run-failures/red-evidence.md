# Red evidence — 11_run-failures

Date: 2026-10-03 · FRs: FR-32

## Tagged tests

- FR-32 → `backend/src/test/java/com/oracul/app/runs/RunDeadlineIT.java` (backend)
- FR-32 → `backend/src/test/java/com/oracul/app/runs/RunDeadlineQueuedIT.java` (backend)
- FR-32 → `backend/src/test/java/com/oracul/app/runs/RunDeadlineSchedulerIT.java` (backend)
- FR-32 → `backend/src/test/java/com/oracul/app/runs/RunFailureHygieneIT.java` (backend)
- FR-32 → `backend/src/test/java/com/oracul/app/runs/RunFailuresTest.java` (backend)
- FR-32 → `backend/src/test/java/com/oracul/app/runs/RunStartupSweepIT.java` (backend)
- FR-32 → `backend/src/test/java/com/oracul/app/runs/RunStartupSweepTest.java` (backend)
- FR-32 → `e2e/tests/run-failures.spec.ts` (e2e)
- FR-32 → `frontend/src/app/app.spec.ts` (frontend)
- FR-32 → `frontend/src/app/runs/run-failure.spec.ts` (frontend)
- FR-32 → `frontend/src/app/runs/run-view.spec.ts` (frontend)
- FR-32 → `frontend/src/app/runs/run.store.spec.ts` (frontend)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```

RunStartupSweepIT > anActiveRunIsFailedAsInterruptedAndTerminalRunsStay() FAILED
    java.lang.AssertionError at RunStartupSweepIT.java:42

RunStartupSweepIT > theInterruptedRunDoesNothingAfterTheGateIsReleased() FAILED
    java.lang.AssertionError at RunStartupSweepIT.java:56

2026-10-03T19:08:44.168+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T19:08:44.168+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-39 - Shutdown initiated...
2026-10-03T19:08:44.169+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-39 - Shutdown completed.
2026-10-03T19:08:44.186+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T19:08:44.186+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-40 - Shutdown initiated...
2026-10-03T19:08:44.187+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-40 - Shutdown completed.
2026-10-03T19:08:44.200+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T19:08:44.201+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-41 - Shutdown initiated...
2026-10-03T19:08:44.201+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-41 - Shutdown completed.
2026-10-03T19:08:44.212+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T19:08:44.213+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-42 - Shutdown initiated...
2026-10-03T19:08:44.213+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-42 - Shutdown completed.
2026-10-03T19:08:44.225+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T19:08:44.225+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-43 - Shutdown initiated...
2026-10-03T19:08:44.226+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-43 - Shutdown completed.
2026-10-03T19:08:44.238+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T19:08:44.238+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-44 - Shutdown initiated...
2026-10-03T19:08:44.239+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-44 - Shutdown completed.
2026-10-03T19:08:44.249+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T19:08:44.250+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-45 - Shutdown initiated...
2026-10-03T19:08:44.254+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-45 - Shutdown completed.

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

1021 tests completed, 27 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test' (registered by plugin 'org.gradle.jvm-test-suite').
> java.nio.file.NoSuchFileException: /Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/test-results/test/binary/in-progress-results-generic.bin

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights from a Build Scan (powered by Develocity).
> Get more help at https://help.gradle.org.

BUILD FAILED in 2m 57s

```

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
    [90m   |[39m                                                 [31m^[39m
    [90m387|[39m     expect(has('[data-testid="progress-view"]')).toBe(false);
    [90m388|[39m     expect(text('failure-message')).toBe('Generation took too long — t…

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[7/18]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/run-view.spec.ts[2m > [22mslice 11_run-failures[2m > [22mthe failure view never shows the code, the run id, the generation id, a URL or JSON
[31m[1mAssertionError[22m: expected '' not to be '' // Object.is equality[39m
[36m [2m❯[22m src/app/runs/run-view.spec.ts:[2m399:25[22m[39m
    [90m397|[39m     await render();
    [90m398|[39m     const content = el().querySelector('[data-testid="failure-view"]')…
    [90m399|[39m     expect(content).not.toBe('');
    [90m   |[39m                         [31m^[39m
    [90m400|[39m     for (const forbidden of ['RUN_TIMEOUT', ID, GENERATION_ID, 'http',…
    [90m401|[39m       expect(content).not.toContain(forbidden);

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[8/18]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/run-view.spec.ts[2m > [22mslice 11_run-failures[2m > [22mTry again of a FAILED run re-submits the failed run configuration
[31m[1mTypeError[22m: Cannot read properties of null (reading 'click')[39m
[36m [2m❯[22m src/app/runs/run-view.spec.ts:[2m410:52[22m[39m
    [90m408|[39m     http.expectOne(isRunGet).flush({ ...failed('RUN_TIMEOUT', 'Generat…
    [90m409|[39m     await render();
    [90m410|[39m     (el().querySelector('[data-testid="try-again"]') as HTMLButtonElem…
    [90m   |[39m                                                    [31m^[39m
    [90m411|[39m     const post = http.expectOne((r) => r.method === 'POST' && r.url.en…
    [90m412|[39m     expect(post.request.body).toEqual({ ...CATALOGUE.defaults, darknes…

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[9/18]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/run.store.spec.ts[2m > [22mslice 11_run-failures: session expiry refreshes the connection state[2m > [22mFAILED with CHATGPT_SESSION_EXPIRED loads the connection state exactly once, also after re-renders
[31m[1mAssertionError[22m: expected [] to have a length of 1 but got +0[39m

- Expected
+ Received

- 1
+ 0

[36m [2m❯[22m src/app/runs/run.store.spec.ts:[2m369:19[22m[39m
    [90m367|[39m     await render();
    [90m368|[39m     const first = http.match(isConnectionGet);
    [90m369|[39m     expect(first).toHaveLength(1);
    [90m   |[39m                   [31m^[39m
    [90m370|[39m     first[0].flush({ state: 'SESSION_EXPIRED', canGenerate: false });
    [90m371|[39m     await render();

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[10/18]⎯[22m[39m


```

RESULT: RED
