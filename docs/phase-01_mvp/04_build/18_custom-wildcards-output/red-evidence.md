# Red evidence — 18_custom-wildcards-output

Date: 2026-10-04 · FRs: FR-5, FR-6

## Tagged tests

- FR-5 → `backend/src/test/java/com/oracul/app/runs/CustomWildcardRunIT.java` (backend)
- FR-5 → `backend/src/test/java/com/oracul/app/runs/StartRunCustomWildcardValidationTest.java` (backend)
- FR-5 → `e2e/tests/custom-wildcards-output.spec.ts` (e2e)
- FR-5 → `frontend/src/app/scenario/custom-wildcards.spec.ts` (frontend)
- FR-5 → `frontend/src/app/scenario/scenario.store.custom.spec.ts` (frontend)
- FR-6 → `backend/src/test/java/com/oracul/app/runs/StartRunOutputValidationTest.java` (backend)
- FR-6 → `e2e/tests/custom-wildcards-output.spec.ts` (e2e)
- FR-6 → `frontend/src/app/scenario/output-settings.spec.ts` (frontend)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
2026-10-04T04:34:23.404+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-43 - Shutdown initiated...
2026-10-04T04:34:23.405+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-43 - Shutdown completed.
2026-10-04T04:34:23.415+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T04:34:23.416+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-44 - Shutdown initiated...
2026-10-04T04:34:23.416+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-44 - Shutdown completed.
2026-10-04T04:34:23.426+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T04:34:23.427+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-45 - Shutdown initiated...
2026-10-04T04:34:23.427+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-45 - Shutdown completed.
2026-10-04T04:34:40.497+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T04:34:40.498+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-88 - Shutdown initiated...
2026-10-04T04:34:40.498+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-88 - Shutdown completed.
2026-10-04T04:34:40.516+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T04:34:40.517+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-93 - Shutdown initiated...
2026-10-04T04:34:40.517+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-93 - Shutdown completed.
2026-10-04T04:34:40.529+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T04:34:40.529+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-94 - Shutdown initiated...
2026-10-04T04:34:40.530+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-94 - Shutdown completed.
2026-10-04T04:34:40.541+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T04:34:40.541+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-95 - Shutdown initiated...
2026-10-04T04:34:40.541+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-95 - Shutdown completed.
2026-10-04T04:34:40.553+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T04:34:40.553+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-96 - Shutdown initiated...
2026-10-04T04:34:40.553+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-96 - Shutdown completed.
2026-10-04T04:34:40.565+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T04:34:40.565+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-97 - Shutdown initiated...
2026-10-04T04:34:40.565+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-97 - Shutdown completed.
2026-10-04T04:34:40.576+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T04:34:40.576+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-98 - Shutdown initiated...
2026-10-04T04:34:40.590+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-98 - Shutdown completed.

> Task :test

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

1236 tests completed, 54 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 2m 41s

```

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/scenario/scenario.store.custom.spec.ts[2m > [22mScenarioStore custom wildcards[2m > [22mcheck order: count, then length, then duplicate
[31m[1mTypeError[22m: cw.addCustomWildcard is not a function[39m
[36m [2m❯[22m src/app/scenario/scenario.store.custom.spec.ts:[2m148:41[22m[39m
    [90m146|[39m   // @trace FR-5
    [90m147|[39m   it('check order: count, then length, then duplicate', () => {
    [90m148|[39m     for (const l of ['A', 'B', 'C']) cw.addCustomWildcard(l);
    [90m   |[39m                                         [31m^[39m
    [90m149|[39m     expect(cw.addCustomWildcard('a')).toBe(MAX);
    [90m150|[39m     cw.removeCustomWildcard(2);

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[28/57]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/scenario/scenario.store.custom.spec.ts[2m > [22mScenarioStore custom wildcards[2m > [22ma rejected add leaves the state unchanged
[31m[1mTypeError[22m: cw.addCustomWildcard is not a function[39m
[36m [2m❯[22m src/app/scenario/scenario.store.custom.spec.ts:[2m157:8[22m[39m
    [90m155|[39m   // @trace FR-5
    [90m156|[39m   it('a rejected add leaves the state unchanged', () => {
    [90m157|[39m     cw.addCustomWildcard('A');
    [90m   |[39m        [31m^[39m
    [90m158|[39m     const before = JSON.stringify(store.configuration());
    [90m159|[39m     cw.addCustomWildcard('');

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[29/57]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/scenario/scenario.store.custom.spec.ts[2m > [22mScenarioStore custom wildcards[2m > [22mcustom wildcard changes never touch other fields, and vice versa
[31m[1mTypeError[22m: cw.addCustomWildcard is not a function[39m
[36m [2m❯[22m src/app/scenario/scenario.store.custom.spec.ts:[2m176:8[22m[39m
    [90m174|[39m     };
    [90m175|[39m     const before = others();
    [90m176|[39m     cw.addCustomWildcard('A');
    [90m   |[39m        [31m^[39m
    [90m177|[39m     cw.addCustomWildcard('B');
    [90m178|[39m     cw.setCustomWildcardIntensity(1, 9);

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[30/57]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/scenario/scenario.store.custom.spec.ts[2m > [22mScenarioStore custom wildcards[2m > [22mload keeps customWildcards as given
[31m[1mTypeError[22m: cw.customWildcards is not a function[39m
[36m [2m❯[22m src/app/scenario/scenario.store.custom.spec.ts:[2m198:15[22m[39m
    [90m196|[39m     };
    [90m197|[39m     store.load(config);
    [90m198|[39m     expect(cw.customWildcards()).toEqual([{ label: 'Mars colony', inte…
    [90m   |[39m               [31m^[39m
    [90m199|[39m     expect(store.configuration()).toEqual(config);
    [90m200|[39m   });

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[31/57]⎯[22m[39m


```

RESULT: RED
