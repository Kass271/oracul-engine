# Red evidence — 07_starting-conditions

Date: 2026-10-07 · FRs: FR-58
Scope: slice (related tests only — the slice gate runs the full suite)

## Tagged tests

- FR-58 → `backend/src/test/java/com/oracul/app/reasoning/CriticIT.java` (backend)
- FR-58 → `backend/src/test/java/com/oracul/app/reasoning/CriticParserTest.java` (backend)
- FR-58 → `backend/src/test/java/com/oracul/app/reasoning/ScenarioCriticPromptTest.java` (backend)
- FR-58 → `backend/src/test/java/com/oracul/app/reasoning/ScenarioGenerationIT.java` (backend)
- FR-58 → `backend/src/test/java/com/oracul/app/reasoning/ScenarioGenerationPromptTest.java` (backend)
- FR-58 → `backend/src/test/java/com/oracul/app/reasoning/SpeculativeScenarioIT.java` (backend)
- FR-58 → `backend/src/test/java/com/oracul/app/reasoning/StartingConditionsTest.java` (backend)
- FR-58 → `e2e/tests/critic.spec.ts` (e2e)
- FR-58 → `e2e/tests/future-story.spec.ts` (e2e)
- FR-58 → `e2e/tests/starting-conditions.spec.ts` (e2e)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```

StartingConditionsTest > theConstantCarriesEveryPrinciplePhrase(String) > contains: "starting conditions" FAILED
    java.lang.AssertionError at StartingConditionsTest.java:15

StartingConditionsTest > theConstantCarriesEveryPrinciplePhrase(String) > contains: "direction, intensity and magnitude" FAILED
    java.lang.AssertionError at StartingConditionsTest.java:15

StartingConditionsTest > theConstantCarriesEveryPrinciplePhrase(String) > contains: "Do not normalise toward the realistic, conservative or statistically most likely outcome" FAILED
    java.lang.AssertionError at StartingConditionsTest.java:15

StartingConditionsTest > theConstantCarriesEveryPrinciplePhrase(String) > contains: "Do not summarise, retell or rewrite the news" FAILED
    java.lang.AssertionError at StartingConditionsTest.java:15

StartingConditionsTest > theConstantCarriesEveryPrinciplePhrase(String) > contains: "every FACT must reference one or more ORACUL Evidence IDs" FAILED
    java.lang.AssertionError at StartingConditionsTest.java:15

StartingConditionsTest > theConstantCarriesEveryPrinciplePhrase(String) > contains: "no current sources found" FAILED
    java.lang.AssertionError at StartingConditionsTest.java:15

StartingConditionsTest > line8QuotesTheEmptySectionTextWithAsciiDoubleQuotes() FAILED
    java.lang.AssertionError at StartingConditionsTest.java:15

StartingConditionsTest > theConstantHasNoBraceAndNoAngleBracket() FAILED
    java.lang.AssertionError at StartingConditionsTest.java:15

2026-10-07T11:42:41.906+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T11:42:41.907+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2026-10-07T11:42:41.908+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.
2026-10-07T11:42:41.939+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T11:42:41.940+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-2 - Shutdown initiated...
2026-10-07T11:42:41.940+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-2 - Shutdown completed.

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

215 tests completed, 100 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 35s

```

RESULT: RED
