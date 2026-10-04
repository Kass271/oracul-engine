# Red evidence — 17_alternative-future

Date: 2026-10-04 · FRs: FR-30

## Tagged tests

- FR-30 → `backend/src/test/java/com/oracul/app/reasoning/AlternativeDistinctnessTest.java` (backend)
- FR-30 → `backend/src/test/java/com/oracul/app/reasoning/ScenarioGenerationPromptTest.java` (backend)
- FR-30 → `backend/src/test/java/com/oracul/app/runs/AlternativeRunIT.java` (backend)
- FR-30 → `backend/src/test/java/com/oracul/app/runs/AlternativeRunStagesIT.java` (backend)
- FR-30 → `e2e/tests/alternative-future.spec.ts` (e2e)
- FR-30 → `frontend/src/app/runs/quick-actions.spec.ts` (frontend)
- FR-30 → `frontend/src/app/runs/run.store.spec.ts` (frontend)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
2026-10-04T03:37:48.435+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-94 - Shutdown initiated...
2026-10-04T03:37:48.435+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-94 - Shutdown completed.
2026-10-04T03:37:48.445+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T03:37:48.446+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-95 - Shutdown initiated...
2026-10-04T03:37:48.446+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-95 - Shutdown completed.
2026-10-04T03:37:48.456+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T03:37:48.457+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-96 - Shutdown initiated...
2026-10-04T03:37:48.472+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-96 - Shutdown completed.
2026-10-04T03:38:06.234+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T03:38:06.234+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-40 - Shutdown initiated...
2026-10-04T03:38:06.235+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-40 - Shutdown completed.
2026-10-04T03:38:06.249+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T03:38:06.250+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-41 - Shutdown initiated...
2026-10-04T03:38:06.250+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-41 - Shutdown completed.
2026-10-04T03:38:06.263+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T03:38:06.264+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-42 - Shutdown initiated...
2026-10-04T03:38:06.265+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-42 - Shutdown completed.
2026-10-04T03:38:06.277+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T03:38:06.277+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-43 - Shutdown initiated...
2026-10-04T03:38:06.277+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-43 - Shutdown completed.
2026-10-04T03:38:06.290+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T03:38:06.291+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-44 - Shutdown initiated...
2026-10-04T03:38:06.291+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-44 - Shutdown completed.
2026-10-04T03:38:06.302+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T03:38:06.302+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-45 - Shutdown initiated...
2026-10-04T03:38:06.303+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-45 - Shutdown completed.
2026-10-04T03:38:06.313+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T03:38:06.314+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-46 - Shutdown initiated...
2026-10-04T03:38:06.314+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-46 - Shutdown completed.

> Task :test

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

1149 tests completed, 39 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 2m 32s

```

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
    [90m321|[39m     expect(b, 'quick-alternative exists').not.toBeNull();
    [90m   |[39m                                               [31m^[39m
    [90m322|[39m     return b as HTMLButtonElement;
    [90m323|[39m   };
[90m [2m❯[22m click src/app/runs/quick-actions.spec.ts:[2m346:5[22m[39m
[90m [2m❯[22m src/app/runs/quick-actions.spec.ts:[2m452:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[8/14]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/quick-actions.spec.ts[2m > [22mslice 17_alternative-future: ALTERNATIVE FUTURE button[2m > [22mnetwork error (status 0): generic message
[31m[1mAssertionError[22m: quick-alternative exists: expected null not to be null[39m
[36m [2m❯[22m alt src/app/runs/quick-actions.spec.ts:[2m321:47[22m[39m
    [90m319|[39m   const alt = (): HTMLButtonElement => {
    [90m320|[39m     const b = el().querySelector('[data-testid="quick-alternative"]');
    [90m321|[39m     expect(b, 'quick-alternative exists').not.toBeNull();
    [90m   |[39m                                               [31m^[39m
    [90m322|[39m     return b as HTMLButtonElement;
    [90m323|[39m   };
[90m [2m❯[22m click src/app/runs/quick-actions.spec.ts:[2m346:5[22m[39m
[90m [2m❯[22m src/app/runs/quick-actions.spec.ts:[2m465:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[9/14]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/run.store.spec.ts[2m > [22mslice 17_alternative-future[2m > [22mstartAlternative sends one POST /api/runs/P/alternatives; 202 sets run() and polls the new run after 1000 ms
[31m[1mTypeError[22m: store.startAlternative is not a function[39m
[36m [2m❯[22m startAlt src/app/runs/run.store.spec.ts:[2m465:120[22m[39m
    [90m463|[39m   const altRun = (): GenerationRun => ({ ...queued(), id: NEW_ID, kind…
    [90m464|[39m
    [90m465|[39m   const startAlt = (store: RunStore, id: string): void => (store as un…
    [90m   |[39m                                                                                                                        [31m^[39m
    [90m466|[39m
    [90m467|[39m   beforeEach(() => {
[90m [2m❯[22m src/app/runs/run.store.spec.ts:[2m482:5[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[10/14]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/run.store.spec.ts[2m > [22mslice 17_alternative-future[2m > [22ma call while starting() sends nothing
[31m[1mTypeError[22m: store.startAlternative is not a function[39m
[36m [2m❯[22m startAlt src/app/runs/run.store.spec.ts:[2m465:120[22m[39m
    [90m463|[39m   const altRun = (): GenerationRun => ({ ...queued(), id: NEW_ID, kind…
    [90m464|[39m
    [90m465|[39m   const startAlt = (store: RunStore, id: string): void => (store as un…
    [90m   |[39m                                                                                                                        [31m^[39m
    [90m466|[39m
    [90m467|[39m   beforeEach(() => {
[90m [2m❯[22m src/app/runs/run.store.spec.ts:[2m493:5[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[11/14]⎯[22m[39m


```

RESULT: RED
