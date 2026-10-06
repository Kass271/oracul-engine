package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.result.AbstractStoryIT;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Shared driver of the slice 11 tests (generation-runs.md "Slice 11_run-failures"): MutableClock, row snapshots and the
 * sweep entry points. The new beans ({@code RunDeadlineScheduler}, {@code RunStartupSweep}) are reached by reflection so
 * the tests compile before they exist; their contract is {@code public int sweep()}.
 */
@Import(MutableClockConfig.class)
@TestPropertySource(properties = {
    "oracul.run.deadline-check-interval=PT1H",
    "oracul.run.min-stage-duration=PT0S",
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.openai.retry-delay=PT0S",
})
public abstract class AbstractDeadlineIT extends AbstractStoryIT {

    protected static final String T = "Generation took too long — try again";
    protected static final String I = "Generation was interrupted — try again";
    protected static final String SCHEDULER = "com.oracul.app.runs.RunDeadlineScheduler";
    protected static final String STARTUP = "com.oracul.app.runs.RunStartupSweep";
    protected static final MutableClock clock = MutableClockConfig.CLOCK;

    @Autowired
    protected ApplicationContext context;

    @BeforeEach
    void resetClock() {
        clock.set(Instant.now());
    }

    /** Opens every gate so no handler thread stays parked and the leftover runs of a test can end. */
    @AfterEach
    void openGates() {
        responses.reset();
    }

    /** Calls {@code public int sweep()} of the bean of the class. */
    protected int sweep(String className) {
        Class<?> type;
        try {
            type = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(className + " is missing");
        }
        Object bean;
        try {
            bean = context.getBean(type);
        } catch (NoSuchBeanDefinitionException e) {
            throw new AssertionError(className + " is not a Spring bean (needs @Component): " + e.getMessage());
        }
        Method m;
        try {
            m = type.getMethod("sweep");
        } catch (NoSuchMethodException e) {
            throw new AssertionError(className + ".sweep() is missing");
        }
        try {
            return (Integer) m.invoke(bean);
        } catch (InvocationTargetException e) {
            throw new AssertionError(className + ".sweep() threw " + e.getCause(), e.getCause());
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    protected void freshStubs() {
        responses.reset(); // arrival counters and recorded requests of earlier runs must not satisfy awaitArrived
        news.reset();
        newsArticles(v4());
        script(NORMALIZATION, N_V4);
        script(CLASSIFICATION, C_V4);
    }

    /** status, stage, failure, timestamps and counts of the run row. */
    protected Map<String, Object> row(String runId) {
        return jdbc.queryForMap("select status, stage, failure_code, failure_message, updated_at, completed_at, "
            + "cast(counts as text) as counts from generation_run where id = cast(? as uuid)", runId);
    }

    protected void assertTimedOut(Map<String, Object> run, String stage, int stageIndex) {
        assertThat(run.get("status")).as("run: " + run).isEqualTo("FAILED");
        assertThat(run.get("failure")).isEqualTo(json("{\"code\":\"RUN_TIMEOUT\",\"message\":\"" + T + "\"}"));
        assertThat(run.get("stage")).isEqualTo(stage);
        assertThat(run.get("stageIndex")).isEqualTo(stageIndex);
        assertThat(run.get("completedAt")).isNotNull();
    }

    protected void assertSlotReleased(String sid) throws Exception {
        assertThat(startRun(sid, B).andReturn().getResponse().getStatus()).as("active-run slot released").isEqualTo(202);
    }

    protected Map<String, Object> runOf(String sid, String id) throws Exception {
        return json(getRun(sid, id));
    }

    protected void waitForStage(String sid, String id, String stage) throws Exception {
        awaitRun(sid, id, 10_000, m -> stage.equals(m.get("stage")));
    }

    /** Watches for {@code ms}: the row never differs from {@code before} and the request counts stay as given. */
    protected void watchUnchanged(String id, Map<String, Object> before, long ms, Runnable alsoCheck) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            assertThat(row(id)).as("the pipeline must not touch a run that was ended by the deadline").isEqualTo(before);
            alsoCheck.run();
            Thread.sleep(100);
        }
    }

    protected static Instant instantOf(Object isoOrOffset) {
        return OffsetDateTime.parse(String.valueOf(isoOrOffset)).toInstant();
    }

    protected static Duration d(long seconds) {
        return Duration.ofSeconds(seconds);
    }
}
