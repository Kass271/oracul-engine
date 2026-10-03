package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.TestcontainersConfiguration;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * research-pipeline.md "Run guard" / "Unit tests": {@code RunGuard.check(runId)} re-reads the run row and passes iff
 * {@code status = 'RUNNING'} and {@code now < deadline_at}, with {@code now} from the injected Clock (fixed here).
 * Only the contract {@code com.oracul.app.runs.RunGuard#check(UUID)} is assumed: a boolean (true = pass), or void that
 * throws when the check fails. The bean is reached by reflection so RED compiles; the run rows are plain SQL.
 */
// @trace FR-14, FR-15
@SpringBootTest
@Import({TestcontainersConfiguration.class, RunGuardTest.FixedClockConfig.class})
class RunGuardTest {

    static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;

    private final List<UUID> runs = new ArrayList<>();
    private final List<UUID> sessions = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID id : runs) jdbc.update("delete from generation_run where id = ?", id);
        for (UUID id : sessions) jdbc.update("delete from browser_session where id = ?", id);
    }

    private UUID insertRun(String status, Instant deadline) {
        UUID session = UUID.randomUUID();
        UUID run = UUID.randomUUID();
        OffsetDateTime created = OffsetDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC);
        jdbc.update("insert into browser_session (id, created_at, last_seen_at) values (?, ?, ?)", session, created, created);
        sessions.add(session);
        jdbc.update("insert into generation_run (id, generation_id, session_id, kind, status, stage, configuration, counts, "
                + "deadline_at, created_at, updated_at) values (?, ?, ?, 'STANDARD', ?, 'CONNECTING_SIGNALS', cast('{}' as jsonb), "
                + "cast('{}' as jsonb), ?, ?, ?)",
            run, "G" + run.toString().substring(0, 8), session, status, OffsetDateTime.ofInstant(deadline, ZoneOffset.UTC), created, created);
        runs.add(run);
        return run;
    }

    /** True when the guard lets the work continue. */
    private boolean passes(UUID runId) {
        Class<?> type;
        try {
            type = Class.forName("com.oracul.app.runs.RunGuard");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("com.oracul.app.runs.RunGuard is missing");
        }
        Object guard;
        try {
            guard = context.getBean(type);
        } catch (NoSuchBeanDefinitionException e) {
            throw new AssertionError("RunGuard is not a Spring bean (needs @Component): " + e.getMessage());
        }
        Method check;
        try {
            check = type.getDeclaredMethod("check", UUID.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("RunGuard.check(UUID runId) is missing");
        }
        try {
            check.setAccessible(true);
            Object result = check.invoke(guard, runId);
            if (check.getReturnType() == void.class) return true;
            if (result instanceof Boolean b) return b;
            throw new AssertionError("RunGuard.check must return boolean (true = the run may continue) or be void, but returns "
                + check.getReturnType());
        } catch (InvocationTargetException e) {
            return false; // a void check that throws means: stop
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void aRunningRunBeforeItsDeadlinePasses() {
        assertThat(passes(insertRun("RUNNING", NOW.plusSeconds(1)))).isTrue();
        assertThat(passes(insertRun("RUNNING", NOW.plusSeconds(180)))).isTrue();
    }

    @Test
    void aRunAtItsDeadlineFails() {
        assertThat(passes(insertRun("RUNNING", NOW))).as("now = deadline_at").isFalse();
    }

    @Test
    void aRunPastItsDeadlineFails() {
        assertThat(passes(insertRun("RUNNING", NOW.minusSeconds(1)))).as("now > deadline_at").isFalse();
        assertThat(passes(insertRun("RUNNING", NOW.minusSeconds(3600)))).isFalse();
    }

    @ParameterizedTest(name = "status {0} fails even before the deadline")
    @ValueSource(strings = {"QUEUED", "COMPLETED", "INSUFFICIENT_EVIDENCE", "FAILED"})
    void everyNonRunningStatusFails(String status) {
        assertThat(passes(insertRun(status, NOW.plusSeconds(100)))).isFalse();
    }

    @Test
    void theGuardReadsTheRowEveryTimeItIsChecked() {
        UUID run = insertRun("RUNNING", NOW.plusSeconds(100));
        assertThat(passes(run)).isTrue();
        jdbc.update("update generation_run set status = 'FAILED' where id = ?", run);
        assertThat(passes(run)).as("re-read, not cached").isFalse();
    }
}
