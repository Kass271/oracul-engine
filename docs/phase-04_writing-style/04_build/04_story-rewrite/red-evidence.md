# Red evidence — 04_story-rewrite

Date: 2026-10-07 · FRs: FR-67
Scope: slice (related tests only — the slice gate runs the full suite)

## Tagged tests

- FR-67 → `backend/src/test/java/com/oracul/app/runs/RewriteRunIT.java` (backend)
- FR-67 → `backend/src/test/java/com/oracul/app/runs/RewriteRunLifecycleIT.java` (backend)
- FR-67 → `backend/src/test/java/com/oracul/app/runs/StartRewriteRunIT.java` (backend)
- FR-67 → `e2e/tests/story-rewrite.spec.ts` (e2e)
- FR-67 → `frontend/src/app/runs/quick-actions.spec.ts` (frontend)
- FR-67 → `frontend/src/app/runs/rewrite-menu.spec.ts` (frontend)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```

StartRewriteRunIT > anInvalidStyleIs400WithAndWithoutAConnection(String, String) > "writingStyle profileId 'kafka '" -> Unknown writing style FAILED
    java.lang.AssertionError at StartRewriteRunIT.java:124

StartRewriteRunIT > anInvalidStyleIs400WithAndWithoutAConnection(String, String) > "writingStyle profileId dickens (not in the shipped file)" -> Unknown writing style FAILED
    java.lang.AssertionError at StartRewriteRunIT.java:124

StartRewriteRunIT > anInvalidStyleIs400WithAndWithoutAConnection(String, String) > "writingStyle profileId 41 x a" -> Unknown writing style FAILED
    java.lang.AssertionError at StartRewriteRunIT.java:124

StartRewriteRunIT > anInvalidStyleIs400WithAndWithoutAConnection(String, String) > "writingStyle profileId 5000 x a" -> Unknown writing style FAILED
    java.lang.AssertionError at StartRewriteRunIT.java:124

StartRewriteRunIT > anInvalidStyleIs400WithAndWithoutAConnection(String, String) > "writingStyle profileId object" -> Unknown writing style FAILED
    java.lang.AssertionError at StartRewriteRunIT.java:124

StartRewriteRunIT > anInvalidStyleIs400WithAndWithoutAConnection(String, String) > "writingStyle profileId array" -> Unknown writing style FAILED
    java.lang.AssertionError at StartRewriteRunIT.java:124

StartRewriteRunIT > anInvalidStyleIs400WithAndWithoutAConnection(String, String) > "writingStyle OFFICIAL with kafka" -> Unknown writing style FAILED
    java.lang.AssertionError at StartRewriteRunIT.java:124

StartRewriteRunIT > anInvalidStyleIs400WithAndWithoutAConnection(String, String) > "writingStyle OFFICIAL with neutral-narrative" -> Unknown writing style FAILED
    java.lang.AssertionError at StartRewriteRunIT.java:124

StartRewriteRunIT > anInvalidStyleIs400WithAndWithoutAConnection(String, String) > "writingStyle NARRATIVE with official-briefing" -> Unknown writing style FAILED
    java.lang.AssertionError at StartRewriteRunIT.java:124

2026-10-08T02:37:36.788+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-08T02:37:36.790+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2026-10-08T02:37:36.790+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

166 tests completed, 166 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 18s

```

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[19/72]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/rewrite-menu.spec.ts[2m > [22mslice 04_story-rewrite: REWRITE AS… in the result view[2m > [22man answer that is not an ApiError shows the generic message
[31m[1mAssertionError[22m: quick-rewrite exists: expected null not to be null[39m
[36m [2m❯[22m btn src/app/runs/rewrite-menu.spec.ts:[2m185:35[22m[39m
    [90m183|[39m   const btn = (id: string): HTMLButtonElement => {
    [90m184|[39m     const b = byId(id);
    [90m185|[39m     expect(b, `${id} exists`).not.toBeNull();
    [90m   |[39m                                   [31m^[39m
    [90m186|[39m     return b as HTMLButtonElement;
    [90m187|[39m   };
[90m [2m❯[22m openMenu src/app/runs/rewrite-menu.spec.ts:[2m214:5[22m[39m
[90m [2m❯[22m src/app/runs/rewrite-menu.spec.ts:[2m531:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[20/72]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/rewrite-menu.spec.ts[2m > [22mslice 04_story-rewrite: quick-rewrite is disabled exactly when quick-alternative is[2m > [22mnot connected
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/rewrite-menu.spec.ts[2m > [22mslice 04_story-rewrite: quick-rewrite is disabled exactly when quick-alternative is[2m > [22mstarting
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/rewrite-menu.spec.ts[2m > [22mslice 04_story-rewrite: quick-rewrite is disabled exactly when quick-alternative is[2m > [22mQUEUED
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/rewrite-menu.spec.ts[2m > [22mslice 04_story-rewrite: quick-rewrite is disabled exactly when quick-alternative is[2m > [22mRUNNING
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/rewrite-menu.spec.ts[2m > [22mslice 04_story-rewrite: quick-rewrite is disabled exactly when quick-alternative is[2m > [22mCOMPLETED
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/rewrite-menu.spec.ts[2m > [22mslice 04_story-rewrite: quick-rewrite is disabled exactly when quick-alternative is[2m > [22mno run
[31m[1mAssertionError[22m: quick-rewrite exists: expected null not to be null[39m
[36m [2m❯[22m src/app/runs/rewrite-menu.spec.ts:[2m589:65[22m[39m
    [90m587|[39m   it.each(STATES)('%s', async (state, disabled) => {
    [90m588|[39m     await setUp(state);
    [90m589|[39m     expect(button('quick-rewrite'), 'quick-rewrite exists').not.toBeNu…
    [90m   |[39m                                                                 [31m^[39m
    [90m590|[39m     expect(button('quick-alternative').disabled).toBe(disabled);
    [90m591|[39m     expect(button('quick-rewrite').disabled).toBe(button('quick-altern…

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[21/72]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/rewrite-menu.spec.ts[2m > [22mslice 04_story-rewrite: quick-rewrite is disabled exactly when quick-alternative is[2m > [22mnot connected: a click on the disabled quick-rewrite opens no menu and sends nothing
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/rewrite-menu.spec.ts[2m > [22mslice 04_story-rewrite: quick-rewrite is disabled exactly when quick-alternative is[2m > [22mstarting: a click on the disabled quick-rewrite opens no menu and sends nothing
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/rewrite-menu.spec.ts[2m > [22mslice 04_story-rewrite: quick-rewrite is disabled exactly when quick-alternative is[2m > [22mQUEUED: a click on the disabled quick-rewrite opens no menu and sends nothing
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/rewrite-menu.spec.ts[2m > [22mslice 04_story-rewrite: quick-rewrite is disabled exactly when quick-alternative is[2m > [22mRUNNING: a click on the disabled quick-rewrite opens no menu and sends nothing
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/rewrite-menu.spec.ts[2m > [22mslice 04_story-rewrite: quick-rewrite is disabled exactly when quick-alternative is[2m > [22mno run: a click on the disabled quick-rewrite opens no menu and sends nothing
[31m[1mAssertionError[22m: quick-rewrite exists: expected null not to be null[39m
[36m [2m❯[22m src/app/runs/rewrite-menu.spec.ts:[2m599:67[22m[39m
    [90m597|[39m     async (state) => {
    [90m598|[39m       await setUp(state);
    [90m599|[39m       expect(button('quick-rewrite'), 'quick-rewrite exists').not.toBe…
    [90m   |[39m                                                                   [31m^[39m
    [90m600|[39m       button('quick-rewrite').click();
    [90m601|[39m       fixture.detectChanges();

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[22/72]⎯[22m[39m


```

RESULT: RED
