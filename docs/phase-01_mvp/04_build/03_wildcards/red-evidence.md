# Red evidence — 03_wildcards

Date: 2026-10-02 · FRs: FR-4

## Tagged tests

- FR-4 → `backend/src/test/java/com/oracul/app/runs/StartRunWildcardValidationTest.java` (backend)
- FR-4 → `backend/src/test/java/com/oracul/app/scenario/WildcardCatalogueTest.java` (backend)
- FR-4 → `e2e/tests/wildcards.spec.ts` (e2e)
- FR-4 → `frontend/src/app/scenario/scenario.store.wildcards.spec.ts` (frontend)
- FR-4 → `frontend/src/app/scenario/wildcard-catalogue.spec.ts` (frontend)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
StartRunWildcardValidationTest > badIntensityIsRejected(String) > [7] raw = "true" FAILED
    java.lang.AssertionError at StartRunWildcardValidationTest.java:68

StartRunWildcardValidationTest > emptyWildcardIdIsUnknown() FAILED
    java.lang.AssertionError at StartRunWildcardValidationTest.java:68

2026-10-03T00:14:28.604+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T00:14:28.605+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2026-10-03T00:14:28.605+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.
2026-10-03T00:14:28.731+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T00:14:28.731+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-2 - Shutdown initiated...
2026-10-03T00:14:28.731+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-2 - Shutdown completed.
2026-10-03T00:14:28.858+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T00:14:28.858+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-3 - Shutdown initiated...
2026-10-03T00:14:28.859+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-3 - Shutdown completed.
2026-10-03T00:14:29.011+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T00:14:29.011+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-4 - Shutdown initiated...
2026-10-03T00:14:29.012+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-4 - Shutdown completed.
2026-10-03T00:14:29.138+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T00:14:29.138+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-5 - Shutdown initiated...
2026-10-03T00:14:29.139+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-5 - Shutdown completed.
2026-10-03T00:14:29.257+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T00:14:29.258+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-6 - Shutdown initiated...
2026-10-03T00:14:29.259+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-6 - Shutdown completed.
2026-10-03T00:14:29.378+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T00:14:29.379+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-11 - Shutdown initiated...
2026-10-03T00:14:29.379+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-11 - Shutdown completed.
2026-10-03T00:14:29.496+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T00:14:29.496+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-12 - Shutdown initiated...
2026-10-03T00:14:29.497+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-12 - Shutdown completed.

> Task :test

> Task :test FAILED
5 actionable tasks: 1 executed, 4 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

174 tests completed, 18 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 21s

```

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
    [90m170|[39m     const input = byId(`wildcard-intensity-${PANDEMIC}-input`) as HTML…

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[16/19]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/scenario/wildcard-catalogue.spec.ts[2m > [22mWildcard catalogue in the Scenario Panel (slice 03_wildcards)[2m > [22mmoving the slider to 8 shows "New pandemic 8/10" and leaves other wildcards alone
[31m[1mTypeError[22m: Cannot set properties of null (setting 'value')[39m
[36m [2m❯[22m setIntensity src/app/scenario/wildcard-catalogue.spec.ts:[2m90:11[22m[39m
    [90m 88|[39m   async function setIntensity(id: string, value: number): Promise<void…
    [90m 89|[39m     const input = byId(`wildcard-intensity-${id}-input`) as HTMLInputE…
    [90m 90|[39m     input.value = String(value);
    [90m   |[39m           [31m^[39m
    [90m 91|[39m     input.dispatchEvent(new Event('input', { bubbles: true }));
    [90m 92|[39m     input.dispatchEvent(new Event('change', { bubbles: true }));
[90m [2m❯[22m src/app/scenario/wildcard-catalogue.spec.ts:[2m183:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[17/19]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/scenario/wildcard-catalogue.spec.ts[2m > [22mWildcard catalogue in the Scenario Panel (slice 03_wildcards)[2m > [22mdisabling removes the slider and the intensity; re-enabling starts again at 5
[31m[1mTypeError[22m: Cannot set properties of null (setting 'value')[39m
[36m [2m❯[22m setIntensity src/app/scenario/wildcard-catalogue.spec.ts:[2m90:11[22m[39m
    [90m 88|[39m   async function setIntensity(id: string, value: number): Promise<void…
    [90m 89|[39m     const input = byId(`wildcard-intensity-${id}-input`) as HTMLInputE…
    [90m 90|[39m     input.value = String(value);
    [90m   |[39m           [31m^[39m
    [90m 91|[39m     input.dispatchEvent(new Event('input', { bubbles: true }));
    [90m 92|[39m     input.dispatchEvent(new Event('change', { bubbles: true }));
[90m [2m❯[22m src/app/scenario/wildcard-catalogue.spec.ts:[2m193:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[18/19]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/scenario/wildcard-catalogue.spec.ts[2m > [22mWildcard catalogue in the Scenario Panel (slice 03_wildcards)[2m > [22mloads wildcards of the current configuration as enabled rows with their intensities
[31m[1mAssertionError[22m: expected undefined to be 'true' // Object.is equality[39m

- Expected:
"true"

+ Received:
undefined

[36m [2m❯[22m src/app/scenario/wildcard-catalogue.spec.ts:[2m213:56[22m[39m
    [90m211|[39m       ],
    [90m212|[39m     });
    [90m213|[39m     expect(sw(PANDEMIC)?.getAttribute('aria-checked')).toBe('true');
    [90m   |[39m                                                        [31m^[39m
    [90m214|[39m     expect(text(`wildcard-label-${PANDEMIC}`)).toBe('New pandemic 8/10…
    [90m215|[39m     expect((byId(`wildcard-intensity-${PANDEMIC}-input`) as HTMLInputE…

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[19/19]⎯[22m[39m


```

RESULT: RED
