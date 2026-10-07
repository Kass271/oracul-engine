package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.QueryExpansionMode;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.WildcardPipeline;
import com.oracul.app.api.model.WildcardPipelineKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * QueryTemplates (pure) - wildcard-search.md FR-51 step 6 and its ranges: the label L, the band x direction vocabulary and the
 * N / P term rules, every template obeying rule Q.
 */
// @trace FR-51
class QueryTemplatesTest {

    static final Set<String> N = Set.of("risk", "risks", "failure", "failures", "crisis", "crises", "conflict", "conflicts",
        "disaster", "disasters", "threat", "threats", "warning", "warnings", "collapse", "catastrophe", "catastrophic");
    static final Set<String> P = Set.of("progress", "breakthrough", "innovation", "research");

    private static ScenarioConfiguration cfg(int darkness, int optimism) {
        return PlanSupport.cfg(8, darkness, optimism, HorizonCode._5Y, List.of(), List.of());
    }

    private static WildcardPipeline catalogue(String label, int level) {
        return new WildcardPipeline("W01", WildcardPipelineKind.CATALOGUE, label, label + " " + level + "/10",
            QueryExpansionMode.TEMPLATE_FALLBACK, new ArrayList<>()).level(level).topicKey("biology-new-pandemic");
    }

    private static WildcardPipeline general() {
        return new WildcardPipeline("W01", WildcardPipelineKind.GENERAL, "General", "General",
            QueryExpansionMode.TEMPLATE_FALLBACK, new ArrayList<>());
    }

    private static List<String> templates(WildcardPipeline p, int darkness, int optimism) {
        return PlanSupport.templates(p, cfg(darkness, optimism));
    }

    /** The words a template adds to the label: the text without the leading "<L> ", or the whole text when it does not start with L. */
    private static List<String> added(String text, String l) {
        String rest = text.startsWith(l + " ") ? text.substring(l.length() + 1) : text;
        return List.of(rest.toLowerCase(Locale.ROOT).split("\\s+"));
    }

    private static long count(List<String> words, Set<String> vocabulary) {
        return words.stream().filter(vocabulary::contains).count();
    }

    // ---- the label L (QueryTemplates.label) ---------------------------------------------------------------------

    static Stream<Arguments> labels() {
        return Stream.of(
            Arguments.of("Energy crisis", "Energy crisis"),
            Arguments.of("\"Ocean\" (desalination) boom", "Ocean desalination boom"),
            Arguments.of("OR", "future developments"),
            Arguments.of("()", "future developments"),
            Arguments.of("abcd def ghi jkl mno pqr stu vwx yza bcd", "abcd def ghi jkl mno pqr"),
            Arguments.of("war or peace", "war peace"),
            Arguments.of("Fusion OR fission", "Fusion fission"),
            Arguments.of("Fusion Or fission", "Fusion fission"),
            Arguments.of("A - B", "A B"),
            Arguments.of("Earth's last ice", "Earth's last ice"),
            Arguments.of("ocean: boom!", "ocean boom"),
            Arguments.of("Rock AND roll", "Rock roll"),
            Arguments.of("NOT again", "again"),
            Arguments.of("  spaced   out   label ", "spaced out label"),
            Arguments.of("", "future developments"),
            Arguments.of("   ", "future developments"),
            Arguments.of("-' - '", "future developments"),
            Arguments.of("one two three four five six seven eight", "one two three four five six"));
    }

    @ParameterizedTest(name = "label \"{0}\" -> \"{1}\"")
    @MethodSource("labels")
    void theLabelForQueriesIsCleanedAndCapped(String raw, String expected) {
        assertThat(QuerySupport.label(raw)).isEqualTo(expected);
    }

    // ---- exact templates (spec step 6 / ranges b) ---------------------------------------------------------------

    @Test
    void exactTemplatesOfTheAcceptanceWildcards() {
        assertThat(templates(catalogue("New pandemic", 8), 9, 2)).containsExactly(
            "New pandemic extreme scenario disaster", "New pandemic unprecedented scale catastrophe",
            "New pandemic radical upheaval collapse");
        assertThat(templates(catalogue("Humanoid robot boom", 6), 9, 2)).containsExactly(
            "Humanoid robot boom serious disruption crisis", "Humanoid robot boom major escalation conflict",
            "Humanoid robot boom growing concerns threat");
        assertThat(templates(catalogue("New pandemic", 1), 2, 5)).containsExactly(
            "New pandemic latest research progress", "New pandemic new developments breakthrough",
            "New pandemic early studies innovation");
    }

    @Test
    void exactTemplatesOfTheBandsWithoutADirectionWord() {
        // Darkness 5 / Optimism 5: neither dark nor bright -> no direction word
        assertThat(templates(catalogue("New pandemic", 2), 5, 5)).containsExactly(
            "New pandemic latest research", "New pandemic new developments", "New pandemic early studies");
        assertThat(templates(catalogue("New pandemic", 5), 5, 5)).containsExactly(
            "New pandemic serious disruption", "New pandemic major escalation", "New pandemic growing concerns");
        assertThat(templates(catalogue("New pandemic", 9), 5, 5)).containsExactly(
            "New pandemic extreme scenario", "New pandemic unprecedented scale", "New pandemic radical upheaval");
    }

    @Test
    void exactTemplatesOfTheGeneralPipeline() {
        assertThat(templates(general(), 5, 5)).containsExactly("major world events today",
            "global economy politics developments", "international science technology developments");
        assertThat(templates(general(), 9, 2)).containsExactly("major world events today disaster",
            "global economy politics developments catastrophe", "international science technology developments collapse");
        assertThat(templates(general(), 5, 8)).containsExactly("major world events today progress",
            "global economy politics developments breakthrough", "international science technology developments innovation");
    }

    @Test
    void optimismAddsTheProgressWordsWhenDarknessIsNeitherHighNorLow() {
        // Darkness 5 / Optimism 8: each template gets progress / breakthrough / innovation
        assertThat(templates(catalogue("New pandemic", 5), 5, 8)).containsExactly(
            "New pandemic serious disruption progress", "New pandemic major escalation breakthrough",
            "New pandemic growing concerns innovation");
    }

    @Test
    void darknessWinsOverOptimism() {
        // Darkness 7 and Optimism 8 -> the negative direction words
        assertThat(templates(catalogue("New pandemic", 5), 7, 8)).containsExactly(
            "New pandemic serious disruption crisis", "New pandemic major escalation conflict",
            "New pandemic growing concerns threat");
    }

    // ---- ranges: bands x Darkness x Optimism over several labels --------------------------------------------------

    static Stream<Arguments> bandDarknessOptimism() {
        List<Arguments> out = new ArrayList<>();
        for (String label : List.of("New pandemic", "Mars", "Energy crisis", "Humanoid robot boom")) {
            for (int level : new int[] {1, 3, 4, 7, 8, 10}) {
                for (int darkness : new int[] {2, 3, 5, 7, 9}) {
                    for (int optimism : new int[] {5, 8}) {
                        out.add(Arguments.of(label, level, darkness, optimism));
                    }
                }
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} level {1} darkness {2} optimism {3}")
    @MethodSource("bandDarknessOptimism")
    void everyTemplateObeysRuleQAndTheDirectionRules(String label, int level, int darkness, int optimism) {
        List<String> list = templates(catalogue(label, level), darkness, optimism);
        assertThat(list).as("forPipeline always returns exactly 3 texts").hasSize(3);
        assertThat(new HashSet<>(list.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList())).as("3 distinct texts").hasSize(3);
        String l = QuerySupport.label(label);
        for (String t : list) {
            assertThat(RuleQOracle.holds(t)).as(RuleQOracle.why(t)).isTrue();
            assertThat(t).as("starts with the label").startsWith(l + " ");
            List<String> words = added(t, l);
            if (darkness >= 7) {
                assertThat(count(words, N)).as("Darkness >= 7: exactly one N term added by '" + t + "'").isEqualTo(1);
            } else if (darkness <= 3) {
                assertThat(count(words, N)).as("Darkness <= 3: no N term added by '" + t + "'").isZero();
                assertThat(count(words, P)).as("Darkness <= 3: a P term added by '" + t + "'").isGreaterThanOrEqualTo(1);
            } else if (optimism >= 7) {
                assertThat(count(words, N)).as("Optimism >= 7: no N term added by '" + t + "'").isZero();
                assertThat(count(words, P)).as("Optimism >= 7: a P term added by '" + t + "'").isGreaterThanOrEqualTo(1);
            } else {
                assertThat(count(words, N)).as("Darkness 5 / Optimism 5: no N term added by '" + t + "'").isZero();
            }
        }
    }

    @ParameterizedTest(name = "{0} darkness {1} optimism {2}")
    @MethodSource("labelDarknessOptimism")
    void theThreeBandsHaveDisjointTemplateSetsAndAreUniformInsideABand(String label, int darkness, int optimism) {
        List<String> low = templates(catalogue(label, 1), darkness, optimism);
        List<String> mid = templates(catalogue(label, 4), darkness, optimism);
        List<String> high = templates(catalogue(label, 8), darkness, optimism);
        assertThat(low).doesNotContainAnyElementsOf(mid).doesNotContainAnyElementsOf(high);
        assertThat(mid).doesNotContainAnyElementsOf(high);
        for (int level = 1; level <= 10; level++) {
            List<String> expected = level <= 3 ? low : level <= 7 ? mid : high;
            assertThat(templates(catalogue(label, level), darkness, optimism)).as("level " + level).isEqualTo(expected);
        }
    }

    static Stream<Arguments> labelDarknessOptimism() {
        List<Arguments> out = new ArrayList<>();
        for (String label : List.of("New pandemic", "Mars", "Energy crisis")) {
            for (int darkness : new int[] {2, 3, 5, 7, 9}) {
                for (int optimism : new int[] {5, 8}) {
                    out.add(Arguments.of(label, darkness, optimism));
                }
            }
        }
        return out.stream();
    }

    @Test
    void wordsOfTheLabelItselfNeverCountAsNegativeTerms() {
        // "Energy crisis" contains the N term "crisis"; the template adds none of its own at Darkness 2
        for (String t : templates(catalogue("Energy crisis", 5), 2, 5)) {
            assertThat(count(added(t, "Energy crisis"), N)).as(t).isZero();
            assertThat(count(added(t, "Energy crisis"), P)).as(t).isGreaterThanOrEqualTo(1);
        }
    }

    @ParameterizedTest(name = "GENERAL darkness {0} optimism {1}")
    @CsvSource({"2,5", "3,5", "5,5", "7,5", "9,5", "2,8", "5,8", "7,8", "9,8"})
    void theGeneralPipelineObeysRuleQAndTheDirectionRules(int darkness, int optimism) {
        List<String> list = templates(general(), darkness, optimism);
        assertThat(list).hasSize(3);
        for (String t : list) {
            assertThat(RuleQOracle.holds(t)).as(RuleQOracle.why(t)).isTrue();
            List<String> words = added(t, "major world events");
            if (darkness >= 7) {
                assertThat(count(words, N)).as(t).isEqualTo(1);
            } else if (darkness <= 3 || optimism >= 7) {
                assertThat(count(words, N)).as(t).isZero();
                assertThat(count(words, P)).as(t).isGreaterThanOrEqualTo(1);
            } else {
                assertThat(count(words, N)).as(t).isZero();
            }
        }
    }

    static Stream<Arguments> hostileLabels() {
        return Stream.of(
            Arguments.of("\"Ocean\" (desalination) boom"), Arguments.of("OR"), Arguments.of("()"),
            Arguments.of("war or peace"), Arguments.of("a b c d e f g h i j"), Arguments.of("x"), Arguments.of("when:7d site:example.org"),
            Arguments.of("Fusion OR fission"), Arguments.of("AND NOT OR"), Arguments.of("abcd def ghi jkl mno pqr stu vwx yza bcd"));
    }

    @ParameterizedTest(name = "label \"{0}\"")
    @MethodSource("hostileLabels")
    void hostileLabelsStillGiveTemplatesThatObeyRuleQ(String label) {
        for (int level : new int[] {1, 5, 9}) {
            for (int darkness : new int[] {2, 5, 9}) {
                for (String t : templates(catalogue(label, level), darkness, 5)) {
                    assertThat(RuleQOracle.holds(t)).as("label '" + label + "' level " + level + " darkness " + darkness + ": "
                        + RuleQOracle.why(t)).isTrue();
                }
            }
        }
    }
}
