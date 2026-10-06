# Red evidence — 02_safe-fetching

Date: 2026-10-06 · FRs: FR-56
Scope: slice (related tests only — the slice gate runs the full suite)

## Tagged tests

- FR-56 → `backend/src/test/java/com/oracul/app/FetchExemptionComposeTest.java` (backend)
- FR-56 → `backend/src/test/java/com/oracul/app/research/ArticleMetadataFetcherTest.java` (backend)
- FR-56 → `backend/src/test/java/com/oracul/app/research/SafeFetcherTest.java` (backend)
- FR-56 → `backend/src/test/java/com/oracul/app/research/SourceMetadataIT.java` (backend)
- FR-56 → `backend/src/test/java/com/oracul/app/research/SourceRetrievalUnitTest.java` (backend)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
    org.opentest4j.AssertionFailedError at ArticleMetadataFetcherTest.java:65

ArticleMetadataFetcherTest > fetchOfAStringFollowsFiveRedirectsAndTheArgumentLowersTheLimit() FAILED
    org.opentest4j.AssertionFailedError at ArticleMetadataFetcherTest.java:65

ArticleMetadataFetcherTest > aRedirectWithoutLocationGivesNoResult() FAILED
    org.opentest4j.AssertionFailedError at ArticleMetadataFetcherTest.java:65

ArticleMetadataFetcherTest > anUnreachableHostGivesNoResult() FAILED
    org.opentest4j.AssertionFailedError at ArticleMetadataFetcherTest.java:65

ArticleMetadataFetcherTest > anUnknownCharsetFallsBackToUtf8() FAILED
    org.opentest4j.AssertionFailedError at ArticleMetadataFetcherTest.java:65

ArticleMetadataFetcherTest > fetchReadsDescriptionAndSiteNameOfAnHtmlPage() FAILED
    org.opentest4j.AssertionFailedError at ArticleMetadataFetcherTest.java:65

ArticleMetadataFetcherTest > moreRedirectsThanAllowedGiveNoResult() FAILED
    org.opentest4j.AssertionFailedError at ArticleMetadataFetcherTest.java:65


> Task :test

SourceMetadataIT > refusedRedirectTargetsLeaveTheFeedLinkAsContentNotRetrieved() FAILED
    java.lang.AssertionError at SourceMetadataIT.java:158

SourceMetadataIT > bigPagesAreReadUpToTwoMegabytesAndNoFurther() FAILED
    org.opentest4j.AssertionFailedError at SourceMetadataIT.java:192

2026-10-07T01:19:52.097+03:00  INFO [SpringApplicationShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-07T01:19:52.099+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2026-10-07T01:19:52.100+03:00  INFO [SpringApplicationShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.

> Task :test FAILED
6 actionable tasks: 1 executed, 5 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

126 tests completed, 120 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 19s

```

RESULT: RED
