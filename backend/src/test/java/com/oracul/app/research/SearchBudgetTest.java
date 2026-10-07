package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oracul.app.runs.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * NFR-10 part 1 / FR-52 (slice 03 delta): the pure {@code SearchBudget(Clock, t0, windows, deadlineAt)} for phase
 * SEARCH, with a fixed (mutable) clock. Reached reflectively: the record does not exist while this test is written.
 */
// @trace FR-52, NFR-10
@Timeout(20)
class SearchBudgetTest {

    private static final Instant T0 = Instant.parse("2026-10-07T12:00:00Z");
    private static final Duration WINDOW = Duration.ofSeconds(60);

    private static MutableClock clockAt(long offsetMs) {
        MutableClock c = new MutableClock();
        c.set(T0.plusMillis(offsetMs));
        return c;
    }

    static Stream<Arguments> offsets() {
        // offset from t0 in ms, expected remaining in ms, expected expired — boundary classes of a 60 s window
        return Stream.of(
            Arguments.of(0L, 60_000L, false),
            Arguments.of(1L, 59_999L, false),
            Arguments.of(30_000L, 30_000L, false),
            Arguments.of(59_000L, 1_000L, false),
            Arguments.of(59_999L, 1L, false),
            Arguments.of(60_000L, 0L, true),
            Arguments.of(60_001L, 0L, true),
            Arguments.of(120_000L, 0L, true));
    }

    // remaining(window) at t0, window - 1 ms, window, after the window: never negative
    @ParameterizedTest(name = "no deadline, {0} ms after t0: remaining {1} ms, expired {2}")
    @MethodSource("offsets")
    void remainingFollowsTheWindow(long offsetMs, long remainingMs, boolean expired) {
        Object budget = ParallelSearchSupport.budget(clockAt(offsetMs), T0, WINDOW, null);
        assertThat(ParallelSearchSupport.remaining(budget, "SEARCH")).isEqualTo(Duration.ofMillis(remainingMs));
        assertThat(ParallelSearchSupport.expired(budget, "SEARCH")).isEqualTo(expired);
    }

    static Stream<Arguments> deadlines() {
        // deadline offset from t0 in ms (earlier, equal and later than the 60 s window), clock offset, expected remaining
        return Stream.of(
            Arguments.of(10_000L, 0L, 10_000L, false),
            Arguments.of(10_000L, 9_999L, 1L, false),
            Arguments.of(10_000L, 10_000L, 0L, true),
            Arguments.of(10_000L, 20_000L, 0L, true),
            Arguments.of(60_000L, 59_999L, 1L, false),
            Arguments.of(90_000L, 0L, 60_000L, false),
            Arguments.of(90_000L, 59_999L, 1L, false),
            Arguments.of(90_000L, 60_000L, 0L, true));
    }

    // the earlier of window end and deadline wins
    @ParameterizedTest(name = "deadline t0+{0} ms, clock t0+{1} ms: remaining {2} ms, expired {3}")
    @MethodSource("deadlines")
    void theDeadlineWinsWhenItIsEarlierThanTheWindow(long deadlineMs, long clockMs, long remainingMs, boolean expired) {
        Object budget = ParallelSearchSupport.budget(clockAt(clockMs), T0, WINDOW, T0.plusMillis(deadlineMs));
        assertThat(ParallelSearchSupport.remaining(budget, "SEARCH")).isEqualTo(Duration.ofMillis(remainingMs));
        assertThat(ParallelSearchSupport.expired(budget, "SEARCH")).isEqualTo(expired);
    }

    @Test
    void theBudgetFollowsTheClockItWasGiven() {
        MutableClock clock = clockAt(0);
        Object budget = ParallelSearchSupport.budget(clock, T0, WINDOW, null);
        assertThat(ParallelSearchSupport.remaining(budget, "SEARCH")).isEqualTo(WINDOW);
        clock.advance(Duration.ofSeconds(45));
        assertThat(ParallelSearchSupport.remaining(budget, "SEARCH")).isEqualTo(Duration.ofSeconds(15));
        clock.advance(Duration.ofSeconds(15));
        assertThat(ParallelSearchSupport.expired(budget, "SEARCH")).isTrue();
    }

    @Test
    void aPhaseWithoutAWindowIsRejected() {
        Object budget = ParallelSearchSupport.budgetOf(clockAt(0), T0, Map.of(), null);
        assertThatThrownBy(() -> ParallelSearchSupport.remaining(budget, "SEARCH")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ParallelSearchSupport.expired(budget, "SEARCH")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theCanonicalConstructorTakesTheWindowsMap() {
        Object budget = ParallelSearchSupport.budgetOf(clockAt(59_999), T0, Map.of("SEARCH", WINDOW), null);
        assertThat(ParallelSearchSupport.remaining(budget, "SEARCH")).isEqualTo(Duration.ofMillis(1));
    }
}
