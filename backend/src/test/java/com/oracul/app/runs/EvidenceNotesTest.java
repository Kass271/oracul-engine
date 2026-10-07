package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.EvidenceNoteKind;
import com.oracul.app.research.MinCoreThresholds;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * wildcard-evidence.md slice 06 (FR-57 step 4): the note decision of a wildcard pack is {@code decide(total, total, realism,
 * thresholds)} with total = the distinct Evidence IDs of the pack. Pure test of the decision table over every realism 1-10 and
 * every pack size 0-7 with thresholds 5 / 3 / 2; the rows that the integration suite cannot reach with the default thresholds
 * 5 / 3 / 1 (realism 3 -> 1, 2 -> 1, realism 1 with a note but no suggestion) are asserted here.
 */
// @trace FR-47, FR-57
class EvidenceNotesTest {

    private static final MinCoreThresholds THRESHOLDS = new MinCoreThresholds(5, 3, 2);

    private static int needed(int realism) {
        return realism >= 9 ? 5 : realism >= 6 ? 3 : 2;
    }

    static Stream<Arguments> everyRealismAndPackSize() {
        return IntStream.rangeClosed(1, 10).boxed().flatMap(realism -> IntStream.rangeClosed(0, 7).mapToObj(n -> Arguments.of(realism, n)));
    }

    @ParameterizedTest(name = "realism {0}, {1} Evidence IDs")
    @MethodSource("everyRealismAndPackSize")
    void theDecisionFollowsTheTableForEveryRealismAndPackSize(int realism, int n) {
        Optional<EvidenceNotes.Decision> decision = EvidenceNotes.decide(n, n, realism, THRESHOLDS);
        int needed = needed(realism);
        if (n == 0) {
            assertThat(decision).as("total 0 <=> NO_EVIDENCE").isPresent();
            assertThat(decision.get().kind()).isEqualTo(EvidenceNoteKind.NO_EVIDENCE);
            assertThat(decision.get().coreItems()).isZero();
            assertThat(decision.get().coreNeeded()).isEqualTo(needed);
            assertThat(decision.get().suggestedRealism()).as("NO_EVIDENCE has no suggestion").isNull();
        } else if (n < needed) {
            assertThat(decision).as("n < needed <=> INSUFFICIENT_EVIDENCE").isPresent();
            assertThat(decision.get().kind()).isEqualTo(EvidenceNoteKind.INSUFFICIENT_EVIDENCE);
            assertThat(decision.get().coreItems()).isEqualTo(n);
            assertThat(decision.get().coreNeeded()).isEqualTo(needed);
            if (realism > 1) assertThat(decision.get().suggestedRealism()).as("max(1, realism - 2)").isEqualTo(Math.max(1, realism - 2));
            else assertThat(decision.get().suggestedRealism()).as("realism 1: a note without a suggestion").isNull();
        } else {
            assertThat(decision).as("the evidence meets the realism: no note").isEmpty();
        }
    }

    static Stream<Arguments> suggestions() {
        return Stream.of(Arguments.of(10, 8), Arguments.of(9, 7), Arguments.of(8, 6), Arguments.of(3, 1), Arguments.of(2, 1));
    }

    @ParameterizedTest(name = "realism {0} -> suggestedRealism {1}")
    @MethodSource("suggestions")
    void theSuggestedRealismIsTwoLowerButAtLeastOne(int realism, int suggested) {
        EvidenceNotes.Decision d = EvidenceNotes.decide(1, 1, realism, THRESHOLDS).orElseThrow();
        assertThat(d.kind()).isEqualTo(EvidenceNoteKind.INSUFFICIENT_EVIDENCE);
        assertThat(d.suggestedRealism()).isEqualTo(suggested);
    }

    @Test
    void realismOneHasANoteButNoSuggestion() {
        EvidenceNotes.Decision d = EvidenceNotes.decide(1, 1, 1, THRESHOLDS).orElseThrow();
        assertThat(d.kind()).isEqualTo(EvidenceNoteKind.INSUFFICIENT_EVIDENCE);
        assertThat(d.coreItems()).isEqualTo(1);
        assertThat(d.coreNeeded()).isEqualTo(2);
        assertThat(d.suggestedRealism()).isNull();
    }
}
