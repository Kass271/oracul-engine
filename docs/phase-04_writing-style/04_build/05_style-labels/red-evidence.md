# Red evidence — 05_style-labels

Date: 2026-10-08 · FRs: FR-68
Scope: slice (related tests only — the slice gate runs the full suite)

## Tagged tests

- FR-68 → `backend/src/test/java/com/oracul/app/runs/AlternativeOfRewriteIT.java` (backend)
- FR-68 → `backend/src/test/java/com/oracul/app/runs/StyleLabelsIT.java` (backend)
- FR-68 → `e2e/tests/style-labels.spec.ts` (e2e)
- FR-68 → `frontend/src/app/history/recent-futures.labels.spec.ts` (frontend)
- FR-68 → `frontend/src/app/result/scenario-metadata.labels.spec.ts` (frontend)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```

StyleLabelsIT > theListLabelsCompletedAndStoppedEntriesAlike(LabelCase, String) > cross legacy (no key) x absent / "STOPPED" FAILED
    org.opentest4j.AssertionFailedError at StyleLabelsIT.java:104

StyleLabelsIT > theListLabelsCompletedAndStoppedEntriesAlike(LabelCase, String) > cross missing tolstoy x en / "COMPLETED" FAILED
    org.opentest4j.AssertionFailedError at StyleLabelsIT.java:104

StyleLabelsIT > theListLabelsCompletedAndStoppedEntriesAlike(LabelCase, String) > cross missing tolstoy x en / "STOPPED" FAILED
    org.opentest4j.AssertionFailedError at StyleLabelsIT.java:104

StyleLabelsIT > theListLabelsCompletedAndStoppedEntriesAlike(LabelCase, String) > cross missing tolstoy x uk / "COMPLETED" FAILED
    org.opentest4j.AssertionFailedError at StyleLabelsIT.java:104

StyleLabelsIT > theListLabelsCompletedAndStoppedEntriesAlike(LabelCase, String) > cross missing tolstoy x uk / "STOPPED" FAILED
    org.opentest4j.AssertionFailedError at StyleLabelsIT.java:104

StyleLabelsIT > theListLabelsCompletedAndStoppedEntriesAlike(LabelCase, String) > cross missing tolstoy x absent / "COMPLETED" FAILED
    org.opentest4j.AssertionFailedError at StyleLabelsIT.java:104

StyleLabelsIT > theListLabelsCompletedAndStoppedEntriesAlike(LabelCase, String) > cross missing tolstoy x absent / "STOPPED" FAILED
    org.opentest4j.AssertionFailedError at StyleLabelsIT.java:104

StyleLabelsIT > aUkrainianNarrativeRunIsLabelledByItsStoredChoice() FAILED
    org.opentest4j.AssertionFailedError at StyleLabelsIT.java:134

StyleLabelsIT > anAlternativeAndAStandardRunHaveNoRewrittenFromButBothHaveLabels() FAILED
    org.opentest4j.AssertionFailedError at StyleLabelsIT.java:148

2026-10-08T04:14:15.931+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-08T04:14:15.932+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2026-10-08T04:14:15.933+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

76 tests completed, 71 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 14s

```

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```

- Rewritten from: <img src=x onerror=alert(1)>

[36m [2m❯[22m src/app/result/scenario-metadata.labels.spec.ts:[2m249:43[22m[39m
    [90m247|[39m         }),
    [90m248|[39m       );
    [90m249|[39m       expect(text('meta-rewritten-from')).toBe(`Rewritten from: ${head…
    [90m   |[39m                                           [31m^[39m
    [90m250|[39m       expect(byId('meta-rewritten-from')?.querySelector('b, img')).toB…
    [90m251|[39m       expect(text('meta-style')).toBe('Style: <i>s</i>');

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[44/57]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/scenario-metadata.labels.spec.ts[2m > [22mslice 05_style-labels: style, language and origin in the scenario metadata[2m > [22mrenders the origin headline Заголовок із майбутнього — Narrative as literal text
[31m[1mAssertionError[22m: expected '' to be 'Rewritten from: Заголовок із майбутнь…' // Object.is equality[39m

- Expected
+ Received

- Rewritten from: Заголовок із майбутнього — Narrative

[36m [2m❯[22m src/app/result/scenario-metadata.labels.spec.ts:[2m249:43[22m[39m
    [90m247|[39m         }),
    [90m248|[39m       );
    [90m249|[39m       expect(text('meta-rewritten-from')).toBe(`Rewritten from: ${head…
    [90m   |[39m                                           [31m^[39m
    [90m250|[39m       expect(byId('meta-rewritten-from')?.querySelector('b, img')).toB…
    [90m251|[39m       expect(text('meta-style')).toBe('Style: <i>s</i>');

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[45/57]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/scenario-metadata.labels.spec.ts[2m > [22mslice 05_style-labels: style, language and origin in the scenario metadata[2m > [22mtakes the labels from the result, never from the panel, the style catalogue or the run configuration
[31m[1mAssertionError[22m: expected '' to be 'Style: Official / Briefing' // Object.is equality[39m

- Expected
+ Received

- Style: Official / Briefing

[36m [2m❯[22m src/app/result/scenario-metadata.labels.spec.ts:[2m271:32[22m[39m
    [90m269|[39m     await show(result({ styleLabel: 'Official / Briefing', languageLab…
    [90m270|[39m     // the run and the result configuration say Kafka / uk; the panel …
    [90m271|[39m     expect(text('meta-style')).toBe('Style: Official / Briefing');
    [90m   |[39m                                [31m^[39m
    [90m272|[39m     expect(text('meta-language')).toBe('Language: English');
    [90m273|[39m     store.setLanguage('en');

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[46/57]⎯[22m[39m


```

RESULT: RED
