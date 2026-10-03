package com.oracul.app.result;

import static com.oracul.app.result.StoryFixtures.HEADLINE;
import static com.oracul.app.result.StoryFixtures.body;
import static com.oracul.app.result.StoryFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.FutureStory;
import com.oracul.app.result.StoryHarness.Parsed;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** future-result.md "Slice 09_future-story" FR-23: StoryParser rows P1-P11 against window W (2026-10-02 .. 2031-10-02). */
// @trace FR-23
class StoryParserTest {

    private static final LocalDate CUTOFF = LocalDate.parse("2026-10-02");
    private static final LocalDate END = LocalDate.parse("2031-10-02");
    private static final String D = "2027-03-01";

    private static Parsed parse(String text) {
        return StoryHarness.parse(Optional.of(text), CUTOFF, END);
    }

    private static void assertErrors(Parsed p, boolean onlyDate, String... errors) {
        assertThat(p.errors()).containsExactly(errors);
        assertThat(p.story()).as("no story with errors").isEmpty();
        assertThat(p.onlyDateErrors()).isEqualTo(onlyDate);
    }

    private static FutureStory valid(Parsed p) {
        assertThat(p.errors()).isEmpty();
        assertThat(p.onlyDateErrors()).isFalse();
        return p.story().orElseThrow();
    }

    // P1
    @Test
    void aValidStoryIsNormalizedAndItsDatelineRebuilt() {
        FutureStory s = valid(parse(StoryFixtures.stDefault(D)));
        assertThat(s.getHeadline()).isEqualTo(HEADLINE);
        assertThat(s.getDateline()).isEqualTo("ORACUL FUTURE — March 1, 2027");
        assertThat(s.getFutureDate()).hasToString(D);
        assertThat(s.getBody()).isEqualTo(body(3));
    }

    // P2
    @ParameterizedTest
    @ValueSource(strings = {"2026-10-03", "2031-10-02"})
    void windowBoundsAreValid(String d) {
        assertThat(valid(parse(json(HEADLINE, d, body(3)))).getFutureDate()).hasToString(d);
    }

    // P3
    @Test
    void cutoffDayIsTooEarly() {
        assertErrors(parse(json(HEADLINE, "2026-10-02", body(3))), true,
            "futureDate 2026-10-02 must be after 2026-10-02 and no later than 2031-10-02");
    }

    @Test
    void dayAfterWindowEndIsTooLate() {
        assertErrors(parse(json(HEADLINE, "2031-10-03", body(3))), true,
            "futureDate 2031-10-03 must be after 2026-10-02 and no later than 2031-10-02");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2027-13-01", "soon"})
    void notADateIsInvalid(String d) {
        assertErrors(parse(json(HEADLINE, d, body(3))), true, "futureDate is not a valid date");
    }

    // P4
    @Test
    void aBodyOf149WordsIsTooShort() {
        assertErrors(parse(json(HEADLINE, D, StoryFixtures.body149())), false, "body must have 150 to 900 words (found 149)");
    }

    @Test
    void aBodyOf901WordsIsTooLong() {
        assertErrors(parse(json(HEADLINE, D, StoryFixtures.body901())), false, "body must have 150 to 900 words (found 901)");
    }

    // P5
    @Test
    void nineHundredWordsAndA160CharacterHeadlineAreValid() {
        valid(parse(json(HEADLINE, D, body(15))));
        valid(parse(json("H".repeat(160), D, body(3))));
    }

    // P6
    @Test
    void aHeadlineOf161CharactersIsTooLong() {
        assertErrors(parse(json("H".repeat(161), D, body(3))), false, "headline must be at most 160 characters (found 161)");
    }

    @Test
    void aBlankHeadlineIsRejected() {
        assertErrors(parse(json("   ", D, body(3))), false, "headline must not be blank");
    }

    // P7
    @Test
    void htmlInTheBodyIsNotPlainText() {
        assertErrors(parse(json(HEADLINE, D, body(3) + "\n\n<p>Breaking</p>")), false, "body must be plain text");
    }

    @Test
    void markdownInTheBodyIsNotPlainText() {
        assertErrors(parse(json(HEADLINE, D, body(3) + "\n\n## Breaking")), false, "body must be plain text");
    }

    @ParameterizedTest
    @ValueSource(strings = {"bold **x** text", "under __x__ text", "code `x` text"})
    void markdownMarkersInTheBodyAreNotPlainText(String fragment) {
        assertErrors(parse(json(HEADLINE, D, body(3) + "\n\n" + fragment)), false, "body must be plain text");
    }

    // P8
    @Test
    void theHeadlineIsTrimmedAndWhitespaceCollapsed() {
        assertThat(valid(parse(json("  Robots\n take   ports ", D, body(3)))).getHeadline()).isEqualTo("Robots take ports");
    }

    // P9
    @Test
    void theBodyIsNormalized() {
        String messy = StoryFixtures.paragraph(1) + "  \r\n\r\n\r\n" + StoryFixtures.paragraph(2) + "   \r\n\r\n\r\n\r\n"
            + StoryFixtures.paragraph(3) + "  ";
        assertThat(valid(parse(json(HEADLINE, D, messy))).getBody()).isEqualTo(body(3));
    }

    // P10
    @Test
    void allRulesAreCheckedInOrderHeadlineBodyDate() {
        Parsed p = parse(json("H".repeat(161), "2026-10-02", StoryFixtures.body149()));
        assertErrors(p, false,
            "headline must be at most 160 characters (found 161)",
            "body must have 150 to 900 words (found 149)",
            "futureDate 2026-10-02 must be after 2026-10-02 and no later than 2031-10-02");
    }

    // P11
    @Test
    void anEmptyOutputIsNoOutputText() {
        assertErrors(parse(""), false, "no output text");
        assertErrors(StoryHarness.parse(Optional.empty(), CUTOFF, END), false, "no output text");
    }

    @Test
    void textThatIsNotJsonIsRejected() {
        assertErrors(parse("not json"), false, "output is not valid JSON");
    }

    @Test
    void anUnknownKeyIsRejected() {
        assertErrors(parse(StoryFixtures.stDefault(D).replaceFirst("\\}$", ",\"tools\":[]}")), false, "unknown field tools");
    }

    @Test
    void aMissingKeyIsRejected() {
        assertErrors(parse("{\"headline\":\"x\",\"dateline\":\"d\",\"futureDate\":\"" + D + "\"}"), false, "missing field body");
    }

    @Test
    void aWrongTypeIsRejected() {
        assertErrors(parse("{\"headline\":1,\"dateline\":\"d\",\"futureDate\":\"" + D + "\",\"body\":\"b\"}"), false,
            "headline has the wrong type");
    }

    @Test
    void trailingGarbageIsNotValidJson() {
        assertErrors(parse(StoryFixtures.stDefault(D) + " x"), false, "output is not valid JSON");
    }
}
