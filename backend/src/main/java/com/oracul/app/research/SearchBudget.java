package com.oracul.app.research;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** NFR-10 part 1: the time window of a search phase, measured on the injected clock and cut by the run's deadline. */
public record SearchBudget(Clock clock, Instant t0, Map<Phase, Duration> windows, Instant deadlineAt) {

    /** Phases with a window (later slices add more). */
    public enum Phase {
        QUERY_GENERATION, SEARCH, RETRIEVAL
    }

    public static SearchBudget search(Clock clock, Instant t0, Duration searchWindow, Instant deadlineAt) {
        return new SearchBudget(clock, t0, Map.of(Phase.SEARCH, searchWindow), deadlineAt);
    }

    public static SearchBudget retrieval(Clock clock, Instant t0, Duration stageBudget, Instant deadlineAt) {
        return new SearchBudget(clock, t0, Map.of(Phase.RETRIEVAL, stageBudget), deadlineAt);
    }

    public static SearchBudget generation(Clock clock, Instant t0, Duration window, Instant deadlineAt) {
        return new SearchBudget(clock, t0, Map.of(Phase.QUERY_GENERATION, window), deadlineAt);
    }

    /** max(0, min(t0 + window, deadlineAt) - now). */
    public Duration remaining(Phase phase) {
        Duration window = windows.get(phase);
        if (window == null) {
            throw new IllegalArgumentException("no window for phase " + phase);
        }
        Instant end = t0.plus(window);
        if (deadlineAt != null && deadlineAt.isBefore(end)) {
            end = deadlineAt;
        }
        Duration left = Duration.between(clock.instant(), end);
        return left.isNegative() ? Duration.ZERO : left;
    }

    public boolean expired(Phase phase) {
        return remaining(phase).isZero();
    }
}
