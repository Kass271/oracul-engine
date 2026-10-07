package com.oracul.app.reasoning;

import static com.oracul.app.reasoning.ReasoningHarness.parseCritic;
import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.reasoning.ReasoningHarness.Critiqued;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** scenario-reasoning.md "Slice 10_critic" FR-22 CriticParser rows C1-C7. */
// @trace FR-22, FR-58
class CriticParserTest {

    /** The six types the parser accepts from phase 03 on (wildcard-evidence.md FR-58), in the order of the error message. */
    private static final List<String> TYPE_LIST = List.of("UNSUPPORTED_FACTUAL_JUMP", "CONTRADICTION", "UNREALISTIC_TIMELINE",
        "WILDCARD_FORCING", "SETTINGS_MISMATCH", "INAPPROPRIATE_CERTAINTY");
    private static final String TYPES = String.join(", ", TYPE_LIST);

    private static void assertErrors(Critiqued c, String... errors) {
        assertThat(c.verdict()).as("no critique expected, got " + c).isEmpty();
        assertThat(c.errors()).containsExactly(errors);
    }

    private static Critiqued valid(Critiqued c) {
        assertThat(c.errors()).as("errors").isEmpty();
        assertThat(c.verdict()).as("critique").isPresent();
        return c;
    }

    private static String fail(String description) {
        return "{\"verdict\":\"FAIL\",\"issues\":[{\"type\":\"CONTRADICTION\",\"description\":" + ReasoningHarness.MAPPER.writeValueAsString(description) + "}]}";
    }

    // C1
    @Test
    void aPassWithoutIssuesIsValid() {
        Critiqued c = valid(parseCritic(CriticFixtures.CR_PASS));
        assertThat(c.verdict()).contains("PASS");
        assertThat(c.issues()).isEmpty();
    }

    // C2
    @Test
    void aFailKeepsItsIssuesInOrder() {
        Critiqued jump = valid(parseCritic(CriticFixtures.CR_JUMP));
        assertThat(jump.verdict()).contains("FAIL");
        assertThat(jump.issues()).containsExactly("UNSUPPORTED_FACTUAL_JUMP | " + CriticFixtures.JUMP_DESCRIPTION);
        Critiqued cert = valid(parseCritic(CriticFixtures.CR_CERT));
        assertThat(cert.issues()).containsExactly("INAPPROPRIATE_CERTAINTY | " + CriticFixtures.CERT_1,
            "UNREALISTIC_TIMELINE | " + CriticFixtures.CERT_2);
    }

    // C3 rows 1-7
    @Test
    void emptyOptionalAndBlankTextAreNoOutputText() {
        assertErrors(parseCritic(Optional.empty()), "no output text");
        assertErrors(parseCritic("   "), "no output text");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{\"verdict\":\"PASS\",\"issues\":[]} x", "[]"})
    void textThatIsNotAJsonObjectIsRejected(String text) {
        assertErrors(parseCritic(text), "output is not valid JSON");
    }

    @Test
    void unknownKeysAreRejected() {
        assertErrors(parseCritic("{\"verdict\":\"PASS\",\"issues\":[],\"tools\":[]}"), "unknown field tools");
        assertErrors(parseCritic("{\"verdict\":\"FAIL\",\"issues\":[{\"type\":\"CONTRADICTION\",\"description\":\"x\",\"severity\":\"high\"}]}"),
            "unknown field issues[0].severity");
    }

    @Test
    void missingOrNullKeysAreRejected() {
        assertErrors(parseCritic("{\"verdict\":\"PASS\"}"), "missing field issues");
        assertErrors(parseCritic("{\"verdict\":\"FAIL\",\"issues\":[{\"type\":\"CONTRADICTION\"}]}"), "missing field issues[0].description");
        assertErrors(parseCritic("{\"verdict\":null,\"issues\":[]}"), "missing field verdict");
    }

    @Test
    void wrongJsonTypesAreRejected() {
        assertErrors(parseCritic("{\"verdict\":1,\"issues\":[]}"), "verdict has the wrong type");
        assertErrors(parseCritic("{\"verdict\":\"PASS\",\"issues\":{}}"), "issues has the wrong type");
        assertErrors(parseCritic("{\"verdict\":\"FAIL\",\"issues\":[{\"type\":\"CONTRADICTION\",\"description\":2}]}"),
            "issues[0].description has the wrong type");
    }

    @Test
    void anUnknownVerdictIsRejected() {
        assertErrors(parseCritic("{\"verdict\":\"MAYBE\",\"issues\":[]}"), "verdict must be one of PASS, FAIL");
    }

    @Test
    void anUnknownIssueTypeIsRejected() {
        assertErrors(parseCritic("{\"verdict\":\"FAIL\",\"issues\":[{\"type\":\"OTHER\",\"description\":\"x\"}]}"),
            "issues[0].type must be one of " + TYPES);
    }

    // C4
    @Test
    void verdictAndIssuesMustAgree() {
        assertErrors(parseCritic("{\"verdict\":\"FAIL\",\"issues\":[]}"), "verdict FAIL needs at least 1 issue");
        assertErrors(parseCritic(CriticFixtures.CR_JUMP.replace("\"verdict\":\"FAIL\"", "\"verdict\":\"PASS\"")),
            "verdict PASS must have no issues");
        assertErrors(parseCritic(fail("   ")), "issues[0].description must not be blank");
    }

    // C5
    @Test
    void allRulesAreCheckedInOrder() {
        assertErrors(parseCritic("{\"verdict\":\"PASS\",\"issues\":[{\"type\":\"CONTRADICTION\",\"description\":\"  \"}]}"),
            "issues[0].description must not be blank", "verdict PASS must have no issues");
    }

    // C6
    @Test
    void descriptionsAreNormalizedAndCapped() {
        assertThat(valid(parseCritic(fail("  Line one\n\tline   two "))).issues()).containsExactly("CONTRADICTION | Line one line two");
        assertThat(valid(parseCritic(fail("x".repeat(350)))).issues()).containsExactly("CONTRADICTION | " + "x".repeat(300) + "…");
        List<String> twelve = new ArrayList<>();
        for (int i = 1; i <= 12; i++) twelve.add("{\"type\":\"CONTRADICTION\",\"description\":\"issue " + i + "\"}");
        Critiqued c = valid(parseCritic("{\"verdict\":\"FAIL\",\"issues\":[" + String.join(",", twelve) + "]}"));
        assertThat(c.issues()).hasSize(10);
        assertThat(c.issues().get(0)).isEqualTo("CONTRADICTION | issue 1");
        assertThat(c.issues().get(9)).isEqualTo("CONTRADICTION | issue 10");
    }

    // C7
    @Test
    void markupAndPipesInADescriptionAreKeptVerbatim() {
        String d = "<img src=x onerror=alert(1)> a|b";
        assertThat(valid(parseCritic(fail(d))).issues()).containsExactly("CONTRADICTION | " + d);
    }

    // ---- slice 07_starting-conditions (FR-58): the type domain is exactly six values --------------------------------------------

    private static String issue(String type) {
        return "{\"type\":\"" + type + "\",\"description\":\"d " + type + "\"}";
    }

    // @trace FR-58
    @ParameterizedTest(name = "type {0} alone is valid")
    @ValueSource(strings = {"UNSUPPORTED_FACTUAL_JUMP", "CONTRADICTION", "UNREALISTIC_TIMELINE", "WILDCARD_FORCING", "SETTINGS_MISMATCH",
        "INAPPROPRIATE_CERTAINTY"})
    void eachOfTheSixTypesAloneIsValidAndKept(String type) {
        Critiqued c = valid(parseCritic("{\"verdict\":\"FAIL\",\"issues\":[" + issue(type) + "]}"));
        assertThat(c.verdict()).contains("FAIL");
        assertThat(c.issues()).containsExactly(type + " | d " + type);
    }

    // @trace FR-58
    @Test
    void aFailWithAllSixTypesKeepsThemInOrder() {
        Critiqued c = valid(parseCritic("{\"verdict\":\"FAIL\",\"issues\":[" + String.join(",", TYPE_LIST.stream().map(CriticParserTest::issue).toList()) + "]}"));
        assertThat(c.issues()).containsExactlyElementsOf(TYPE_LIST.stream().map(t -> t + " | d " + t).toList());
    }

    // @trace FR-58
    @ParameterizedTest(name = "type \"{0}\" is rejected")
    @ValueSource(strings = {"IGNORED_COUNTER_SIGNALS", "OTHER", "contradiction", "Contradiction", "", " CONTRADICTION"})
    void everyOtherTypeStringIsATypeError(String type) {
        assertErrors(parseCritic("{\"verdict\":\"FAIL\",\"issues\":[" + issue(type) + "]}"), "issues[0].type must be one of " + TYPES);
    }

    // @trace FR-58
    @Test
    void theErrorNamesTheFirstOffendingIndex() {
        assertErrors(parseCritic("{\"verdict\":\"FAIL\",\"issues\":[" + issue("CONTRADICTION") + "," + issue("IGNORED_COUNTER_SIGNALS") + "]}"),
            "issues[1].type must be one of " + TYPES);
        assertErrors(parseCritic("{\"verdict\":\"FAIL\",\"issues\":[" + issue("OTHER") + "," + issue("IGNORED_COUNTER_SIGNALS") + "]}"),
            "issues[0].type must be one of " + TYPES);
    }

    // @trace FR-58
    @Test
    void aPassCarryingAnIgnoredCounterSignalsIssueReportsTheTypeErrorNotThePassRule() {
        assertErrors(parseCritic("{\"verdict\":\"PASS\",\"issues\":[" + issue("IGNORED_COUNTER_SIGNALS") + "]}"),
            "issues[0].type must be one of " + TYPES);
    }

    // @trace FR-58
    @Test
    void theLegacyCritiqueFixtureIsRejected() {
        assertErrors(parseCritic(CriticFixtures.CR_ICS), "issues[0].type must be one of " + TYPES);
        assertErrors(parseCritic(CriticFixtures.fixture("CR-ICS")), "issues[0].type must be one of " + TYPES);
    }
}
