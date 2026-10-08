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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** future-result.md "Slice 09_future-story" FR-23: StoryParser rows P1-P11 against window W (2026-10-02 .. 2031-10-02). */
// @trace FR-23, FR-66, FR-65
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

    // ---- phase 04 styled-story.md FR-66: the word count is language-independent ---------------------------------

    // @trace FR-66
    @ParameterizedTest(name = "Ukrainian body of {0} words")
    @org.junit.jupiter.params.provider.CsvSource({"149, false", "150, true", "151, true", "899, true", "900, true", "901, false"})
    void aUkrainianBodyIsCountedLikeAnEnglishOneAndHasTheSameBoundaries(int words, boolean ok) {
        String ukBody = StoryFixtures.ubodyOfWords(words);
        assertThat(ukBody.trim().split("\\s+")).as("fixture word count").hasSize(words);
        Parsed p = parse(json(StoryFixtures.UK_HEADLINE, D, ukBody));
        if (ok) {
            FutureStory s = valid(p);
            assertThat(s.getBody()).isEqualTo(ukBody);
            assertThat(s.getHeadline()).isEqualTo(StoryFixtures.UK_HEADLINE);
            assertThat(s.getDateline()).as("the parser still returns the English dateline, StoryWriter replaces it")
                .isEqualTo("ORACUL FUTURE — March 1, 2027");
        } else {
            assertErrors(p, false, "body must have 150 to 900 words (found " + words + ")");
        }
    }

    // @trace FR-66
    @Test
    void theStubUkrainianBodyOfThreeParagraphsHas180WordsAndIsValid() {
        FutureStory s = valid(parse(json(StoryFixtures.UK_HEADLINE + " — Narrative — inspired by Franz Kafka", D, StoryFixtures.ubody(3))));
        assertThat(s.getBody()).isEqualTo(StoryFixtures.ubody(3));
        assertThat(StoryFixtures.ubody(3).split("\\s+")).hasSize(180);
    }

    // ---- phase 04 styled-story.md FR-65 post-release correction 2026-10-08: analysis labels and Evidence-ID citations ----

    private static final String HEADLINE_LABEL = "headline must not start with FACT, INFERENCE, SPECULATION or FUTURE EVENT";
    private static final String HEADLINE_CITATION = "headline must not cite Evidence IDs";
    private static final String BODY_LABEL = "body must not label paragraphs FACT, INFERENCE, SPECULATION or FUTURE EVENT";
    private static final String BODY_CITATION = "body must not cite Evidence IDs";

    /** The label prefixes of the spec's "invalid" classes. */
    static final List<String> INVALID_LABELS = List.of("FACT — ", "FACT: ", "INFERENCE — ", "INFERENCE:", "SPECULATION — ",
        "SPECULATION: ", "FUTURE EVENT — ", "FUTURE EVENT: ", "  FACT — ");
    /** The look-alikes the spec lists as valid. */
    static final List<String> VALID_LABEL_LOOKALIKES = List.of("Fact: ", "Факт: ", "Facts — ", "FACTS — ", "ФАКТИ — ", "FACTORY: ",
        "FUTURE EVENTS — ", "FUTURE-EVENT — ", "FACT - ", "FACT-", "FACT. ", "FACT is ", "The FACT — ", "SITUATION. ", "OUTLOOK: ",
        "СИТУАЦІЯ. ", "ПЕРСПЕКТИВА. ", "FACT -", "fact — ", "Fact — ", "a fact: ", "Future event: ");
    static final List<String> INVALID_CITATIONS = List.of("[E001]", "[E1]", "[E001, E004]", "[E12345]");
    static final List<String> VALID_CITATIONS = List.of("E001", "(E001)", "[Europe]", "[e001]", "[F1]", "E001]", "[ E001]");

    /** Paragraph k of BODY(3) prefixed by {@code prefix}; k = 0 prefixes all three. */
    private static String bodyWithPrefix(String prefix, int k) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 3; i++) {
            if (i > 1) sb.append("\n\n");
            sb.append(k == 0 || k == i ? prefix : "").append(StoryFixtures.paragraph(i));
        }
        return sb.toString();
    }

    /** BODY(3) with {@code citation} inserted in the middle of paragraph k; k = 0 puts one into each paragraph. */
    private static String bodyWithCitation(String citation, int k) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 3; i++) {
            if (i > 1) sb.append("\n\n");
            String para = StoryFixtures.paragraph(i);
            sb.append(k == 0 || k == i ? para.replaceFirst("lorem", citation + " lorem") : para);
        }
        return sb.toString();
    }

    static Stream<Arguments> invalidLabelsByParagraph() {
        List<Arguments> out = new ArrayList<>();
        for (String label : INVALID_LABELS) {
            for (int k : new int[] {1, 3, 0}) out.add(Arguments.of(label, k));
        }
        return out.stream();
    }

    static Stream<Arguments> invalidCitationsByParagraph() {
        List<Arguments> out = new ArrayList<>();
        for (String c : INVALID_CITATIONS) {
            for (int k : new int[] {1, 2, 3, 0}) out.add(Arguments.of(c, k));
        }
        return out.stream();
    }

    // @trace FR-65
    @ParameterizedTest(name = "body paragraph {1} starts with \"{0}\"")
    @MethodSource("invalidLabelsByParagraph")
    void aBodyLineThatStartsWithAnAnalysisLabelGivesExactlyOneBodyLabelError(String label, int paragraph) {
        assertErrors(parse(json(HEADLINE, D, bodyWithPrefix(label, paragraph))), false, BODY_LABEL);
    }

    // @trace FR-65
    @ParameterizedTest(name = "valid body look-alike \"{0}\"")
    @MethodSource("validLabelLookalikes")
    void aBodyLineThatOnlyLooksLikeALabelIsAccepted(String prefix) {
        valid(parse(json(HEADLINE, D, bodyWithPrefix(prefix, 1))));
        valid(parse(json(HEADLINE, D, bodyWithPrefix(prefix, 3))));
    }

    static Stream<String> validLabelLookalikes() {
        return VALID_LABEL_LOOKALIKES.stream();
    }

    // @trace FR-65
    @ParameterizedTest(name = "body citation {0} in paragraph {1}")
    @MethodSource("invalidCitationsByParagraph")
    void anEvidenceIdCitationAnywhereInABodyLineGivesExactlyOneBodyCitationError(String citation, int paragraph) {
        assertErrors(parse(json(HEADLINE, D, bodyWithCitation(citation, paragraph))), false, BODY_CITATION);
    }

    // @trace FR-65
    @ParameterizedTest(name = "valid body without citation \"{0}\"")
    @MethodSource("validCitations")
    void aBodyWithoutABracketedEvidenceIdIsAccepted(String text) {
        valid(parse(json(HEADLINE, D, bodyWithCitation(text, 2))));
    }

    static Stream<String> validCitations() {
        return VALID_CITATIONS.stream();
    }

    // @trace FR-65
    @ParameterizedTest(name = "headline starts with \"{0}\"")
    @MethodSource("headlineLabels")
    void aHeadlineThatStartsWithAnAnalysisLabelGivesTheHeadlineLabelError(String label) {
        assertErrors(parse(json(label + HEADLINE, D, body(3))), false, HEADLINE_LABEL);
    }

    static Stream<String> headlineLabels() {
        return INVALID_LABELS.stream();
    }

    // @trace FR-65
    @ParameterizedTest(name = "valid headline look-alike \"{0}\"")
    @MethodSource("validLabelLookalikes")
    void aHeadlineThatOnlyLooksLikeALabelIsAccepted(String prefix) {
        assertThat(valid(parse(json(prefix + HEADLINE, D, body(3)))).getHeadline()).isEqualTo((prefix + HEADLINE).trim());
    }

    // @trace FR-65
    @ParameterizedTest(name = "headline citation {0}")
    @MethodSource("headlineCitations")
    void anEvidenceIdCitationInTheHeadlineGivesTheHeadlineCitationError(String citation) {
        assertErrors(parse(json("Stub " + citation + " headline", D, body(3))), false, HEADLINE_CITATION);
        assertErrors(parse(json(HEADLINE + " " + citation, D, body(3))), false, HEADLINE_CITATION);
    }

    static Stream<String> headlineCitations() {
        return INVALID_CITATIONS.stream();
    }

    // @trace FR-65
    @ParameterizedTest(name = "valid headline text \"{0}\"")
    @MethodSource("validCitations")
    void aHeadlineWithoutABracketedEvidenceIdIsAccepted(String text) {
        assertThat(valid(parse(json("Stub " + text + " headline", D, body(3)))).getHeadline()).isEqualTo("Stub " + text + " headline");
    }

    // @trace FR-65
    @Test
    void headlineAndBodyAreCheckedSeparately() {
        // a label in the body does not trigger the headline errors and vice versa
        assertErrors(parse(json("FACT — " + HEADLINE, D, bodyWithPrefix("FACT — ", 1))), false, HEADLINE_LABEL, BODY_LABEL);
        assertErrors(parse(json(HEADLINE + " [E001]", D, bodyWithCitation("[E002]", 2))), false, HEADLINE_CITATION, BODY_CITATION);
    }

    // @trace FR-65
    @Test
    void aLabelAndACitationInTheBodyGiveBothErrorsInTableOrder() {
        String body = StoryFixtures.paragraph(1) + " [E001]\n\nFACT — " + StoryFixtures.paragraph(2) + "\n\n" + StoryFixtures.paragraph(3);
        assertErrors(parse(json(HEADLINE, D, body)), false, BODY_LABEL, BODY_CITATION);
    }

    // @trace FR-65
    @Test
    void allFourErrorsComeInTableOrderAfterTheLengthAndPlainTextErrors() {
        String headline = "FACT — [E001] " + "H".repeat(150);
        String body = "FACT: " + StoryFixtures.paragraph(1) + " [E001]\n\n" + StoryFixtures.paragraph(2) + "\n\n" + StoryFixtures.paragraph(3)
            + "\n\nbold **x** text";
        Parsed p = parse(json(headline, D, body));
        assertThat(p.errors()).containsExactly("headline must be at most 160 characters (found 164)", "body must be plain text",
            HEADLINE_LABEL, HEADLINE_CITATION, BODY_LABEL, BODY_CITATION);
        assertThat(p.story()).isEmpty();
        assertThat(p.onlyDateErrors()).isFalse();
    }

    // @trace FR-65
    @ParameterizedTest(name = "label with a date error: futureDate {0}")
    @ValueSource(strings = {"2026-10-02", "2031-10-03", "2027-13-01"})
    void aLabelErrorTogetherWithADateErrorIsNotADateOnlyFailure(String futureDate) {
        Parsed p = parse(json(HEADLINE, futureDate, bodyWithPrefix("FACT — ", 1)));
        assertThat(p.errors()).contains(BODY_LABEL).hasSize(2);
        assertThat(p.errors().stream().filter(e -> e.startsWith("futureDate"))).hasSize(1);
        assertThat(p.story()).isEmpty();
        assertThat(p.onlyDateErrors()).isFalse();
    }

    // @trace FR-65
    @Test
    void aCitationErrorTogetherWithADateErrorIsNotADateOnlyFailure() {
        Parsed p = parse(json(HEADLINE, "2026-10-02", bodyWithCitation("[E001]", 1)));
        assertThat(p.errors()).contains(BODY_CITATION).hasSize(2);
        assertThat(p.onlyDateErrors()).isFalse();
    }

    // @trace FR-65
    @Test
    void theNamedStoryFixturesTriggerExactlyTheirErrors() {
        assertErrors(parse(StoryFixtures.fixture("ST-FACT", "Future event date: " + D)), false, BODY_LABEL);
        assertErrors(parse(StoryFixtures.fixture("ST-FACT-COLON", "Future event date: " + D)), false, BODY_LABEL);
        assertErrors(parse(StoryFixtures.fixture("ST-CITE", "Future event date: " + D)), false, BODY_CITATION);
    }

    // ---- FR-65 review R1: the full LABEL cross product (spec "Ranges & invariants") -------------------------------

    private static final String NBSP = "\u00A0";
    static final List<String> LABEL_WORDS = List.of("FACT", "INFERENCE", "SPECULATION", "FUTURE EVENT", "FUTURE_EVENT", "ФАКТ",
        "ВИСНОВОК", "ПРИПУЩЕННЯ", "МАЙБУТНЯ ПОДІЯ");
    static final List<String> LABEL_SEPARATORS = List.of("\u2014", "\u2013", ":");
    static final List<String> LABEL_INNERS = List.of("", " ", NBSP, "  ");
    static final List<String> LABEL_LEADS = List.of("", "  ", NBSP, " " + NBSP);
    static final List<String> LABEL_POSITIONS = List.of("headline", "body paragraph 1", "body paragraph 3");

    private static String visible(String s) {
        return s.replace(NBSP, "<nbsp>").replace(" ", "_");
    }

    static Stream<Arguments> labelCrossProduct() {
        List<Arguments> out = new ArrayList<>();
        for (String word : LABEL_WORDS) {
            for (String sep : LABEL_SEPARATORS) {
                for (String inner : LABEL_INNERS) {
                    for (String lead : LABEL_LEADS) {
                        for (String position : LABEL_POSITIONS) {
                            out.add(Arguments.of(lead, word, inner, sep, position,
                                visible(lead) + "|" + word + "|" + visible(inner) + "|" + sep));
                        }
                    }
                }
            }
        }
        return out.stream();
    }

    // @trace FR-65
    @Test
    void theLabelCrossProductHas1296Cases() {
        assertThat(labelCrossProduct().count()).isEqualTo(9L * 3 * 4 * 4 * 3);
    }

    // @trace FR-65
    @ParameterizedTest(name = "[{index}] {4}: lead|word|inner|sep = {5}")
    @MethodSource("labelCrossProduct")
    void everyLabelFormGivesExactlyTheOneErrorOfItsPosition(String lead, String word, String inner, String sep, String position,
        String shown) {
        String prefix = lead + word + inner + sep + " ";
        switch (position) {
            case "headline" -> assertErrors(parse(json(prefix + HEADLINE, D, body(3))), false, HEADLINE_LABEL);
            case "body paragraph 1" -> assertErrors(parse(json(HEADLINE, D, bodyWithPrefix(prefix, 1))), false, BODY_LABEL);
            case "body paragraph 3" -> assertErrors(parse(json(HEADLINE, D, bodyWithPrefix(prefix, 3))), false, BODY_LABEL);
            default -> throw new IllegalStateException(position + " / " + visible(prefix));
        }
    }

    static Stream<Arguments> allThreeParagraphsLabelled() {
        List<Arguments> out = new ArrayList<>();
        for (String word : LABEL_WORDS) {
            for (String sep : LABEL_SEPARATORS) {
                out.add(Arguments.of(word, sep));
            }
        }
        return out.stream();
    }

    // @trace FR-65
    @ParameterizedTest(name = "all 3 paragraphs start with {0}{1}")
    @MethodSource("allThreeParagraphsLabelled")
    void labellingAllThreeParagraphsStillGivesExactlyOneBodyLabelError(String word, String sep) {
        assertErrors(parse(json(HEADLINE, D, bodyWithPrefix(word + sep + " ", 0))), false, BODY_LABEL);
    }

    // @trace FR-65
    @ParameterizedTest(name = "all 3 paragraphs start with NBSP + {0} + space + {1}")
    @MethodSource("allThreeParagraphsLabelled")
    void labellingAllThreeParagraphsWithNoBreakSpacesStillGivesExactlyOneBodyLabelError(String word, String sep) {
        assertErrors(parse(json(HEADLINE, D, bodyWithPrefix(NBSP + word + " " + sep + " ", 0))), false, BODY_LABEL);
    }
}
