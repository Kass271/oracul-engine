package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.HorizonCode;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * FR-52 (slice 03 delta): the pure rules of the text sent to Google News — cleaning of a planned query text and the
 * {@code when:<N>d} suffix per horizon. They replace the OR-group element rules of the deleted {@code GoogleQueryGroups}
 * (its former test was {@code GoogleQueryGroupsRulesTest}). The class is reached reflectively (it does not exist while
 * this test is written), so the tests fail on assertions.
 */
// @trace FR-52
@Timeout(10)
class GoogleNewsSearchTextTest {

    static Stream<Arguments> texts() {
        return Stream.of(
            Arguments.of("plain text stays", "pandemic vaccine", "pandemic vaccine"),
            Arguments.of("quotes become spaces", "\"mRNA vaccine\" approval", "mRNA vaccine approval"),
            Arguments.of("parentheses become spaces, OR is removed", "(fusion OR fission) energy", "fusion fission energy"),
            Arguments.of("curly quotes become spaces", "“curly” quotes", "curly quotes"),
            Arguments.of("whitespace is collapsed and trimmed", "a  b\tc", "a b c"),
            Arguments.of("leading and trailing blanks go", "   solar flare  ", "solar flare"),
            Arguments.of("lower-case or is a word", "war or peace", "war or peace"),
            Arguments.of("AND is removed", "wind AND solar", "wind solar"),
            Arguments.of("NOT is removed", "wind NOT solar", "wind solar"),
            Arguments.of("OR inside a word stays", "ORACUL FORECAST", "ORACUL FORECAST"),
            Arguments.of("only operators: nothing is left", "OR AND NOT", null),
            Arguments.of("only blanks: nothing is left", "   ", null),
            Arguments.of("only quotes and parentheses: nothing is left", "\"()\"", null),
            Arguments.of("empty", "", null),
            Arguments.of("null", null, null));
    }

    // ranges: every class of the sent-text rule (a)
    @ParameterizedTest(name = "{0}: [{1}] -> [{2}]")
    @MethodSource("texts")
    void theSentTextIsTheCleanedPlannedText(String name, String planned, String expected) {
        assertThat(ParallelSearchSupport.text(planned)).as(name).isEqualTo(expected);
    }

    static Stream<Arguments> horizons() {
        return Stream.of(
            Arguments.of(HorizonCode._1D, 7), Arguments.of(HorizonCode._1W, 7), Arguments.of(HorizonCode._1M, 14),
            Arguments.of(HorizonCode._1Y, 90), Arguments.of(HorizonCode._5Y, 90), Arguments.of(HorizonCode._10Y, 90),
            Arguments.of(HorizonCode._20Y, 90));
    }

    // every horizon code
    @ParameterizedTest(name = "horizon {0}: {1} days")
    @MethodSource("horizons")
    void qIsTheTextPlusWhenAndTimespanDaysForEveryHorizon(HorizonCode horizon, int days) {
        assertThat(ParallelSearchSupport.timespanDays(horizon)).isEqualTo(days);
        assertThat(ParallelSearchSupport.q("solar flare", horizon)).isEqualTo("solar flare when:" + days + "d");
    }

    @Test
    void qNeverAddsQuotesParenthesesOrOr() {
        for (HorizonCode h : HorizonCode.values()) {
            assertThat(ParallelSearchSupport.q("mRNA vaccine approval", h))
                .doesNotContain("\"").doesNotContain("(").doesNotContain(")").doesNotContain(" OR ");
        }
    }
}
