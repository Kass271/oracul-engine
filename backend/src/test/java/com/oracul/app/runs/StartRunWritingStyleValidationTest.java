package com.oracul.app.runs;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oracul.app.style.WritingStyles;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * startRun validation of output.writingStyle and output.language (slice 02_style-selection), shipped style file.
 * Without a ChatGPT connection a valid body answers 401 CHATGPT_NOT_CONNECTED, so 400 vs 401 tells validation apart
 * from acceptance (the technique of StartRunOutputValidationTest).
 */
@WebMvcTest
@Import(WritingStyles.class)
class StartRunWritingStyleValidationTest {

    private static final String NOT_CONNECTED = "Connect ChatGPT to generate";
    private static final String STYLE = "Unknown writing style";
    private static final String LANGUAGE = "Unknown story language";
    private static final String STORY = "Story output is required";
    private static final String ILLUSTRATION = "Illustration is not available yet (MVP+1)";

    private static final List<String> OFFICIAL_IDS = List.of("official-briefing");
    private static final List<String> NARRATIVE_IDS =
        List.of("neutral-narrative", "poe", "kafka", "wells", "verne", "orwell", "clarke", "harrison");
    private static final List<String> ALL_IDS = DickensStyleFile.SHIPPED_IDS;

    @Autowired
    private MockMvc mvc;

    private static String style(String mode, String profileId) {
        return "{\"mode\":\"" + mode + "\",\"profileId\":\"" + profileId + "\"}";
    }

    /** Output with raw JSON values; a null value leaves the key out. */
    private static String output(String styleRaw, String languageRaw) {
        StringBuilder b = new StringBuilder("{\"story\":true,\"illustration\":false");
        if (styleRaw != null) b.append(",\"writingStyle\":").append(styleRaw);
        if (languageRaw != null) b.append(",\"language\":").append(languageRaw);
        return b.append('}').toString();
    }

    private static String body(String outputRaw) {
        return "{\"realism\":8,\"darkness\":5,\"optimism\":5,\"horizon\":\"1y\",\"wildcards\":[],\"customWildcards\":[],"
            + "\"output\":" + outputRaw + "}";
    }

    private static String bodyWith(String realism, String wildcards, String custom, String outputRaw) {
        return "{\"realism\":" + realism + ",\"darkness\":5,\"optimism\":5,\"horizon\":\"1y\",\"wildcards\":" + wildcards
            + ",\"customWildcards\":" + custom + ",\"output\":" + outputRaw + "}";
    }

    private ResultActions run(String json) throws Exception {
        return mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void assertError(String json, int status, String code, String message) throws Exception {
        run(json).andExpect(status().is(status))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.message").value(message));
    }

    private void assertAccepted(String json) throws Exception {
        assertError(json, 401, "CHATGPT_NOT_CONNECTED", NOT_CONNECTED);
    }

    private void assertUnknownStyle(String json) throws Exception {
        assertError(json, 400, "VALIDATION_FAILED", STYLE);
    }

    private void assertUnknownLanguage(String json) throws Exception {
        assertError(json, 400, "VALIDATION_FAILED", LANGUAGE);
    }

    // ---- writingStyle: valid classes ----

    static Stream<Arguments> validStyles() {
        Stream<Arguments> pairs = Stream.concat(
            OFFICIAL_IDS.stream().map(id -> Arguments.of("OFFICIAL " + id, style("OFFICIAL", id))),
            NARRATIVE_IDS.stream().map(id -> Arguments.of("NARRATIVE " + id, style("NARRATIVE", id))));
        return Stream.concat(pairs, Stream.of(
            Arguments.of("absent", null),
            Arguments.of("null", "null"),
            Arguments.of("unknown inner key", "{\"mode\":\"NARRATIVE\",\"profileId\":\"kafka\",\"foo\":1}"),
            Arguments.of("unknown inner keys, other order", "{\"foo\":{},\"profileId\":\"official-briefing\",\"mode\":\"OFFICIAL\"}")));
    }

    // Ranges & invariants: each of the 9 shipped (mode, profileId) pairs, absent, null and an unknown inner key pass.
    // @trace FR-63
    @ParameterizedTest(name = "writingStyle {0} passes validation")
    @MethodSource("validStyles")
    void validStylePassesValidation(String name, String styleRaw) throws Exception {
        assertAccepted(body(output(styleRaw, null)));
    }

    // ---- writingStyle: exhaustive mode x profile grid ----

    static Stream<Arguments> modeProfileGrid() {
        return Stream.of("OFFICIAL", "NARRATIVE").flatMap(mode -> ALL_IDS.stream().map(id -> {
            boolean valid = mode.equals(id.equals("official-briefing") ? "OFFICIAL" : "NARRATIVE");
            return Arguments.of(mode, id, valid);
        }));
    }

    // Ranges & invariants: all 2 x 9 combinations; valid iff the mode equals the profile's mode (9 valid, 9 rejected).
    // @trace FR-63
    @ParameterizedTest(name = "mode {0}, profile {1}, valid {2}")
    @MethodSource("modeProfileGrid")
    void modeProfileGridIsValidIffTheModeMatches(String mode, String profileId, boolean valid) throws Exception {
        String json = body(output(style(mode, profileId), null));
        if (valid) assertAccepted(json);
        else assertUnknownStyle(json);
    }

    // ---- writingStyle: invalid classes ----

    static Stream<Arguments> invalidStyles() {
        return Stream.of(
            // not an object
            Arguments.of("string kafka", "\"kafka\""),
            Arguments.of("empty string", "\"\""),
            Arguments.of("number", "5"),
            Arguments.of("true", "true"),
            Arguments.of("false", "false"),
            Arguments.of("empty array", "[]"),
            Arguments.of("array holding a valid object", "[{\"mode\":\"OFFICIAL\",\"profileId\":\"official-briefing\"}]"),
            Arguments.of("empty object", "{}"),
            // mode
            Arguments.of("mode missing", "{\"profileId\":\"kafka\"}"),
            Arguments.of("mode null", "{\"mode\":null,\"profileId\":\"kafka\"}"),
            Arguments.of("mode official", "{\"mode\":\"official\",\"profileId\":\"official-briefing\"}"),
            Arguments.of("mode Narrative", "{\"mode\":\"Narrative\",\"profileId\":\"kafka\"}"),
            Arguments.of("mode POETIC", "{\"mode\":\"POETIC\",\"profileId\":\"kafka\"}"),
            Arguments.of("mode empty", "{\"mode\":\"\",\"profileId\":\"kafka\"}"),
            Arguments.of("mode 1", "{\"mode\":1,\"profileId\":\"kafka\"}"),
            Arguments.of("mode array", "{\"mode\":[\"NARRATIVE\"],\"profileId\":\"kafka\"}"),
            // profileId
            Arguments.of("profileId missing", "{\"mode\":\"NARRATIVE\"}"),
            Arguments.of("profileId null", "{\"mode\":\"NARRATIVE\",\"profileId\":null}"),
            Arguments.of("profileId empty", "{\"mode\":\"NARRATIVE\",\"profileId\":\"\"}"),
            Arguments.of("profileId 5", "{\"mode\":\"NARRATIVE\",\"profileId\":5}"),
            Arguments.of("profileId Kafka", "{\"mode\":\"NARRATIVE\",\"profileId\":\"Kafka\"}"),
            Arguments.of("profileId ' kafka'", "{\"mode\":\"NARRATIVE\",\"profileId\":\" kafka\"}"),
            Arguments.of("profileId 'kafka '", "{\"mode\":\"NARRATIVE\",\"profileId\":\"kafka \"}"),
            Arguments.of("profileId dickens (not in the shipped file)", "{\"mode\":\"NARRATIVE\",\"profileId\":\"dickens\"}"),
            Arguments.of("profileId 41 x a", "{\"mode\":\"NARRATIVE\",\"profileId\":\"" + "a".repeat(41) + "\"}"),
            Arguments.of("profileId 5000 x a", "{\"mode\":\"NARRATIVE\",\"profileId\":\"" + "a".repeat(5000) + "\"}"),
            Arguments.of("profileId object", "{\"mode\":\"NARRATIVE\",\"profileId\":{\"id\":\"kafka\"}}"),
            Arguments.of("profileId array", "{\"mode\":\"NARRATIVE\",\"profileId\":[\"kafka\"]}"),
            // the other mode's profile
            Arguments.of("OFFICIAL with kafka", style("OFFICIAL", "kafka")),
            Arguments.of("OFFICIAL with neutral-narrative", style("OFFICIAL", "neutral-narrative")),
            Arguments.of("NARRATIVE with official-briefing", style("NARRATIVE", "official-briefing")));
    }

    // Ranges & invariants: every invalid class answers exactly 400 VALIDATION_FAILED "Unknown writing style".
    // @trace FR-63
    @ParameterizedTest(name = "writingStyle {0} -> Unknown writing style")
    @MethodSource("invalidStyles")
    void invalidStyleIsRejected(String name, String styleRaw) throws Exception {
        assertUnknownStyle(body(output(styleRaw, null)));
    }

    // @trace FR-63
    @ParameterizedTest(name = "writingStyle {0} is rejected before the language is looked at")
    @MethodSource("invalidStyles")
    void invalidStyleWinsOverAnInvalidLanguage(String name, String styleRaw) throws Exception {
        assertUnknownStyle(body(output(styleRaw, "\"de\"")));
    }

    // @trace FR-63
    @Test
    void invalidStyleIsRejectedEvenWhenNotConnected() throws Exception {
        // 400, never the 401 of a valid body: validation comes before the ChatGPT connection check
        assertUnknownStyle(body(output(style("NARRATIVE", "nobody"), null)));
    }

    // ---- language ----

    static Stream<Arguments> validLanguages() {
        return Stream.of(
            Arguments.of("en", "\"en\""),
            Arguments.of("uk", "\"uk\""),
            Arguments.of("absent", null),
            Arguments.of("null", "null"));
    }

    // Ranges & invariants: "en", "uk", absent and null pass.
    // @trace FR-64
    @ParameterizedTest(name = "language {0} passes validation")
    @MethodSource("validLanguages")
    void validLanguagePassesValidation(String name, String languageRaw) throws Exception {
        assertAccepted(body(output(null, languageRaw)));
    }

    static Stream<Arguments> invalidLanguages() {
        return Stream.of(
            Arguments.of("EN", "\"EN\""),
            Arguments.of("Uk", "\"Uk\""),
            Arguments.of("UK", "\"UK\""),
            Arguments.of("ua", "\"ua\""),
            Arguments.of("uk-UA", "\"uk-UA\""),
            Arguments.of("en-US", "\"en-US\""),
            Arguments.of("de", "\"de\""),
            Arguments.of("empty", "\"\""),
            Arguments.of("' en'", "\" en\""),
            Arguments.of("'uk '", "\"uk \""),
            Arguments.of("number 1", "1"),
            Arguments.of("number 0", "0"),
            Arguments.of("true", "true"),
            Arguments.of("false", "false"),
            Arguments.of("array", "[\"uk\"]"),
            Arguments.of("empty object", "{}"),
            Arguments.of("object with code", "{\"code\":\"uk\"}"));
    }

    // Ranges & invariants: every invalid class answers exactly 400 VALIDATION_FAILED "Unknown story language".
    // @trace FR-64
    @ParameterizedTest(name = "language {0} -> Unknown story language")
    @MethodSource("invalidLanguages")
    void invalidLanguageIsRejected(String name, String languageRaw) throws Exception {
        assertUnknownLanguage(body(output(null, languageRaw)));
    }

    // @trace FR-64
    @ParameterizedTest(name = "language {0} with a valid style -> Unknown story language")
    @MethodSource("invalidLanguages")
    void invalidLanguageIsRejectedAfterAValidStyle(String name, String languageRaw) throws Exception {
        assertUnknownLanguage(body(output(style("NARRATIVE", "poe"), languageRaw)));
    }

    // @trace FR-64
    @Test
    void invalidLanguageIsRejectedEvenWhenNotConnected() throws Exception {
        assertUnknownLanguage(body(output(null, "\"fr\"")));
    }

    // ---- precedence ----

    // @trace FR-63
    @Test
    void realismViolationComesBeforeABadStyle() throws Exception {
        assertError(bodyWith("11", "[]", "[]", output("\"kafka\"", null)), 400, "VALIDATION_FAILED",
            "realism must be between 1 and 10");
    }

    // @trace FR-63
    @Test
    void unknownWildcardComesBeforeABadStyle() throws Exception {
        assertError(bodyWith("8", "[{\"wildcardId\":\"nope\",\"intensity\":5}]", "[]", output("\"kafka\"", null)), 400,
            "VALIDATION_FAILED", "unknown wildcard: nope");
    }

    // @trace FR-63
    @Test
    void tooManyCustomWildcardsComeBeforeABadStyle() throws Exception {
        String four = "[{\"label\":\"a\",\"intensity\":5},{\"label\":\"b\",\"intensity\":5},"
            + "{\"label\":\"c\",\"intensity\":5},{\"label\":\"d\",\"intensity\":5}]";
        assertError(bodyWith("8", "[]", four, output("\"kafka\"", null)), 400, "VALIDATION_FAILED",
            "At most 3 custom wildcards");
    }

    // @trace FR-63
    @Test
    void storyFalseComesBeforeABadStyle() throws Exception {
        assertError(body("{\"story\":false,\"illustration\":false,\"writingStyle\":\"kafka\",\"language\":\"de\"}"), 400,
            "VALIDATION_FAILED", STORY);
    }

    // @trace FR-63
    @Test
    void illustrationTrueComesBeforeABadStyle() throws Exception {
        assertError(body("{\"story\":true,\"illustration\":true,\"writingStyle\":\"kafka\",\"language\":\"de\"}"), 400,
            "VALIDATION_FAILED", ILLUSTRATION);
    }

    // @trace FR-64
    @Test
    void storyFalseComesBeforeABadLanguage() throws Exception {
        assertError(body("{\"story\":false,\"illustration\":false,\"language\":\"de\"}"), 400, "VALIDATION_FAILED", STORY);
    }

    // @trace FR-64
    @Test
    void illustrationTrueComesBeforeABadLanguage() throws Exception {
        assertError(body("{\"story\":true,\"illustration\":true,\"language\":\"de\"}"), 400, "VALIDATION_FAILED",
            ILLUSTRATION);
    }

    // @trace FR-64
    @Test
    void badStyleAndBadLanguageReportTheStyle() throws Exception {
        assertUnknownStyle(body(output("{\"mode\":\"OFFICIAL\",\"profileId\":\"kafka\"}", "\"de\"")));
    }

    // @trace FR-64
    @Test
    void validStyleAndBadLanguageReportTheLanguage() throws Exception {
        assertUnknownLanguage(body(output(style("OFFICIAL", "official-briefing"), "\"de\"")));
    }

    // @trace FR-63, FR-64
    @Test
    void validStyleAndLanguageTogetherPassValidation() throws Exception {
        assertAccepted(body(output(style("NARRATIVE", "kafka"), "\"uk\"")));
    }

    // @trace FR-63
    @Test
    void theBadStyleBodyIsExactlyTwoKeysWithNoBeanValidationText() throws Exception {
        run(body(output("{\"mode\":\"NARRATIVE\",\"profileId\":null}", null)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.message").value(STYLE));
    }
}
