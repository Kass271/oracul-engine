# Red evidence — 06_wildcard-pack

Date: 2026-10-07 · FRs: FR-57
Scope: slice (related tests only — the slice gate runs the full suite)

## Tagged tests

- FR-57 → `backend/src/test/java/com/oracul/app/reasoning/EvidenceGuardTest.java` (backend)
- FR-57 → `backend/src/test/java/com/oracul/app/reasoning/ScenarioGenerationIT.java` (backend)
- FR-57 → `backend/src/test/java/com/oracul/app/reasoning/ScenarioGenerationPromptTest.java` (backend)
- FR-57 → `backend/src/test/java/com/oracul/app/reasoning/SpeculativeScenarioIT.java` (backend)
- FR-57 → `backend/src/test/java/com/oracul/app/research/EventNormalizationIT.java` (backend)
- FR-57 → `backend/src/test/java/com/oracul/app/research/EvidencePackAtomicIT.java` (backend)
- FR-57 → `backend/src/test/java/com/oracul/app/research/EvidencePackIT.java` (backend)
- FR-57 → `backend/src/test/java/com/oracul/app/research/RankingMinQualityIT.java` (backend)
- FR-57 → `backend/src/test/java/com/oracul/app/research/RankingSelectionIT.java` (backend)
- FR-57 → `backend/src/test/java/com/oracul/app/research/WildcardPackRendererTest.java` (backend)
- FR-57 → `backend/src/test/java/com/oracul/app/result/EvidenceNoteNoThresholdIT.java` (backend)
- FR-57 → `backend/src/test/java/com/oracul/app/result/InsufficientEvidenceIT.java` (backend)
- FR-57 → `backend/src/test/java/com/oracul/app/result/OptionalWireFieldsAbsentIT.java` (backend)
- FR-57 → `backend/src/test/java/com/oracul/app/result/WildcardPackRunIT.java` (backend)
- FR-57 → `backend/src/test/java/com/oracul/app/runs/EvidenceNotesTest.java` (backend)
- FR-57 → `e2e/tests/evidence-pack.spec.ts` (e2e)
- FR-57 → `e2e/tests/insufficient-evidence.spec.ts` (e2e)
- FR-57 → `e2e/tests/validated-scenario.spec.ts` (e2e)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```

WildcardPackRunIT > theInvariantsOfTheGroupedPackHoldForEveryDataset(String) > "V4 under body A" FAILED
    java.lang.AssertionError at WildcardPackRunIT.java:91

WildcardPackRunIT > theInvariantsOfTheGroupedPackHoldForEveryDataset(String) > "V4 under body B" FAILED
    java.lang.AssertionError at WildcardPackRunIT.java:91

WildcardPackRunIT > theInvariantsOfTheGroupedPackHoldForEveryDataset(String) > "seven sources, one shared" FAILED
    java.lang.AssertionError at WildcardPackRunIT.java:93

WildcardPackRunIT > theInvariantsOfTheGroupedPackHoldForEveryDataset(String) > "empty feed under body A" FAILED
    java.lang.AssertionError at WildcardPackRunIT.java:95

WildcardPackRunIT > anAlternativeRunReusesTheParentsPackUnchanged() FAILED
    org.opentest4j.AssertionFailedError at WildcardPackRunIT.java:223

2026-10-07T09:52:23.753+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T09:52:23.754+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2026-10-07T09:52:23.755+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.
2026-10-07T09:52:23.804+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T09:52:23.804+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-2 - Shutdown initiated...
2026-10-07T09:52:23.804+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-2 - Shutdown completed.
2026-10-07T09:52:23.819+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T09:52:23.819+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-3 - Shutdown initiated...
2026-10-07T09:52:23.819+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-3 - Shutdown completed.
2026-10-07T09:52:23.831+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T09:52:23.832+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-4 - Shutdown initiated...
2026-10-07T09:52:23.832+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-4 - Shutdown completed.
2026-10-07T09:52:23.844+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T09:52:23.844+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-5 - Shutdown initiated...
2026-10-07T09:52:23.845+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-5 - Shutdown completed.

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

495 tests completed, 301 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 35s

```

RESULT: RED
