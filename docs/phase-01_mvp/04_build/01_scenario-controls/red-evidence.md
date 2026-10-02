# Red evidence — 01_scenario-controls

Date: 2026-10-02 · FRs: FR-1, FR-2, FR-3

## Tagged tests

- FR-1 → `backend/src/test/java/com/oracul/app/scenario/ScenarioControllerTest.java` (backend)
- FR-1 → `e2e/tests/scenario-controls.spec.ts` (e2e)
- FR-1 → `frontend/src/app/app.spec.ts` (frontend)
- FR-2 → `backend/src/test/java/com/oracul/app/runs/StartRunValidationTest.java` (backend)
- FR-2 → `backend/src/test/java/com/oracul/app/scenario/ScenarioControllerTest.java` (backend)
- FR-2 → `e2e/tests/scenario-controls.spec.ts` (e2e)
- FR-2 → `frontend/src/app/app.spec.ts` (frontend)
- FR-3 → `backend/src/test/java/com/oracul/app/runs/StartRunValidationTest.java` (backend)
- FR-3 → `backend/src/test/java/com/oracul/app/scenario/ScenarioControllerTest.java` (backend)
- FR-3 → `e2e/tests/scenario-controls.spec.ts` (e2e)
- FR-3 → `frontend/src/app/app.spec.ts` (frontend)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
    java.lang.AssertionError at StartRunValidationTest.java:53

StartRunValidationTest > unknownHorizonIsRejected(String) > [3] raw = "\"\"" FAILED
    java.lang.AssertionError at StartRunValidationTest.java:53

StartRunValidationTest > unknownHorizonIsRejected(String) > [4] raw = "5" FAILED
    java.lang.AssertionError at StartRunValidationTest.java:53

StartRunValidationTest > unknownHorizonIsRejected(String) > [5] raw = "null" FAILED
    java.lang.AssertionError at StartRunValidationTest.java:53

ScenarioControllerTest > catalogueServesLimitsAndCategoryArray() FAILED
    java.lang.AssertionError at ScenarioControllerTest.java:63

ScenarioControllerTest > catalogueServesDefaults() FAILED
    java.lang.AssertionError at ScenarioControllerTest.java:48

ScenarioControllerTest > scenarioResponsesNeverMentionOracle(String) > [1] path = "/api/scenario/catalogue" FAILED
    java.lang.AssertionError at ScenarioControllerTest.java:92

ScenarioControllerTest > scenarioResponsesNeverMentionOracle(String) > [2] path = "/api/scenario/configuration" FAILED
    java.lang.AssertionError at ScenarioControllerTest.java:92

ScenarioControllerTest > catalogueServesSevenHorizonsInOrder() FAILED
    java.lang.AssertionError at ScenarioControllerTest.java:35

ScenarioControllerTest > configurationEqualsDefaultsForFreshSession() FAILED
    java.lang.AssertionError at ScenarioControllerTest.java:76

2026-10-02T23:05:13.610+03:00  INFO 94511 --- [oracul-engine] [ionShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-02T23:05:13.612+03:00  INFO 94511 --- [oracul-engine] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2026-10-02T23:05:13.612+03:00  INFO 94511 --- [oracul-engine] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.

> Task :test FAILED
5 actionable tasks: 1 executed, 4 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

55 tests completed, 53 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 6s

```

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
[90m [2m❯[22m src/app/app.spec.ts:[2m272:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[14/17]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/app.spec.ts[2m > [22mApp shell (slice 01_scenario-controls)[2m > [22mclicking 5 years selects only 5 years
[31m[1mError[22m: Expected one matching request for criteria "Match by function: ", found none. Requests received are: GET /api/ping.[39m
[90m [2m❯[22m HttpClientTestingBackend.expectOne ../darwin_arm64-fastbuild-ST-fdfa778d11ba/bin/packages/common/http/testing/src/backend.ts:[2m112:13[22m[39m
[36m [2m❯[22m flushCatalogue src/app/app.spec.ts:[2m58:10[22m[39m
    [90m 56|[39m
    [90m 57|[39m   function flushCatalogue(): void {
    [90m 58|[39m     http.expectOne((r) => r.url.endsWith('/api/scenario/catalogue')).f…
    [90m   |[39m          [31m^[39m
    [90m 59|[39m   }
    [90m 60|[39m   function flushConfiguration(config: ScenarioConfiguration = DEFAULTS…
[90m [2m❯[22m startLoaded src/app/app.spec.ts:[2m66:5[22m[39m
[90m [2m❯[22m src/app/app.spec.ts:[2m279:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[15/17]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/app.spec.ts[2m > [22mApp shell (slice 01_scenario-controls)[2m > [22mclicking the already selected option keeps it selected
[31m[1mError[22m: Expected one matching request for criteria "Match by function: ", found none. Requests received are: GET /api/ping.[39m
[90m [2m❯[22m HttpClientTestingBackend.expectOne ../darwin_arm64-fastbuild-ST-fdfa778d11ba/bin/packages/common/http/testing/src/backend.ts:[2m112:13[22m[39m
[36m [2m❯[22m flushCatalogue src/app/app.spec.ts:[2m58:10[22m[39m
    [90m 56|[39m
    [90m 57|[39m   function flushCatalogue(): void {
    [90m 58|[39m     http.expectOne((r) => r.url.endsWith('/api/scenario/catalogue')).f…
    [90m   |[39m          [31m^[39m
    [90m 59|[39m   }
    [90m 60|[39m   function flushConfiguration(config: ScenarioConfiguration = DEFAULTS…
[90m [2m❯[22m startLoaded src/app/app.spec.ts:[2m66:5[22m[39m
[90m [2m❯[22m src/app/app.spec.ts:[2m288:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[16/17]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/app.spec.ts[2m > [22mApp shell (slice 01_scenario-controls)[2m > [22mhorizon selection does not change the sliders
[31m[1mError[22m: Expected one matching request for criteria "Match by function: ", found none. Requests received are: GET /api/ping.[39m
[90m [2m❯[22m HttpClientTestingBackend.expectOne ../darwin_arm64-fastbuild-ST-fdfa778d11ba/bin/packages/common/http/testing/src/backend.ts:[2m112:13[22m[39m
[36m [2m❯[22m flushCatalogue src/app/app.spec.ts:[2m58:10[22m[39m
    [90m 56|[39m
    [90m 57|[39m   function flushCatalogue(): void {
    [90m 58|[39m     http.expectOne((r) => r.url.endsWith('/api/scenario/catalogue')).f…
    [90m   |[39m          [31m^[39m
    [90m 59|[39m   }
    [90m 60|[39m   function flushConfiguration(config: ScenarioConfiguration = DEFAULTS…
[90m [2m❯[22m startLoaded src/app/app.spec.ts:[2m66:5[22m[39m
[90m [2m❯[22m src/app/app.spec.ts:[2m297:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[17/17]⎯[22m[39m


```

RESULT: RED
