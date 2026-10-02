# Red evidence — 02_chatgpt-connection

Date: 2026-10-02 · FRs: FR-7, FR-8, FR-9

## Tagged tests

- FR-7 → `backend/src/test/java/com/oracul/app/chatgpt/ChatGptMisconfiguredIT.java` (backend)
- FR-7 → `backend/src/test/java/com/oracul/app/chatgpt/ChatGptPendingExpiryIT.java` (backend)
- FR-7 → `backend/src/test/java/com/oracul/app/chatgpt/ChatGptSignInIT.java` (backend)
- FR-7 → `backend/src/test/java/com/oracul/app/chatgpt/ChatGptStartupValidationTest.java` (backend)
- FR-7 → `backend/src/test/java/com/oracul/app/chatgpt/ChatGptTokenTimeoutIT.java` (backend)
- FR-7 → `e2e/tests/chatgpt-connection.spec.ts` (e2e)
- FR-7 → `frontend/src/app/chatgpt/chatgpt-connection.spec.ts` (frontend)
- FR-8 → `backend/src/test/java/com/oracul/app/chatgpt/ChatGptConnectionIT.java` (backend)
- FR-8 → `backend/src/test/java/com/oracul/app/session/BrowserSessionIT.java` (backend)
- FR-8 → `e2e/tests/chatgpt-connection.spec.ts` (e2e)
- FR-8 → `frontend/src/app/chatgpt/chatgpt-connection.spec.ts` (frontend)
- FR-9 → `backend/src/test/java/com/oracul/app/chatgpt/ChatGptCredentialHandlingIT.java` (backend)
- FR-9 → `e2e/tests/chatgpt-connection.spec.ts` (e2e)
- FR-9 → `frontend/src/app/chatgpt/chatgpt-connection.spec.ts` (frontend)

## backend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
        Caused by: org.postgresql.util.PSQLException at BrowserSessionIT.java:89

BrowserSessionIT > unknownOrMalformedCookieGetsANewSession() FAILED
    java.lang.AssertionError at BrowserSessionIT.java:79

BrowserSessionIT > missingCookieCreatesSessionRowAndSessionCookie() FAILED
    java.lang.AssertionError at BrowserSessionIT.java:56

2026-10-02T23:35:36.737+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-02T23:35:36.738+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2026-10-02T23:35:36.738+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.
2026-10-02T23:35:36.868+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-02T23:35:36.868+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-2 - Shutdown initiated...
2026-10-02T23:35:36.869+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-2 - Shutdown completed.
2026-10-02T23:35:36.973+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-02T23:35:36.973+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-3 - Shutdown initiated...
2026-10-02T23:35:36.973+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-3 - Shutdown completed.
2026-10-02T23:35:37.102+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-02T23:35:37.103+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-4 - Shutdown initiated...
2026-10-02T23:35:37.104+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-4 - Shutdown completed.
2026-10-02T23:35:37.233+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-02T23:35:37.233+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-5 - Shutdown initiated...
2026-10-02T23:35:37.239+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-5 - Shutdown completed.
2026-10-02T23:35:37.354+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-02T23:35:37.355+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-10 - Shutdown initiated...
2026-10-02T23:35:37.356+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-10 - Shutdown completed.
2026-10-02T23:35:37.459+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-02T23:35:37.459+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-11 - Shutdown initiated...
2026-10-02T23:35:37.459+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-11 - Shutdown completed.
2026-10-02T23:35:37.585+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2026-10-02T23:35:37.585+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-12 - Shutdown initiated...
2026-10-02T23:35:37.586+03:00  INFO 460 --- [oracul-engine] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-12 - Shutdown completed.

> Task :test FAILED
5 actionable tasks: 1 executed, 4 up-to-date
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

139 tests completed, 80 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/frederiks/courses/agent_crash/oracul/apps/oracul-engine/backend/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 19s

```

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
[90m [2m❯[22m startWith src/app/chatgpt/chatgpt-connection.spec.ts:[2m67:5[22m[39m
[90m [2m❯[22m startAfterReturn src/app/chatgpt/chatgpt-connection.spec.ts:[2m259:13[22m[39m
[90m [2m❯[22m src/app/chatgpt/chatgpt-connection.spec.ts:[2m282:7[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[18/21]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/chatgpt/chatgpt-connection.spec.ts[2m > [22mChatGPT connection header (slice 02_chatgpt-connection)[2m > [22mreturn from OpenAI (?chatgpt=)[2m > [22man unknown value shows no snackbar but is still removed from the URL
[31m[1mAssertionError[22m: expected 0 to be greater than or equal to 1[39m
[36m [2m❯[22m flushConnection src/app/chatgpt/chatgpt-connection.spec.ts:[2m60:28[22m[39m
    [90m 58|[39m   function flushConnection(state: ChatGptConnectionState): void {
    [90m 59|[39m     const pending = http.match((r) => isConnection(r));
    [90m 60|[39m     expect(pending.length).toBeGreaterThanOrEqual(1);
    [90m   |[39m                            [31m^[39m
    [90m 61|[39m     pending.forEach((r) => r.flush({ state, canGenerate: state === 'CO…
    [90m 62|[39m   }
[90m [2m❯[22m startWith src/app/chatgpt/chatgpt-connection.spec.ts:[2m67:5[22m[39m
[90m [2m❯[22m startAfterReturn src/app/chatgpt/chatgpt-connection.spec.ts:[2m259:13[22m[39m
[90m [2m❯[22m src/app/chatgpt/chatgpt-connection.spec.ts:[2m290:7[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[19/21]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/chatgpt/chatgpt-connection.spec.ts[2m > [22mChatGPT connection header (slice 02_chatgpt-connection)[2m > [22mreturn from OpenAI (?chatgpt=)[2m > [22mthe other query parameters survive the removal of chatgpt
[31m[1mAssertionError[22m: expected 0 to be greater than or equal to 1[39m
[36m [2m❯[22m flushConnection src/app/chatgpt/chatgpt-connection.spec.ts:[2m60:28[22m[39m
    [90m 58|[39m   function flushConnection(state: ChatGptConnectionState): void {
    [90m 59|[39m     const pending = http.match((r) => isConnection(r));
    [90m 60|[39m     expect(pending.length).toBeGreaterThanOrEqual(1);
    [90m   |[39m                            [31m^[39m
    [90m 61|[39m     pending.forEach((r) => r.flush({ state, canGenerate: state === 'CO…
    [90m 62|[39m   }
[90m [2m❯[22m startWith src/app/chatgpt/chatgpt-connection.spec.ts:[2m67:5[22m[39m
[90m [2m❯[22m src/app/chatgpt/chatgpt-connection.spec.ts:[2m299:13[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[20/21]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/chatgpt/chatgpt-connection.spec.ts[2m > [22mChatGPT connection header (slice 02_chatgpt-connection)[2m > [22mreturn from OpenAI (?chatgpt=)[2m > [22mwithout the parameter no snackbar is shown
[31m[1mAssertionError[22m: expected 0 to be greater than or equal to 1[39m
[36m [2m❯[22m flushConnection src/app/chatgpt/chatgpt-connection.spec.ts:[2m60:28[22m[39m
    [90m 58|[39m   function flushConnection(state: ChatGptConnectionState): void {
    [90m 59|[39m     const pending = http.match((r) => isConnection(r));
    [90m 60|[39m     expect(pending.length).toBeGreaterThanOrEqual(1);
    [90m   |[39m                            [31m^[39m
    [90m 61|[39m     pending.forEach((r) => r.flush({ state, canGenerate: state === 'CO…
    [90m 62|[39m   }
[90m [2m❯[22m startWith src/app/chatgpt/chatgpt-connection.spec.ts:[2m67:5[22m[39m
[90m [2m❯[22m src/app/chatgpt/chatgpt-connection.spec.ts:[2m305:13[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[21/21]⎯[22m[39m


```

RESULT: RED
