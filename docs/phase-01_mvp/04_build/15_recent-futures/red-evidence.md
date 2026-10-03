# Red evidence — 15_recent-futures

Date: 2026-10-03 · FRs: FR-33

## Tagged tests

- FR-33 → `backend/src/test/java/com/oracul/app/history/RecentRunsIT.java` (backend)
- FR-33 → `e2e/tests/recent-futures.spec.ts` (e2e)
- FR-33 → `frontend/src/app/history/recent-futures.spec.ts` (frontend)
- FR-33 → `frontend/src/app/runs/run.store.spec.ts` (frontend)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
2026-10-04T00:46:30.354+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-43 - Shutdown initiated...
2026-10-04T00:46:30.354+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-43 - Shutdown completed.
2026-10-04T00:46:30.366+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T00:46:30.366+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-44 - Shutdown initiated...
2026-10-04T00:46:30.366+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-44 - Shutdown completed.
2026-10-04T00:46:30.379+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T00:46:30.379+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-45 - Shutdown initiated...
2026-10-04T00:46:30.413+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-45 - Shutdown completed.
2026-10-04T00:46:37.948+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T00:46:37.949+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-88 - Shutdown initiated...
2026-10-04T00:46:37.949+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-88 - Shutdown completed.
2026-10-04T00:46:37.962+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T00:46:37.963+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-91 - Shutdown initiated...
2026-10-04T00:46:37.963+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-91 - Shutdown completed.
2026-10-04T00:46:37.975+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T00:46:37.975+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-92 - Shutdown initiated...
2026-10-04T00:46:37.976+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-92 - Shutdown completed.
2026-10-04T00:46:37.987+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T00:46:37.987+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-93 - Shutdown initiated...
2026-10-04T00:46:37.987+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-93 - Shutdown completed.
2026-10-04T00:46:37.998+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T00:46:37.998+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-94 - Shutdown initiated...
2026-10-04T00:46:37.999+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-94 - Shutdown completed.
2026-10-04T00:46:38.009+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T00:46:38.009+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-95 - Shutdown initiated...
2026-10-04T00:46:38.010+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-95 - Shutdown completed.
2026-10-04T00:46:38.020+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T00:46:38.021+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-96 - Shutdown initiated...
2026-10-04T00:46:38.021+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-96 - Shutdown completed.

> Task :test

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

1108 tests completed, 10 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 2m 18s

```

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
    [90m 86|[39m
    [90m 87|[39m   async function open(): Promise<TestRequest> {
    [90m 88|[39m     (fixture.nativeElement.querySelector('[data-testid="recent-futures…
    [90m   |[39m                                                                                 [31m^[39m
    [90m 89|[39m     await settle();
    [90m 90|[39m     const reqs = http.match(isList);
[90m [2m❯[22m openWith src/app/history/recent-futures.spec.ts:[2m96:23[22m[39m
[90m [2m❯[22m src/app/history/recent-futures.spec.ts:[2m240:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[16/26]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/run.store.spec.ts[2m > [22mslice 15_recent-futures: reopening a run loads its configuration into the panel[2m > [22mopen() puts the run configuration into the ScenarioStore on the first successful getRun
[31m[1mAssertionError[22m: expected 5 to be 9 // Object.is equality[39m

- Expected
+ Received

- 9
+ 5

[36m [2m❯[22m src/app/runs/run.store.spec.ts:[2m418:30[22m[39m
    [90m416|[39m     expect(panel.darkness()).toBe(5);
    [90m417|[39m     http.expectOne(isRunGet).flush(runWith(9, 'COMPLETED'));
    [90m418|[39m     expect(panel.darkness()).toBe(9);
    [90m   |[39m                              [31m^[39m
    [90m419|[39m     expect(panel.horizon()).toBe('5y');
    [90m420|[39m     expect(panel.configuration()).toEqual(config(9));

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[17/26]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/run.store.spec.ts[2m > [22mslice 15_recent-futures: reopening a run loads its configuration into the panel[2m > [22ma later poll with another configuration does not change the panel
[31m[1mAssertionError[22m: expected 5 to be 9 // Object.is equality[39m

- Expected
+ Received

- 9
+ 5

[36m [2m❯[22m src/app/runs/run.store.spec.ts:[2m428:30[22m[39m
    [90m426|[39m     TestBed.inject(RunStore).open(ID);
    [90m427|[39m     http.expectOne(isRunGet).flush(runWith(9));
    [90m428|[39m     expect(panel.darkness()).toBe(9);
    [90m   |[39m                              [31m^[39m
    [90m429|[39m     panel.setDarkness(4);
    [90m430|[39m     await vi.advanceTimersByTimeAsync(POLL_MS);

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[18/26]⎯[22m[39m


```

RESULT: RED
