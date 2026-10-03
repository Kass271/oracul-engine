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
// @trace FR-22
class CriticParserTest {

    private static final String TYPES = "UNSUPPORTED_FACTUAL_JUMP, CONTRADICTION, UNREALISTIC_TIMELINE, IGNORED_COUNTER_SIGNALS, "
        + "WILDCARD_FORCING, SETTINGS_MISMATCH, INAPPROPRIATE_CERTAINTY";

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
        Critiqued ics = valid(parseCritic(CriticFixtures.CR_ICS));
        assertThat(ics.verdict()).contains("FAIL");
        assertThat(ics.issues()).containsExactly("IGNORED_COUNTER_SIGNALS | " + CriticFixtures.ICS_DESCRIPTION);
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
        assertErrors(parseCritic(CriticFixtures.CR_ICS.replace("\"verdict\":\"FAIL\"", "\"verdict\":\"PASS\"")),
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
}
