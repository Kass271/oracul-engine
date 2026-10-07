package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oracul.app.api.model.EvidenceNote;
import com.oracul.app.api.model.EvidenceNoteKind;
import com.oracul.app.research.MinCoreThresholds;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * wildcard-evidence.md "Slice 09_wildcard-results" (FR-59 steps 1-6): the pure note decision of a wildcard pack with the labels of
 * the wildcards that kept no source. The new {@code decide(int, List, int, MinCoreThresholds)}, {@code missingSentence}, the
 * 5-argument {@code message} / {@code note} and {@code Decision.wildcardsWithoutSources} do not exist before the slice, so they are
 * reached by reflection: a missing member is an AssertionError naming it, not a compile error.
 */
// @trace FR-59
class EvidenceNotesWildcardTest {

    private static final MinCoreThresholds THRESHOLDS = new MinCoreThresholds(5, 3, 2);
    private static final String NO_EVIDENCE_TEXT = "No current news could be used — this future is speculative, not grounded in evidence.";

    private static int needed(int realism) {
        return realism >= 9 ? 5 : realism >= 6 ? 3 : 2;
    }

    // ---- reflection ----------------------------------------------------------------------------------------------

    private static Method method(String name, Class<?>... params) {
        try {
            Method m = EvidenceNotes.class.getDeclaredMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            throw new AssertionError("EvidenceNotes." + name + "(" + String.join(", ", java.util.Arrays.stream(params).map(Class::getSimpleName).toList())
                + ") is missing");
        }
    }

    private static Object invoke(Method m, Object... args) {
        try {
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException re) throw re;
            throw new AssertionError(m.getName() + " failed: " + e.getTargetException(), e.getTargetException());
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Optional<Object> decide(int total, List<String> missing, int realism) {
        return (Optional<Object>) invoke(method("decide", int.class, List.class, int.class, MinCoreThresholds.class), total, missing, realism,
            THRESHOLDS);
    }

    private static Object accessor(Object decision, String name) {
        try {
            Method m = decision.getClass().getDeclaredMethod(name);
            m.setAccessible(true);
            return m.invoke(decision);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("EvidenceNotes.Decision." + name + "() is missing: " + e);
        }
    }

    private static String missingSentence(List<String> labels) {
        return (String) invoke(method("missingSentence", List.class), labels);
    }

    private static String message(EvidenceNoteKind kind, int realism, int core, int needed, List<String> wildcards) {
        return (String) invoke(method("message", EvidenceNoteKind.class, int.class, int.class, int.class, List.class), kind, realism, core, needed,
            wildcards);
    }

    private static EvidenceNote note(EvidenceNoteKind kind, int realism, int core, int needed, List<String> wildcards) {
        return (EvidenceNote) invoke(method("note", EvidenceNoteKind.class, int.class, int.class, int.class, List.class), kind, realism, core, needed,
            wildcards);
    }

    @SuppressWarnings("unchecked")
    private static List<String> wildcardsOf(Object decision) {
        return (List<String>) accessor(decision, "wildcardsWithoutSources");
    }

    private static String sentence(String... labels) {
        return "No current sources found for: " + String.join(", ", labels) + ". This part of the future is speculative.";
    }

    private static String insufficient(int realism, int core, int needed) {
        return "Realism " + realism + " couldn't be fully met: only " + core + " core evidence " + (core == 1 ? "item" : "items") + " (needs "
            + needed + "). This future is less grounded.";
    }

    // ---- (a) decision table --------------------------------------------------------------------------------------

    static final List<List<String>> MISSING = List.of(List.of(), List.of("Energy crisis"), List.of("Energy crisis", "AI takeover"));

    /** Realism 1-10 x total in {0, needed - 1 (if >= 1), needed, needed + 1} x missing in {[], one, two}. */
    static Stream<Arguments> table() {
        return IntStream.rangeClosed(1, 10).boxed().flatMap(realism -> {
            int needed = needed(realism);
            List<Integer> totals = new ArrayList<>(List.of(0));
            if (needed - 1 >= 1) totals.add(needed - 1);
            totals.add(needed);
            totals.add(needed + 1);
            return totals.stream().flatMap(total -> IntStream.range(0, MISSING.size()).mapToObj(m -> Arguments.of(realism, total, m)));
        });
    }

    @ParameterizedTest(name = "realism {0}, total {1}, missing #{2}")
    @MethodSource("table")
    void theDecisionFollowsTheTableForEveryRealismTotalAndMissingSet(int realism, int total, int missingIndex) {
        List<String> missing = MISSING.get(missingIndex);
        int needed = needed(realism);
        Optional<Object> d = decide(total, missing, realism);
        if (total == 0) {
            assertThat(d).as("total 0 <=> NO_EVIDENCE").isPresent();
            assertThat(accessor(d.get(), "kind")).isEqualTo(EvidenceNoteKind.NO_EVIDENCE);
            assertThat(accessor(d.get(), "coreItems")).isEqualTo(0);
            assertThat(accessor(d.get(), "coreNeeded")).isEqualTo(needed);
            assertThat(accessor(d.get(), "suggestedRealism")).as("no suggestion").isNull();
            assertThat(wildcardsOf(d.get())).as("whatever the missing set").isEmpty();
            assertThat(message(EvidenceNoteKind.NO_EVIDENCE, realism, 0, needed, missing)).isEqualTo(NO_EVIDENCE_TEXT);
        } else if (total < needed) {
            assertThat(d).as("0 < total < needed <=> INSUFFICIENT_EVIDENCE").isPresent();
            assertThat(accessor(d.get(), "kind")).isEqualTo(EvidenceNoteKind.INSUFFICIENT_EVIDENCE);
            assertThat(accessor(d.get(), "coreItems")).isEqualTo(total);
            assertThat(accessor(d.get(), "coreNeeded")).isEqualTo(needed);
            if (realism > 1) assertThat(accessor(d.get(), "suggestedRealism")).isEqualTo(Math.max(1, realism - 2));
            else assertThat(accessor(d.get(), "suggestedRealism")).isNull();
            assertThat(wildcardsOf(d.get())).isEqualTo(missing);
            String expected = (missing.isEmpty() ? "" : sentence(missing.toArray(String[]::new)) + " ") + insufficient(realism, total, needed);
            assertThat(message(EvidenceNoteKind.INSUFFICIENT_EVIDENCE, realism, total, needed, wildcardsOf(d.get()))).isEqualTo(expected);
        } else if (!missing.isEmpty()) {
            assertThat(d).as("enough evidence but a wildcard without sources").isPresent();
            assertThat(accessor(d.get(), "kind")).isEqualTo(EvidenceNoteKind.MISSING_WILDCARD_SOURCES);
            assertThat(accessor(d.get(), "coreItems")).isEqualTo(total);
            assertThat(accessor(d.get(), "coreNeeded")).isEqualTo(needed);
            assertThat(accessor(d.get(), "suggestedRealism")).as("MISSING_WILDCARD_SOURCES has no suggestion").isNull();
            assertThat(wildcardsOf(d.get())).isEqualTo(missing);
            assertThat(message(EvidenceNoteKind.MISSING_WILDCARD_SOURCES, realism, total, needed, missing))
                .isEqualTo(sentence(missing.toArray(String[]::new)));
        } else {
            assertThat(d).as("the evidence meets the realism and every wildcard has sources: no note").isEmpty();
        }
    }

    // ---- (b) message classes -------------------------------------------------------------------------------------

    static Stream<Arguments> sentences() {
        return Stream.of(
            Arguments.of(List.of("Energy crisis"), "No current sources found for: Energy crisis. This part of the future is speculative."),
            Arguments.of(List.of("Energy crisis", "AI takeover"),
                "No current sources found for: Energy crisis, AI takeover. This part of the future is speculative."),
            Arguments.of(List.of("AI takeover", "Energy crisis"),
                "No current sources found for: AI takeover, Energy crisis. This part of the future is speculative."),
            Arguments.of(List.of("Odd, label", "<b>x</b>"),
                "No current sources found for: Odd, label, <b>x</b>. This part of the future is speculative."));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sentences")
    void theMissingSentenceJoinsTheLabelsVerbatimInTheGivenOrder(List<String> labels, String expected) {
        assertThat(missingSentence(labels)).isEqualTo(expected);
        assertThat(message(EvidenceNoteKind.MISSING_WILDCARD_SOURCES, 8, 4, 0, labels)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "core {0}")
    @MethodSource("coreCounts")
    void theInsufficientSentenceSaysItemOnlyForOne(int core) {
        String m = message(EvidenceNoteKind.INSUFFICIENT_EVIDENCE, 8, core, 3, List.of("Energy crisis"));
        assertThat(m).isEqualTo(sentence("Energy crisis") + " " + insufficient(8, core, 3));
        assertThat(m).contains(core == 1 ? "only 1 core evidence item (" : "core evidence items (");
        assertThat(message(EvidenceNoteKind.INSUFFICIENT_EVIDENCE, 8, core, 3, List.of())).as("no prefix without wildcards")
            .isEqualTo(insufficient(8, core, 3));
    }

    static Stream<Integer> coreCounts() {
        return Stream.of(1, 2);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("noteKinds")
    void theNoteCarriesTheWildcardsExactlyWhenTheyAreNonEmptyAndTheKindIsNotNoEvidence(EvidenceNoteKind kind, List<String> wildcards, boolean carried) {
        EvidenceNote n = note(kind, 8, 2, 3, wildcards);
        assertThat(n.getKind()).isEqualTo(kind);
        assertThat(n.getMessage()).isEqualTo(message(kind, 8, 2, 3, wildcards));
        assertThat(n.getCoreItems()).isEqualTo(2);
        assertThat(n.getCoreNeeded()).isEqualTo(3);
        if (carried) assertThat(n.getWildcardsWithoutSources()).isEqualTo(wildcards);
        else assertThat(n.getWildcardsWithoutSources() == null || n.getWildcardsWithoutSources().isEmpty()).isTrue();
    }

    static Stream<Arguments> noteKinds() {
        List<String> one = List.of("Energy crisis");
        return Stream.of(
            Arguments.of(EvidenceNoteKind.MISSING_WILDCARD_SOURCES, one, true),
            Arguments.of(EvidenceNoteKind.INSUFFICIENT_EVIDENCE, one, true),
            Arguments.of(EvidenceNoteKind.INSUFFICIENT_EVIDENCE, List.<String>of(), false),
            Arguments.of(EvidenceNoteKind.NO_EVIDENCE, one, false),
            Arguments.of(EvidenceNoteKind.NO_EVIDENCE, List.<String>of(), false));
    }

    // ---- (c) length ----------------------------------------------------------------------------------------------

    @Test
    void thirtyThreeLabelsOfFortyCharactersStayWithinTheMessageBound() {
        List<String> labels = IntStream.rangeClosed(1, 33).mapToObj(i -> String.format("%02d", i) + "x".repeat(38)).toList();
        Optional<Object> d = decide(1, labels, 10);
        assertThat(d).isPresent();
        assertThat(accessor(d.get(), "kind")).isEqualTo(EvidenceNoteKind.INSUFFICIENT_EVIDENCE);
        assertThat(wildcardsOf(d.get())).hasSize(33);
        String m = message(EvidenceNoteKind.INSUFFICIENT_EVIDENCE, 10, 1, 5, wildcardsOf(d.get()));
        assertThat(m.length()).isBetween(1, 2000);
        assertThat(m).startsWith("No current sources found for: 01");
    }

    // ---- (d) the input list is not mutated, the result is unmodifiable ------------------------------------------

    @Test
    void theMissingListIsNeitherMutatedNorSortedAndTheResultIsUnmodifiable() {
        List<String> missing = new ArrayList<>(List.of("Zeta", "Alpha", "Zeta"));
        List<String> before = List.copyOf(missing);
        Optional<Object> d = decide(4, missing, 8);
        assertThat(d).isPresent();
        assertThat(missing).as("no sorting, no de-duplication, no mutation").isEqualTo(before);
        assertThat(wildcardsOf(d.get())).isEqualTo(before);
        assertThatThrownBy(() -> wildcardsOf(d.get()).add("x")).isInstanceOf(UnsupportedOperationException.class);
        missing.add("later");
        assertThat(wildcardsOf(d.get())).as("a copy, not a view").isEqualTo(before);
    }

    @Test
    void aRealismOutsideOneToTenIsRejected() {
        assertThatThrownBy(() -> decide(3, List.of(), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> decide(3, List.of(), 11)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- (e) the phase-02 overload is unchanged ------------------------------------------------------------------

    static Stream<Arguments> everyRealismAndPackSize() {
        return IntStream.rangeClosed(1, 10).boxed().flatMap(realism -> IntStream.rangeClosed(0, 7).mapToObj(n -> Arguments.of(realism, n)));
    }

    @ParameterizedTest(name = "realism {0}, total {1}")
    @MethodSource("everyRealismAndPackSize")
    void theFourArgumentOverloadAndTheNewOneAgreeWithoutWildcards(int realism, int n) {
        Optional<EvidenceNotes.Decision> old = EvidenceNotes.decide(n, n, realism, THRESHOLDS);
        Optional<Object> now = decide(n, List.of(), realism);
        assertThat(now.isPresent()).isEqualTo(old.isPresent());
        if (old.isPresent()) {
            assertThat(accessor(now.get(), "kind")).isEqualTo(old.get().kind());
            assertThat(accessor(now.get(), "coreItems")).isEqualTo(old.get().coreItems());
            assertThat(accessor(now.get(), "coreNeeded")).isEqualTo(old.get().coreNeeded());
            assertThat(accessor(now.get(), "suggestedRealism")).isEqualTo(old.get().suggestedRealism());
            assertThat(wildcardsOf(now.get())).isEmpty();
            EvidenceNote old4 = EvidenceNotes.note(old.get().kind(), realism, old.get().coreItems(), old.get().coreNeeded());
            EvidenceNote new5 = note(old.get().kind(), realism, old.get().coreItems(), old.get().coreNeeded(), List.of());
            assertThat(new5.getMessage()).isEqualTo(old4.getMessage());
            assertThat(EvidenceNotes.message(old.get().kind(), realism, old.get().coreItems(), old.get().coreNeeded())).isEqualTo(old4.getMessage());
        }
    }
}
