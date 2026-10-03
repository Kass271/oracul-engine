# Red evidence — 12_insufficient-evidence

Date: 2026-10-03 · FRs: FR-31

## Tagged tests

- FR-31 → `backend/src/test/java/com/oracul/app/research/EvidenceConfigurationTest.java` (backend)
- FR-31 → `backend/src/test/java/com/oracul/app/research/EvidenceSufficiencyTest.java` (backend)
- FR-31 → `backend/src/test/java/com/oracul/app/result/InsufficientEvidenceIT.java` (backend)
- FR-31 → `backend/src/test/java/com/oracul/app/runs/RunFailuresTest.java` (backend)
- FR-31 → `e2e/tests/insufficient-evidence.spec.ts` (e2e)
- FR-31 → `frontend/src/app/runs/insufficient-evidence.spec.ts` (frontend)
- FR-31 → `frontend/src/app/runs/run-view.spec.ts` (frontend)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
2026-10-03T20:36:35.462+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-38 - Shutdown initiated...
2026-10-03T20:36:35.463+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-38 - Shutdown completed.
2026-10-03T20:36:35.474+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T20:36:35.474+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-39 - Shutdown initiated...
2026-10-03T20:36:35.475+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-39 - Shutdown completed.
2026-10-03T20:36:35.487+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T20:36:35.487+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-40 - Shutdown initiated...
2026-10-03T20:36:35.499+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-40 - Shutdown completed.
2026-10-03T20:38:31.348+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T20:38:31.348+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-60 - Shutdown initiated...
2026-10-03T20:38:31.348+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-60 - Shutdown completed.
2026-10-03T20:38:31.362+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T20:38:31.362+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-61 - Shutdown initiated...
2026-10-03T20:38:31.362+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-61 - Shutdown completed.
2026-10-03T20:38:31.375+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T20:38:31.375+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-62 - Shutdown initiated...
2026-10-03T20:38:31.375+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-62 - Shutdown completed.
2026-10-03T20:38:31.387+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T20:38:31.388+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-63 - Shutdown initiated...
2026-10-03T20:38:31.388+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-63 - Shutdown completed.
2026-10-03T20:38:31.399+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T20:38:31.399+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-64 - Shutdown initiated...
2026-10-03T20:38:31.399+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-64 - Shutdown completed.
2026-10-03T20:38:31.410+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T20:38:31.411+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-65 - Shutdown initiated...
2026-10-03T20:38:31.411+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-65 - Shutdown completed.
2026-10-03T20:38:31.422+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T20:38:31.422+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-66 - Shutdown initiated...
2026-10-03T20:38:31.422+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-66 - Shutdown completed.

> Task :test

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

1088 tests completed, 62 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 3m 5s

```

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
    [90m194|[39m     await open(insufficient(10, { suggestedRealism: 8 }));
    [90m195|[39m     const content = byId('insufficient-view')?.textContent ?? '';
    [90m196|[39m     expect(content).not.toBe('');
    [90m   |[39m                         [31m^[39m
    [90m197|[39m     for (const forbidden of ['INSUFFICIENT_EVIDENCE', RUN_ID, 'ORC-', …
    [90m198|[39m       expect(content).not.toContain(forbidden);

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[9/11]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/run-view.spec.ts[2m > [22mslice 12_insufficient-evidence[2m > [22man INSUFFICIENT_EVIDENCE run shows only the insufficient view and makes no result call
[31m[1mAssertionError[22m: expected [ 'progress-view' ] to deeply equal [ 'insufficient-view' ][39m

- Expected
+ Received

  [
-   "insufficient-view",
+   "progress-view",
  ]

[36m [2m❯[22m src/app/runs/run-view.spec.ts:[2m461:21[22m[39m
    [90m459|[39m       has(`[data-testid="${id}"]`),
    [90m460|[39m     );
    [90m461|[39m     expect(present).toEqual(['insufficient-view']);
    [90m   |[39m                     [31m^[39m
    [90m462|[39m     expect(has('app-future-result')).toBe(false);
    [90m463|[39m     expect((el().querySelector('[data-testid="insufficient-message"]')…

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[10/11]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/run-view.spec.ts[2m > [22mslice 12_insufficient-evidence[2m > [22mpolling RUNNING then INSUFFICIENT_EVIDENCE switches to the insufficient view within one tick and stops polling
[31m[1mAssertionError[22m: expected false to be true // Object.is equality[39m

- Expected
+ Received

- true
+ false

[36m [2m❯[22m src/app/runs/run-view.spec.ts:[2m478:54[22m[39m
    [90m476|[39m     await vi.advanceTimersByTimeAsync(0);
    [90m477|[39m     harness.detectChanges();
    [90m478|[39m     expect(has('[data-testid="insufficient-view"]')).toBe(true);
    [90m   |[39m                                                      [31m^[39m
    [90m479|[39m     expect(has('[data-testid="progress-view"]')).toBe(false);
    [90m480|[39m     await vi.advanceTimersByTimeAsync(5000);

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[11/11]⎯[22m[39m


```

RESULT: RED
