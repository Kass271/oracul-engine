package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * QueryRules (pure, static) - rule Q of wildcard-search.md FR-51: 3-12 words, at most 120 characters, none of
 * {@code " “ ” ( )}, no {@code :}, no standalone token OR in any letter case; {@code clean} trims and collapses whitespace.
 */
// @trace FR-51
class QueryRulesTest {

    private static String words(int n) {
        return String.join(" ", IntStream.rangeClosed(1, n).mapToObj(i -> "w" + i).toList());
    }

    /** Three words of the given total length (two spaces included). */
    private static String ofLength(int length) {
        int rest = length - 2;
        int a = rest / 3;
        int b = rest / 3;
        int c = rest - a - b;
        return "a".repeat(a) + " " + "b".repeat(b) + " " + "c".repeat(c);
    }

    static Stream<Arguments> classes() {
        return Stream.of(
            Arguments.of("pandemic vaccine", false, "2 words"),
            Arguments.of("pandemic vaccine approval", true, "3 words"),
            Arguments.of(words(12), true, "12 words"),
            Arguments.of(words(13), false, "13 words"),
            Arguments.of(ofLength(120), true, "120 characters"),
            Arguments.of(ofLength(121), false, "121 characters"),
            Arguments.of("\"mRNA vaccine\" approval news", false, "straight double quotes"),
            Arguments.of("“mRNA vaccine” approval news", false, "curly double quotes"),
            Arguments.of("mRNA vaccine approval” news", false, "a closing curly quote"),
            Arguments.of("(fusion OR fission) news today", false, "parentheses and OR"),
            Arguments.of("fusion (plants) news today", false, "parentheses"),
            Arguments.of("fusion plants) news today", false, "a closing parenthesis"),
            Arguments.of("fusion OR fission plants", false, "token OR"),
            Arguments.of("fusion or fission plants", false, "token or"),
            Arguments.of("fusion Or fission plants", false, "token Or"),
            Arguments.of("ORganic farming growth trends", true, "OR inside a longer token"),
            Arguments.of("Oregon wildfire season news", true, "or inside longer tokens"),
            Arguments.of("vaccine news when:7d", false, "when: filter"),
            Arguments.of("site:example.org vaccine news", false, "site: filter"),
            Arguments.of("vaccine ratio 3:1 news", false, "a colon anywhere"),
            Arguments.of("  alpha beta gamma  ", true, "valid after trimming"),
            Arguments.of("alpha     beta\tgamma\ndelta", true, "mixed whitespace between words"),
            Arguments.of("", false, "empty"),
            Arguments.of("   ", false, "blank"),
            Arguments.of(null, false, "null"));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("classes")
    void ruleQClasses(String text, boolean expected, String name) {
        assertThat(QuerySupport.valid(text)).as(name + ": '" + text + "'").isEqualTo(expected);
    }

    static Stream<Arguments> everyWordCount() {
        return IntStream.rangeClosed(0, 15).mapToObj(n -> Arguments.of(n));
    }

    // exhaustive over the word counts 0 ... 15: valid exactly for 3 ... 12
    @ParameterizedTest(name = "{0} words")
    @MethodSource("everyWordCount")
    void onlyThreeToTwelveWordsPass(int n) {
        assertThat(QuerySupport.valid(words(n))).as(n + " words").isEqualTo(n >= 3 && n <= 12);
    }

    static Stream<Arguments> everyLength() {
        return IntStream.rangeClosed(105, 135).mapToObj(n -> Arguments.of(n));
    }

    // exhaustive around the 120-character boundary: valid exactly up to 120
    @ParameterizedTest(name = "{0} characters")
    @MethodSource("everyLength")
    void onlyUpTo120CharactersPass(int length) {
        String text = ofLength(length);
        assertThat(text).hasSize(length);
        assertThat(QuerySupport.valid(text)).as(length + " characters").isEqualTo(length <= 120);
    }

    @Test
    void cleanTrimsAndCollapsesWhitespaceAndKeepsNull() {
        assertThat(QuerySupport.clean(null)).isNull();
        assertThat(QuerySupport.clean("  a   b  c ")).isEqualTo("a b c");
        assertThat(QuerySupport.clean("a\tb\n c")).isEqualTo("a b c");
        assertThat(QuerySupport.clean("")).isEmpty();
        assertThat(QuerySupport.clean("   ")).isEmpty();
        assertThat(QuerySupport.clean("already clean")).isEqualTo("already clean");
    }
}
