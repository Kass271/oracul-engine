# Red evidence — 01_style-catalogue

Date: 2026-10-07 · FRs: FR-62
Scope: slice (related tests only — the slice gate runs the full suite)

## Tagged tests

- FR-62 → `backend/src/test/java/com/oracul/app/style/WritingStylesApiIT.java` (backend)
- FR-62 → `backend/src/test/java/com/oracul/app/style/WritingStylesCatalogueTest.java` (backend)
- FR-62 → `backend/src/test/java/com/oracul/app/style/WritingStylesStartupTest.java` (backend)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
    java.lang.AssertionError at WritingStylesStartupTest.java:75

WritingStylesStartupTest > idInsideThePatternStarts(String) > valid id ["0"] FAILED
    java.lang.AssertionError at WritingStylesStartupTest.java:75

WritingStylesStartupTest > idInsideThePatternStarts(String) > valid id ["-"] FAILED
    java.lang.AssertionError at WritingStylesStartupTest.java:75

WritingStylesStartupTest > idInsideThePatternStarts(String) > valid id ["a-b"] FAILED
    java.lang.AssertionError at WritingStylesStartupTest.java:75

WritingStylesStartupTest > idInsideThePatternStarts(String) > valid id ["k-1"] FAILED
    java.lang.AssertionError at WritingStylesStartupTest.java:75

WritingStylesStartupTest > idInsideThePatternStarts(String) > valid id ["poe"] FAILED
    java.lang.AssertionError at WritingStylesStartupTest.java:75

WritingStylesStartupTest > idInsideThePatternStarts(String) > valid id ["dickens"] FAILED
    java.lang.AssertionError at WritingStylesStartupTest.java:75

WritingStylesStartupTest > idInsideThePatternStarts(String) > valid id ["aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"] FAILED
    java.lang.AssertionError at WritingStylesStartupTest.java:75

WritingStylesStartupTest > idInsideThePatternStarts(String) > valid id ["9999999999999999999999999999999999999999"] FAILED
    java.lang.AssertionError at WritingStylesStartupTest.java:75

WritingStylesStartupTest > idInsideThePatternStarts(String) > valid id ["----------------------------------------"] FAILED
    java.lang.AssertionError at WritingStylesStartupTest.java:75

2026-10-07T22:42:36.582+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T22:42:36.583+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2026-10-07T22:42:36.584+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

295 tests completed, 295 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 7s

```

RESULT: RED
