# Red evidence — 03_run-modes-readme

Date: 2026-10-04 · FRs: FR-42, FR-43, FR-45, FR-46, FR-47, FR-48
Scope: slice (related tests only — the slice gate runs the full suite)

## Tagged tests

- FR-42 → `e2e/tests/run-modes.spec.ts` (e2e)
- FR-43 → `e2e/tests/readme.spec.ts` (e2e)
- FR-45 → `backend/src/test/java/com/oracul/app/history/RecentRunsIT.java` (backend)
- FR-45 → `backend/src/test/java/com/oracul/app/runs/AlternativeRunIT.java` (backend)
- FR-45 → `backend/src/test/java/com/oracul/app/runs/RunGuardTest.java` (backend)
- FR-45 → `backend/src/test/java/com/oracul/app/runs/StopQueuedRunIT.java` (backend)
- FR-45 → `backend/src/test/java/com/oracul/app/runs/StopRunIT.java` (backend)
- FR-45 → `backend/src/test/java/com/oracul/app/runs/StoppedRunDeadlineIT.java` (backend)
- FR-45 → `e2e/tests/run-control.spec.ts` (e2e)
- FR-45 → `e2e/tests/run-start.spec.ts` (e2e)
- FR-45 → `frontend/src/app/history/recent-futures.spec.ts` (frontend)
- FR-45 → `frontend/src/app/runs/generate-button.spec.ts` (frontend)
- FR-45 → `frontend/src/app/runs/run.store.spec.ts` (frontend)
- FR-45 → `frontend/src/app/runs/stop-run.spec.ts` (frontend)
- FR-46 → `backend/src/test/java/com/oracul/app/research/EventBatchingIT.java` (backend)
- FR-46 → `backend/src/test/java/com/oracul/app/research/EventConcurrencyIT.java` (backend)
- FR-46 → `backend/src/test/java/com/oracul/app/research/EventConcurrencyOneIT.java` (backend)
- FR-46 → `backend/src/test/java/com/oracul/app/research/EventFailureIT.java` (backend)
- FR-46 → `backend/src/test/java/com/oracul/app/research/EventSourceCapIT.java` (backend)
- FR-46 → `backend/src/test/java/com/oracul/app/research/GoogleNewsRunIT.java` (backend)
- FR-46 → `backend/src/test/java/com/oracul/app/research/SourceCapIT.java` (backend)
- FR-46 → `backend/src/test/java/com/oracul/app/research/SourceFixtureIT.java` (backend)
- FR-46 → `e2e/tests/events.spec.ts` (e2e)
- FR-46 → `e2e/tests/evidence-pack.spec.ts` (e2e)
- FR-46 → `e2e/tests/search-sources.spec.ts` (e2e)
- FR-47 → `backend/src/test/java/com/oracul/app/reasoning/CriticIT.java` (backend)
- FR-47 → `backend/src/test/java/com/oracul/app/reasoning/SpeculativeScenarioIT.java` (backend)
- FR-47 → `backend/src/test/java/com/oracul/app/reasoning/StructuredScenarioIT.java` (backend)
- FR-47 → `backend/src/test/java/com/oracul/app/research/ErrorClassificationIT.java` (backend)
- FR-47 → `backend/src/test/java/com/oracul/app/research/EventNormalizationIT.java` (backend)
- FR-47 → `backend/src/test/java/com/oracul/app/research/EvidencePackIT.java` (backend)
- FR-47 → `backend/src/test/java/com/oracul/app/research/NewsSearchNoBudgetIT.java` (backend)
- FR-47 → `backend/src/test/java/com/oracul/app/research/NewsSearchRunIT.java` (backend)
- FR-47 → `backend/src/test/java/com/oracul/app/research/ResearchPlanIT.java` (backend)
- FR-47 → `backend/src/test/java/com/oracul/app/research/ResearchPlanTimeoutIT.java` (backend)
- FR-47 → `backend/src/test/java/com/oracul/app/research/SourceQueryTimeoutIT.java` (backend)
- FR-47 → `backend/src/test/java/com/oracul/app/research/SourceRetrievalIT.java` (backend)
- FR-47 → `backend/src/test/java/com/oracul/app/result/EvidenceNoteNoThresholdIT.java` (backend)
- FR-47 → `backend/src/test/java/com/oracul/app/result/FutureResultIT.java` (backend)
- FR-47 → `backend/src/test/java/com/oracul/app/result/InsufficientEvidenceIT.java` (backend)
- FR-47 → `backend/src/test/java/com/oracul/app/runs/GetRunTerminalIT.java` (backend)
- FR-47 → `backend/src/test/java/com/oracul/app/runs/RunDeadlineIT.java` (backend)
- FR-47 → `e2e/tests/events.spec.ts` (e2e)
- FR-47 → `e2e/tests/insufficient-evidence.spec.ts` (e2e)
- FR-47 → `e2e/tests/search-sources.spec.ts` (e2e)
- FR-47 → `frontend/src/app/runs/evidence-note.spec.ts` (frontend)
- FR-48 → `backend/src/test/java/com/oracul/app/research/GoogleNewsBudgetIT.java` (backend)
- FR-48 → `backend/src/test/java/com/oracul/app/research/GoogleNewsRunIT.java` (backend)
- FR-48 → `backend/src/test/java/com/oracul/app/research/GoogleNewsSearchIT.java` (backend)
- FR-48 → `backend/src/test/java/com/oracul/app/research/GoogleNewsSourceIT.java` (backend)
- FR-48 → `backend/src/test/java/com/oracul/app/research/GoogleNewsTimingIT.java` (backend)
- FR-48 → `e2e/tests/search-sources.spec.ts` (e2e)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
    org.opentest4j.AssertionFailedError at StopRunIT.java:56

StopRunIT > aStopRacingTheEndOfTheRunEndsEitherCompletedWithAStoryOrStoppedWithoutOne() FAILED
    org.opentest4j.AssertionFailedError at StopRunIT.java:56

2026-10-04T19:37:29.136+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T19:37:29.137+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-8 - Shutdown initiated...
2026-10-04T19:37:29.138+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-8 - Shutdown completed.
2026-10-04T19:37:29.175+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T19:37:29.176+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-9 - Shutdown initiated...
2026-10-04T19:37:29.177+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-9 - Shutdown completed.
2026-10-04T19:37:29.197+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T19:37:29.198+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-10 - Shutdown initiated...
2026-10-04T19:37:29.199+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-10 - Shutdown completed.
2026-10-04T19:37:29.221+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T19:37:29.221+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-11 - Shutdown initiated...
2026-10-04T19:37:29.222+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-11 - Shutdown completed.
2026-10-04T19:37:29.242+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T19:37:29.243+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-12 - Shutdown initiated...
2026-10-04T19:37:29.244+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-12 - Shutdown completed.
2026-10-04T19:37:29.262+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T19:37:29.262+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-13 - Shutdown initiated...
2026-10-04T19:37:29.263+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-13 - Shutdown completed.
2026-10-04T19:37:29.279+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T19:37:29.279+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-14 - Shutdown initiated...
2026-10-04T19:37:29.279+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-14 - Shutdown completed.
2026-10-04T19:37:29.292+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-04T19:37:29.292+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-15 - Shutdown initiated...
2026-10-04T19:37:29.292+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-15 - Shutdown completed.

> Task :test

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

567 tests completed, 220 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 2m 46s

```

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
[36m [2m❯[22m click src/app/runs/stop-run.spec.ts:[2m169:43[22m[39m
    [90m167|[39m
    [90m168|[39m   function click(id: string): void {
    [90m169|[39m     expect(byId(id), `element ${id}`).not.toBeNull();
    [90m   |[39m                                           [31m^[39m
    [90m170|[39m     (byId(id) as HTMLButtonElement).click();
    [90m171|[39m     fixture.detectChanges();
[90m [2m❯[22m src/app/runs/stop-run.spec.ts:[2m441:5[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[25/41]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/stop-run.spec.ts[2m > [22mrun-control FR-45: stop a generation and start a new one[2m > [22ma stop answered with a COMPLETED run shows the result, without a message
[31m[1mAssertionError[22m: element generate-button: expected null not to be null[39m
[36m [2m❯[22m click src/app/runs/stop-run.spec.ts:[2m169:43[22m[39m
    [90m167|[39m
    [90m168|[39m   function click(id: string): void {
    [90m169|[39m     expect(byId(id), `element ${id}`).not.toBeNull();
    [90m   |[39m                                           [31m^[39m
    [90m170|[39m     (byId(id) as HTMLButtonElement).click();
    [90m171|[39m     fixture.detectChanges();
[90m [2m❯[22m src/app/runs/stop-run.spec.ts:[2m461:5[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[26/41]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/stop-run.spec.ts[2m > [22mrun-control FR-45: stop a generation and start a new one[2m > [22ma stop answered with a FAILED run shows the failure view, without a message
[31m[1mAssertionError[22m: element generate-button: expected null not to be null[39m
[36m [2m❯[22m click src/app/runs/stop-run.spec.ts:[2m169:43[22m[39m
    [90m167|[39m
    [90m168|[39m   function click(id: string): void {
    [90m169|[39m     expect(byId(id), `element ${id}`).not.toBeNull();
    [90m   |[39m                                           [31m^[39m
    [90m170|[39m     (byId(id) as HTMLButtonElement).click();
    [90m171|[39m     fixture.detectChanges();
[90m [2m❯[22m src/app/runs/stop-run.spec.ts:[2m483:5[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[27/41]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/stop-run.spec.ts[2m > [22mrun-control FR-45: stop a generation and start a new one[2m > [22m/futures/<id> of a STOPPED run shows the stopped view and loads its configuration into the panel
[31m[1mAssertionError[22m: expected null not to be null[39m
[36m [2m❯[22m src/app/runs/stop-run.spec.ts:[2m505:38[22m[39m
    [90m503|[39m     await tick(0);
    [90m504|[39m     await tick(0);
    [90m505|[39m     expect(byId('stopped-view')).not.toBeNull();
    [90m   |[39m                                      [31m^[39m
    [90m506|[39m     expect(text('stopped-message')).toBe('Generation stopped');
    [90m507|[39m     expect(TestBed.inject(ScenarioStore).darkness()).toBe(9);

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[28/41]⎯[22m[39m


```

RESULT: RED
