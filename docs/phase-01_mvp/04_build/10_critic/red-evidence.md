# Red evidence — 10_critic

Date: 2026-10-03 · FRs: FR-22

## Tagged tests

- FR-22 → `backend/src/test/java/com/oracul/app/reasoning/CriticIT.java` (backend)
- FR-22 → `backend/src/test/java/com/oracul/app/reasoning/CriticParserTest.java` (backend)
- FR-22 → `backend/src/test/java/com/oracul/app/reasoning/CriticStagesIT.java` (backend)
- FR-22 → `backend/src/test/java/com/oracul/app/reasoning/ScenarioCriticPromptTest.java` (backend)
- FR-22 → `backend/src/test/java/com/oracul/app/reasoning/ScenarioGenerationPromptTest.java` (backend)
- FR-22 → `e2e/tests/critic.spec.ts` (e2e)
- FR-22 → `frontend/src/app/result/future-result.spec.ts` (frontend)
- FR-22 → `frontend/src/app/result/scenario-metadata.spec.ts` (frontend)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
2026-10-03T18:30:19.629+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-24 - Shutdown initiated...
2026-10-03T18:30:19.630+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-24 - Shutdown completed.
2026-10-03T18:30:19.641+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T18:30:19.641+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-25 - Shutdown initiated...
2026-10-03T18:30:19.641+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-25 - Shutdown completed.
2026-10-03T18:30:19.652+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T18:30:19.652+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-26 - Shutdown initiated...
2026-10-03T18:30:19.684+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-26 - Shutdown completed.
2026-10-03T18:31:00.788+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T18:31:00.788+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-59 - Shutdown initiated...
2026-10-03T18:31:00.788+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-59 - Shutdown completed.
2026-10-03T18:31:00.801+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T18:31:00.801+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-60 - Shutdown initiated...
2026-10-03T18:31:00.802+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-60 - Shutdown completed.
2026-10-03T18:31:00.814+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T18:31:00.814+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-61 - Shutdown initiated...
2026-10-03T18:31:00.814+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-61 - Shutdown completed.
2026-10-03T18:31:00.826+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T18:31:00.826+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-62 - Shutdown initiated...
2026-10-03T18:31:00.827+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-62 - Shutdown completed.
2026-10-03T18:31:00.838+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T18:31:00.838+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-63 - Shutdown initiated...
2026-10-03T18:31:00.839+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-63 - Shutdown completed.
2026-10-03T18:31:00.850+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T18:31:00.850+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-64 - Shutdown initiated...
2026-10-03T18:31:00.851+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-64 - Shutdown completed.
2026-10-03T18:31:00.862+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-03T18:31:00.862+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-65 - Shutdown initiated...
2026-10-03T18:31:00.862+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-65 - Shutdown completed.

> Task :test

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

991 tests completed, 50 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 2m 16s

```

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
[90m [2m❯[22m executeTemplate ../darwin_arm64-fastbuild-ST-fdfa778d11ba/bin/packages/core/src/render3/instructions/shared.ts:[2m107:5[22m[39m
[90m [2m❯[22m refreshView ../darwin_arm64-fastbuild-ST-fdfa778d11ba/bin/packages/core/src/render3/instructions/change_detection.ts:[2m242:7[22m[39m
[90m [2m❯[22m detectChangesInView ../darwin_arm64-fastbuild-ST-fdfa778d11ba/bin/packages/core/src/render3/instructions/change_detection.ts:[2m541:5[22m[39m
[90m [2m❯[22m detectChangesInViewIfAttached ../darwin_arm64-fastbuild-ST-fdfa778d11ba/bin/packages/core/src/render3/instructions/change_detection.ts:[2m489:3[22m[39m
[90m [2m❯[22m detectChangesInComponent ../darwin_arm64-fastbuild-ST-fdfa778d11ba/bin/packages/core/src/render3/instructions/change_detection.ts:[2m474:5[22m[39m
[90m [2m❯[22m detectChangesInChildComponents ../darwin_arm64-fastbuild-ST-fdfa778d11ba/bin/packages/core/src/render3/instructions/change_detection.ts:[2m570:5[22m[39m
[90m [2m❯[22m refreshView ../darwin_arm64-fastbuild-ST-fdfa778d11ba/bin/packages/core/src/render3/instructions/change_detection.ts:[2m307:7[22m[39m
[90m [2m❯[22m detectChangesInView ../darwin_arm64-fastbuild-ST-fdfa778d11ba/bin/packages/core/src/render3/instructions/change_detection.ts:[2m541:5[22m[39m
[90m [2m❯[22m detectChangesInViewIfAttached ../darwin_arm64-fastbuild-ST-fdfa778d11ba/bin/packages/core/src/render3/instructions/change_detection.ts:[2m489:3[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[2/4]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/scenario-metadata.spec.ts[2m > [22mslice 09_future-story: scenario metadata panel[2m > [22mslice 10_critic: open critic issues[2m > [22mshows the titled note with one entry per issue in array order after the evidence line
[31m[1mAssertionError[22m: expected null to be 'note' // Object.is equality[39m

- Expected:
"note"

+ Received:
null

[36m [2m❯[22m src/app/result/scenario-metadata.spec.ts:[2m197:42[22m[39m
    [90m195|[39m       const note = byId('critic-issues');
    [90m196|[39m       expect(note).not.toBeNull();
    [90m197|[39m       expect(note?.getAttribute('role')).toBe('note');
    [90m   |[39m                                          [31m^[39m
    [90m198|[39m       expect(note?.tagName.toLowerCase()).toBe('div');
    [90m199|[39m       expect(note?.querySelector('mat-icon')?.textContent?.trim()).toB…

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[3/4]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/scenario-metadata.spec.ts[2m > [22mslice 09_future-story: scenario metadata panel[2m > [22mslice 10_critic: open critic issues[2m > [22mrenders a description as literal text, never as markup
[31m[1mAssertionError[22m: expected '' to be 'Open questions from ORACUL\'s critic' // Object.is equality[39m

- Expected
+ Received

- Open questions from ORACUL's critic

[36m [2m❯[22m src/app/result/scenario-metadata.spec.ts:[2m214:43[22m[39m
    [90m212|[39m     it('renders a description as literal text, never as markup', async…
    [90m213|[39m       await show(withIssues([{ type: 'CONTRADICTION', description: '<i…
    [90m214|[39m       expect(text('critic-issues-title')).toBe("Open questions from OR…
    [90m   |[39m                                           [31m^[39m
    [90m215|[39m       expect(text('critic-issue-0')).toBe('<img src=x onerror=alert(1)…
    [90m216|[39m       expect(byId('critic-issues')?.querySelector('img')).toBeNull();

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[4/4]⎯[22m[39m


```

RESULT: RED
