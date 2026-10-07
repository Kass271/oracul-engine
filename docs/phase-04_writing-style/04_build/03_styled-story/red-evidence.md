# Red evidence — 03_styled-story

Date: 2026-10-07 · FRs: FR-65, FR-66
Scope: slice (related tests only — the slice gate runs the full suite)

## Tagged tests

- FR-65 → `backend/src/test/java/com/oracul/app/result/StoryStyleTest.java` (backend)
- FR-65 → `backend/src/test/java/com/oracul/app/result/StoryWritingIT.java` (backend)
- FR-65 → `backend/src/test/java/com/oracul/app/result/StoryWritingPromptTest.java` (backend)
- FR-65 → `backend/src/test/java/com/oracul/app/result/StyledStoryIT.java` (backend)
- FR-65 → `e2e/tests/styled-story.spec.ts` (e2e)
- FR-66 → `backend/src/test/java/com/oracul/app/result/DatelinesTest.java` (backend)
- FR-66 → `backend/src/test/java/com/oracul/app/result/StoryParserTest.java` (backend)
- FR-66 → `backend/src/test/java/com/oracul/app/result/StoryStyleTest.java` (backend)
- FR-66 → `backend/src/test/java/com/oracul/app/result/StoryWritingPromptTest.java` (backend)
- FR-66 → `backend/src/test/java/com/oracul/app/result/StyledStoryIT.java` (backend)
- FR-66 → `e2e/tests/styled-story.spec.ts` (e2e)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
    org.opentest4j.AssertionFailedError at StyledStoryIT.java:220

StyledStoryIT > anAlternativeOfTheKafkaUkrainianRunSendsTheKafkaUkrainianSection() FAILED
    java.lang.AssertionError at StyledStoryIT.java:177

StyledStoryIT > aKafkaUkrainianRunStoresAndServesTheUkrainianStoryWithEnglishLabels() FAILED
    org.opentest4j.AssertionFailedError at StyledStoryIT.java:220

StyledStoryIT > theRecordedStoryRequestCarriesTheSectionOfTheRunsStyle(String, String, String) > "OFFICIAL" / "official-briefing" / "en" FAILED
    java.lang.AssertionError at StyledStoryIT.java:104

StyledStoryIT > theRecordedStoryRequestCarriesTheSectionOfTheRunsStyle(String, String, String) > "OFFICIAL" / "official-briefing" / "uk" FAILED
    java.lang.AssertionError at StyledStoryIT.java:104

StyledStoryIT > theRecordedStoryRequestCarriesTheSectionOfTheRunsStyle(String, String, String) > "NARRATIVE" / "neutral-narrative" / "en" FAILED
    java.lang.AssertionError at StyledStoryIT.java:104

StyledStoryIT > theRecordedStoryRequestCarriesTheSectionOfTheRunsStyle(String, String, String) > "NARRATIVE" / "kafka" / "uk" FAILED
    java.lang.AssertionError at StyledStoryIT.java:104

StyledStoryIT > theRecordedStoryRequestCarriesTheSectionOfTheRunsStyle(String, String, String) > "NARRATIVE" / "poe" / "en" FAILED
    java.lang.AssertionError at StyledStoryIT.java:104

StyledStoryIT > theDateFallbackRebuildsTheDatelineInTheRunsLanguage(String) > date fallback, language "uk" FAILED
    org.opentest4j.AssertionFailedError at StyledStoryIT.java:224

StyledStoryIT > bothAttemptsOfOneStageSendTheSameStyleSection() FAILED
    java.lang.AssertionError at StyledStoryIT.java:206

2026-10-08T01:25:16.252+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-08T01:25:16.254+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2026-10-08T01:25:16.254+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

199 tests completed, 129 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 16s

```

RESULT: RED
