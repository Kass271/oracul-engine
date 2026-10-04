package com.oracul.app.runs;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// @trace FR-5
@WebMvcTest
class StartRunCustomWildcardValidationTest {

    private static final String L40 = "a".repeat(40);
    private static final String L41 = "a".repeat(41);
    private static final String OK_MSG = "Connect ChatGPT to generate";
    private static final String NAME = "Wildcard name must be 1–40 characters";
    private static final String MAX = "At most 3 custom wildcards";
    private static final String DUP = "This wildcard already exists";
    private static final String INTENSITY = "wildcard intensity must be between 1 and 10";
    private static final String INVALID = "customWildcards is invalid";

    @Autowired
    private MockMvc mvc;

    private static String body(String customJson) {
        return bodyFull("8", "\"1y\"", "[]", customJson, "{\"story\":true,\"illustration\":false}");
    }

    private static String bodyFull(String realism, String horizon, String wildcards, String customJson, String output) {
        return "{\"realism\":" + realism + ",\"darkness\":5,\"optimism\":5,\"horizon\":" + horizon
            + ",\"wildcards\":" + wildcards + (customJson == null ? "" : ",\"customWildcards\":" + customJson)
            + ",\"output\":" + output + "}";
    }

    private static String quote(String s) {
        return "\"" + s + "\"";
    }

    /** One element; labelRaw / intensityRaw are raw JSON, null removes the key. */
    private static String cw(String labelRaw, String intensityRaw) {
        StringBuilder sb = new StringBuilder("{");
        if (labelRaw != null) sb.append("\"label\":").append(labelRaw);
        if (intensityRaw != null) sb.append(labelRaw != null ? "," : "").append("\"intensity\":").append(intensityRaw);
        return sb.append("}").toString();
    }

    private static String cw(String label, int intensity) {
        return cw(quote(label), String.valueOf(intensity));
    }

    private static String arr(String... items) {
        return "[" + String.join(",", items) + "]";
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

    private void assertValid(String customJson) throws Exception {
        assertError(body(customJson), 401, "CHATGPT_NOT_CONNECTED", OK_MSG);
    }

    private void assertInvalid(String customJson, String message) throws Exception {
        assertError(body(customJson), 400, "VALIDATION_FAILED", message);
    }

    // ---- accepted ----

    // @trace FR-5
    @Test
    void validCustomWildcardsReachTheConnectionCheck() throws Exception {
        assertValid(arr(cw("Ocean desalination boom", 5)));
        assertValid(arr(cw("A", 1), cw("B", 10), cw(L40, 5)));
        assertValid(arr(cw("  Mars colony  ", 7)));
        assertValid(arr(cw("  " + L40 + "  ", 7)));
        assertValid(arr(cw("Mars  colony", 5), cw("Mars colony", 5)));
        assertValid(arr(cw("New pandemic", 5)));
        assertValid("[{\"label\":\"x\",\"intensity\":5,\"foo\":1}]");
        assertValid("[]");
    }

    // ---- range: count classes 0..3 ok, 4 error ----

    static Stream<Arguments> counts() {
        return Stream.of(0, 1, 2, 3, 4).map(n -> Arguments.of(n));
    }

    // @trace FR-5
    @ParameterizedTest(name = "{0} custom wildcards")
    @MethodSource("counts")
    void countClasses(int n) throws Exception {
        String json = arr(java.util.stream.IntStream.range(0, n).mapToObj(i -> cw("Label " + i, 5)).toArray(String[]::new));
        if (n <= 3) assertValid(json);
        else assertInvalid(json, MAX);
    }

    // @trace FR-5
    @ParameterizedTest(name = "4 items, element {0} also invalid")
    @ValueSource(ints = {0, 1, 2, 3})
    void fourItemsWithAnInvalidElementStillReportCount(int bad) throws Exception {
        String[] items = {cw("A", 5), cw("B", 5), cw("C", 5), cw("D", 5)};
        items[bad] = cw("", 5);
        assertInvalid(arr(items), MAX);
    }

    // @trace FR-5
    @Test
    void shapeIsCheckedBeforeCount() throws Exception {
        assertInvalid(arr(cw("A", 5), cw("B", 5), cw("C", 5), "1"), INVALID);
    }

    // ---- range: label length classes ----

    static Stream<Arguments> labels() {
        return Stream.of(
            Arguments.of("", false), Arguments.of("   ", false), Arguments.of("a", true),
            Arguments.of(L40, true), Arguments.of(L41, false), Arguments.of("  " + L41, false),
            Arguments.of("  " + L40 + "  ", true), Arguments.of("  " + L41 + "  ", false));
    }

    // @trace FR-5
    @ParameterizedTest(name = "label \"{0}\" valid={1}")
    @MethodSource("labels")
    void labelLengthClasses(String label, boolean valid) throws Exception {
        String json = arr(cw(label, 5));
        if (valid) assertValid(json);
        else assertInvalid(json, NAME);
    }

    // @trace FR-5
    @ParameterizedTest(name = "label raw {0}")
    @ValueSource(strings = {"5", "true", "null", "{}", "[]"})
    void nonStringLabelIsRejected(String raw) throws Exception {
        assertInvalid(arr(cw(raw, "5")), NAME);
    }

    // @trace FR-5
    @Test
    void missingLabelIsRejected() throws Exception {
        assertInvalid(arr(cw(null, "5")), NAME);
    }

    // ---- duplicates ----

    static Stream<Arguments> duplicates() {
        return Stream.of(
            Arguments.of("Mars colony", "Mars colony"),
            Arguments.of("Mars colony", "mars colony"),
            Arguments.of("Mars colony", "  MARS COLONY "),
            Arguments.of("Mars colony", " Mars colony"),
            Arguments.of("Mars colony", "Mars colony  "));
    }

    // @trace FR-5
    @ParameterizedTest(name = "duplicate {0} / {1}")
    @MethodSource("duplicates")
    void duplicateClasses(String first, String second) throws Exception {
        assertInvalid(arr(cw(first, 5), cw(second, 3)), DUP);
    }

    // ---- intensity classes ----

    // @trace FR-5
    @ParameterizedTest(name = "intensity {0} valid")
    @ValueSource(strings = {"1", "5", "10"})
    void intensityInRangeAccepted(String raw) throws Exception {
        assertValid(arr(cw("\"Mars colony\"", raw)));
    }

    // @trace FR-5
    @ParameterizedTest(name = "intensity {0} invalid")
    @ValueSource(strings = {"0", "11", "-1", "null", "5.5", "\"8\"", "true"})
    void intensityOutOfRangeRejected(String raw) throws Exception {
        assertInvalid(arr(cw("\"Mars colony\"", raw)), INTENSITY);
    }

    // @trace FR-5
    @Test
    void missingIntensityIsRejected() throws Exception {
        assertInvalid(arr(cw("\"Mars colony\"", null)), INTENSITY);
    }

    // ---- precedence ----

    // @trace FR-5
    @Test
    void elementsAreCheckedInArrayOrder() throws Exception {
        assertInvalid(arr(cw("A", 11), cw("", 5)), INTENSITY);
    }

    // @trace FR-5
    @Test
    void labelIsCheckedBeforeIntensity() throws Exception {
        assertInvalid(arr(cw("", 11)), NAME);
    }

    // @trace FR-5
    @Test
    void duplicateIsCheckedBeforeIntensity() throws Exception {
        assertInvalid(arr(cw("A", 5), cw("a", 0)), DUP);
    }

    // ---- container ----

    // @trace FR-5
    @ParameterizedTest(name = "customWildcards {0}")
    @ValueSource(strings = {"null", "{}", "\"x\"", "[1]", "[null]", "[\"A\"]"})
    void invalidContainerIsRejected(String raw) throws Exception {
        assertInvalid(raw, INVALID);
    }

    // @trace FR-5
    @Test
    void missingContainerIsRejected() throws Exception {
        assertInvalid(null, INVALID);
    }

    // ---- earlier fields first, output after ----

    // @trace FR-5
    @Test
    void earlierFieldsTakePrecedence() throws Exception {
        String four = arr(cw("A", 5), cw("B", 5), cw("C", 5), cw("D", 5));
        assertError(bodyFull("8", "\"1y\"", "[{\"wildcardId\":\"biology-zombies\",\"intensity\":5}]", four,
            "{\"story\":true,\"illustration\":false}"), 400, "VALIDATION_FAILED", "unknown wildcard: biology-zombies");
        assertError(bodyFull("8", "\"3y\"", "[]", arr(cw("", 5)), "{\"story\":true,\"illustration\":false}"), 400,
            "VALIDATION_FAILED", "unknown horizon");
    }

    // @trace FR-5
    @Test
    void customWildcardViolationBeatsOutputViolation() throws Exception {
        assertError(bodyFull("8", "\"1y\"", "[]", arr(cw("", 5)), "{\"story\":false,\"illustration\":true}"), 400,
            "VALIDATION_FAILED", NAME);
    }

    // ---- invariant sweep: every label length 0..45 ----

    // @trace FR-5
    @Test
    void everyLabelLengthFromZeroToFortyFiveClassifiedByTrimmedLength() throws Exception {
        for (int len = 0; len <= 45; len++) {
            String label = "b".repeat(len);
            for (String padded : List.of(label, "  " + label + "  ")) {
                String json = arr(cw(padded, 5));
                if (len >= 1 && len <= 40) assertValid(json);
                else assertInvalid(json, NAME);
            }
        }
    }
}
