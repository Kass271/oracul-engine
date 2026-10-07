# Red evidence — 02_style-selection

Date: 2026-10-07 · FRs: FR-63, FR-64
Scope: slice (related tests only — the slice gate runs the full suite)

## Tagged tests

- FR-63 → `backend/src/test/java/com/oracul/app/runs/StartRunWritingStyleDickensIT.java` (backend)
- FR-63 → `backend/src/test/java/com/oracul/app/runs/StartRunWritingStyleDickensTest.java` (backend)
- FR-63 → `backend/src/test/java/com/oracul/app/runs/StartRunWritingStyleIT.java` (backend)
- FR-63 → `backend/src/test/java/com/oracul/app/runs/StartRunWritingStyleValidationTest.java` (backend)
- FR-63 → `e2e/tests/writing-style.spec.ts` (e2e)
- FR-63 → `frontend/src/app/runs/quick-actions.spec.ts` (frontend)
- FR-63 → `frontend/src/app/runs/run-failure.spec.ts` (frontend)
- FR-63 → `frontend/src/app/runs/run.store.spec.ts` (frontend)
- FR-63 → `frontend/src/app/scenario/scenario.loader.spec.ts` (frontend)
- FR-63 → `frontend/src/app/scenario/scenario.store.writing-style.spec.ts` (frontend)
- FR-63 → `frontend/src/app/scenario/writing-style-selector.spec.ts` (frontend)
- FR-64 → `backend/src/test/java/com/oracul/app/runs/StartRunWritingStyleIT.java` (backend)
- FR-64 → `backend/src/test/java/com/oracul/app/runs/StartRunWritingStyleValidationTest.java` (backend)
- FR-64 → `e2e/tests/writing-style.spec.ts` (e2e)
- FR-64 → `frontend/src/app/runs/quick-actions.spec.ts` (frontend)
- FR-64 → `frontend/src/app/runs/run-failure.spec.ts` (frontend)
- FR-64 → `frontend/src/app/runs/run.store.spec.ts` (frontend)
- FR-64 → `frontend/src/app/scenario/scenario.loader.spec.ts` (frontend)
- FR-64 → `frontend/src/app/scenario/scenario.store.writing-style.spec.ts` (frontend)
- FR-64 → `frontend/src/app/scenario/writing-style-selector.spec.ts` (frontend)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```

StartRunWritingStyleIT > aRejectedLanguageStoresNothing(String, String) > rejected language "number": 400 and no run stored FAILED
    java.lang.AssertionError at StartRunWritingStyleIT.java:66

StartRunWritingStyleIT > aRejectedLanguageStoresNothing(String, String) > rejected language "array": 400 and no run stored FAILED
    java.lang.AssertionError at StartRunWritingStyleIT.java:66

StartRunWritingStyleIT > aRejectedLanguageStoresNothing(String, String) > rejected language "object": 400 and no run stored FAILED
    java.lang.AssertionError at StartRunWritingStyleIT.java:66

StartRunWritingStyleIT > theShippedFileDoesNotKnowDickens() FAILED
    java.lang.AssertionError at StartRunWritingStyleIT.java:66

StartRunWritingStyleIT > aBadStyleWithoutAnyConnectionIs400Not401() FAILED
    java.lang.AssertionError at StartRunWritingStyleIT.java:66

StartRunWritingStyleIT > aBadStyleIsReportedBeforeTheActiveRunRule() FAILED
    java.lang.AssertionError at StartRunWritingStyleIT.java:66

StartRunWritingStyleIT > aValidLanguageIsStoredAsSent(String) > language "en" is stored as sent FAILED
    org.opentest4j.AssertionFailedError at StartRunWritingStyleIT.java:138

StartRunWritingStyleIT > aValidLanguageIsStoredAsSent(String) > language "uk" is stored as sent FAILED
    org.opentest4j.AssertionFailedError at StartRunWritingStyleIT.java:138

2026-10-08T00:02:01.554+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-08T00:02:01.556+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2026-10-08T00:02:01.556+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.
2026-10-08T00:02:01.576+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-08T00:02:01.577+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-2 - Shutdown initiated...
2026-10-08T00:02:01.577+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-2 - Shutdown completed.

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

191 tests completed, 149 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 9s

```

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/scenario/writing-style-selector.spec.ts[2m > [22mWriting Style selector and Story language select (slice 02_style-selection)[2m > [22mmobile (phone width)[2m > [22mthe dropdown selects neutral-narrative
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/scenario/writing-style-selector.spec.ts[2m > [22mWriting Style selector and Story language select (slice 02_style-selection)[2m > [22mmobile (phone width)[2m > [22mthe dropdown selects poe
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/scenario/writing-style-selector.spec.ts[2m > [22mWriting Style selector and Story language select (slice 02_style-selection)[2m > [22mmobile (phone width)[2m > [22mthe dropdown selects kafka
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/scenario/writing-style-selector.spec.ts[2m > [22mWriting Style selector and Story language select (slice 02_style-selection)[2m > [22mmobile (phone width)[2m > [22mthe dropdown selects wells
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/scenario/writing-style-selector.spec.ts[2m > [22mWriting Style selector and Story language select (slice 02_style-selection)[2m > [22mmobile (phone width)[2m > [22mthe dropdown selects verne
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/scenario/writing-style-selector.spec.ts[2m > [22mWriting Style selector and Story language select (slice 02_style-selection)[2m > [22mmobile (phone width)[2m > [22mthe dropdown selects orwell
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/scenario/writing-style-selector.spec.ts[2m > [22mWriting Style selector and Story language select (slice 02_style-selection)[2m > [22mmobile (phone width)[2m > [22mthe dropdown selects clarke
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/scenario/writing-style-selector.spec.ts[2m > [22mWriting Style selector and Story language select (slice 02_style-selection)[2m > [22mmobile (phone width)[2m > [22mthe dropdown selects harrison
[31m[1mError[22m: Expected one matching request for criteria "Match by function: ", found none. Requests received are: GET /api/auth/chatgpt/connection.[39m
[90m [2m❯[22m HttpClientTestingBackend.expectOne ../darwin_arm64-fastbuild-ST-fdfa778d11ba/bin/packages/common/http/testing/src/backend.ts:[2m112:13[22m[39m
[36m [2m❯[22m startLoaded src/app/scenario/writing-style-selector.spec.ts:[2m103:10[22m[39m
    [90m101|[39m     http.expectOne((r) => r.url.endsWith('/api/scenario/catalogue')).f…
    [90m102|[39m     http.expectOne((r) => r.url.endsWith('/api/scenario/configuration'…
    [90m103|[39m     http.expectOne((r) => r.url.endsWith('/api/writing-styles')).flush…
    [90m   |[39m          [31m^[39m
    [90m104|[39m     await settle();
    [90m105|[39m     if (subject.value.matches) {
[90m [2m❯[22m src/app/scenario/writing-style-selector.spec.ts:[2m536:13[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[123/291]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/scenario/writing-style-selector.spec.ts[2m > [22mWriting Style selector and Story language select (slice 02_style-selection)[2m > [22mmobile (phone width)[2m > [22ma breakpoint change keeps the selected profile and swaps the radios for the select and back
[31m[1mError[22m: Expected one matching request for criteria "Match by function: ", found none. Requests received are: GET /api/auth/chatgpt/connection.[39m
[90m [2m❯[22m HttpClientTestingBackend.expectOne ../darwin_arm64-fastbuild-ST-fdfa778d11ba/bin/packages/common/http/testing/src/backend.ts:[2m112:13[22m[39m
[36m [2m❯[22m startLoaded src/app/scenario/writing-style-selector.spec.ts:[2m103:10[22m[39m
    [90m101|[39m     http.expectOne((r) => r.url.endsWith('/api/scenario/catalogue')).f…
    [90m102|[39m     http.expectOne((r) => r.url.endsWith('/api/scenario/configuration'…
    [90m103|[39m     http.expectOne((r) => r.url.endsWith('/api/writing-styles')).flush…
    [90m   |[39m          [31m^[39m
    [90m104|[39m     await settle();
    [90m105|[39m     if (subject.value.matches) {
[90m [2m❯[22m src/app/scenario/writing-style-selector.spec.ts:[2m548:13[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[124/291]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/scenario/writing-style-selector.spec.ts[2m > [22mWriting Style selector and Story language select (slice 02_style-selection)[2m > [22mmobile (phone width)[2m > [22mthe language select works on a phone and reads Ukrainian after choosing it
[31m[1mError[22m: Expected one matching request for criteria "Match by function: ", found none. Requests received are: GET /api/auth/chatgpt/connection.[39m
[90m [2m❯[22m HttpClientTestingBackend.expectOne ../darwin_arm64-fastbuild-ST-fdfa778d11ba/bin/packages/common/http/testing/src/backend.ts:[2m112:13[22m[39m
[36m [2m❯[22m startLoaded src/app/scenario/writing-style-selector.spec.ts:[2m103:10[22m[39m
    [90m101|[39m     http.expectOne((r) => r.url.endsWith('/api/scenario/catalogue')).f…
    [90m102|[39m     http.expectOne((r) => r.url.endsWith('/api/scenario/configuration'…
    [90m103|[39m     http.expectOne((r) => r.url.endsWith('/api/writing-styles')).flush…
    [90m   |[39m          [31m^[39m
    [90m104|[39m     await settle();
    [90m105|[39m     if (subject.value.matches) {
[90m [2m❯[22m src/app/scenario/writing-style-selector.spec.ts:[2m563:13[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[125/291]⎯[22m[39m


```

RESULT: RED
