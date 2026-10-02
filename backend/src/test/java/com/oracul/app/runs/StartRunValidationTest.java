package com.oracul.app.runs;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest
class StartRunValidationTest {

    private static final String NOT_JSON = "Request body is not valid JSON";

    @Autowired
    private MockMvc mvc;

    /** Base valid body as raw JSON values; a null value removes the field. */
    private static String body(String... overrides) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("realism", "8");
        m.put("darkness", "5");
        m.put("optimism", "5");
        m.put("horizon", "\"1y\"");
        m.put("wildcards", "[]");
        m.put("customWildcards", "[]");
        m.put("output", "{\"story\":true,\"illustration\":false}");
        for (int i = 0; i < overrides.length; i += 2) {
            if (overrides[i + 1] == null) m.remove(overrides[i]);
            else m.put(overrides[i], overrides[i + 1]);
        }
        return m.entrySet().stream()
            .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
            .collect(Collectors.joining(",", "{", "}"));
    }

    private ResultActions startRun(String json) throws Exception {
        return mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void assertError(ResultActions r, int status, String code, String message) throws Exception {
        r.andExpect(status().is(status))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.message").value(message));
    }

    private void assertInvalid(String json, String message) throws Exception {
        assertError(startRun(json), 400, "VALIDATION_FAILED", message);
    }

    // ---- FR-2 ----

    // @trace FR-2
    @ParameterizedTest
    @ValueSource(strings = {"0", "11", "-1", "null", "5.5", "\"9\"", "true"})
    void darknessOutOfRangeOrWrongTypeIsRejected(String raw) throws Exception {
        assertInvalid(body("darkness", raw), "darkness must be between 1 and 10");
    }

    // @trace FR-2
    @Test
    void darknessMissingIsRejected() throws Exception {
        assertInvalid(body("darkness", null), "darkness must be between 1 and 10");
    }

    // @trace FR-2
    @ParameterizedTest
    @ValueSource(strings = {"0", "11", "null", "5.5", "\"9\""})
    void optimismOutOfRangeOrWrongTypeIsRejected(String raw) throws Exception {
        assertInvalid(body("optimism", raw), "optimism must be between 1 and 10");
    }

    // @trace FR-2
    @Test
    void optimismMissingIsRejected() throws Exception {
        assertInvalid(body("optimism", null), "optimism must be between 1 and 10");
    }

    // @trace FR-2
    @ParameterizedTest
    @ValueSource(strings = {"0", "11", "null", "5.5", "\"9\""})
    void realismOutOfRangeOrWrongTypeIsRejected(String raw) throws Exception {
        assertInvalid(body("realism", raw), "realism must be between 1 and 10");
    }

    // @trace FR-2
    @Test
    void realismMissingIsRejected() throws Exception {
        assertInvalid(body("realism", null), "realism must be between 1 and 10");
    }

    // @trace FR-2
    @Test
    void realismReportedBeforeDarknessAndHorizon() throws Exception {
        assertInvalid(body("realism", "0", "darkness", "11", "horizon", "\"3y\""), "realism must be between 1 and 10");
    }

    // @trace FR-2
    @Test
    void darknessReportedBeforeHorizon() throws Exception {
        assertInvalid(body("darkness", "11", "horizon", "\"3y\""), "darkness must be between 1 and 10");
    }

    // @trace FR-2
    @ParameterizedTest
    @ValueSource(strings = {"{\"realism\":", ""})
    void truncatedOrEmptyBodyIsNotValidJson(String json) throws Exception {
        assertInvalid(json, NOT_JSON);
    }

    // @trace FR-2
    @Test
    void jsonArrayBodyIsNotValidJson() throws Exception {
        assertInvalid("[]", NOT_JSON);
    }

    // @trace FR-2
    @Test
    void jsonStringBodyIsNotValidJson() throws Exception {
        assertInvalid("\"x\"", NOT_JSON);
    }

    // @trace FR-2
    @Test
    void nonJsonContentTypeIsRejected() throws Exception {
        assertError(
            mvc.perform(post("/api/runs").contentType(MediaType.TEXT_PLAIN).content(body())),
            400, "VALIDATION_FAILED", NOT_JSON);
    }

    // @trace FR-2
    @Test
    void missingWildcardsFallsBackToFieldIsInvalid() throws Exception {
        assertInvalid(body("wildcards", null), "wildcards is invalid");
    }

    // @trace FR-2
    @Test
    void errorBodiesLeakNoInternals() throws Exception {
        startRun(body("darkness", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.trace").doesNotExist())
            .andExpect(jsonPath("$.exception").doesNotExist())
            .andExpect(jsonPath("$.path").doesNotExist())
            .andExpect(jsonPath("$.timestamp").doesNotExist())
            .andExpect(jsonPath("$.error").doesNotExist());
    }

    // @trace FR-2
    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"darkness\":1,\"optimism\":1,\"realism\":1}",
        "{\"darkness\":10,\"optimism\":10,\"realism\":10}",
        "{\"darkness\":9,\"optimism\":9}",
        "{\"foo\":1}",
    })
    void validIntensitiesPassValidationAndReachConnectionCheck(String variant) throws Exception {
        String json = switch (variant) {
            case "{}" -> body();
            case "{\"foo\":1}" -> body("foo", "1");
            case "{\"darkness\":1,\"optimism\":1,\"realism\":1}" -> body("darkness", "1", "optimism", "1", "realism", "1");
            case "{\"darkness\":10,\"optimism\":10,\"realism\":10}" ->
                body("darkness", "10", "optimism", "10", "realism", "10");
            default -> body("darkness", "9", "optimism", "9");
        };
        assertError(startRun(json), 401, "CHATGPT_NOT_CONNECTED", "Connect ChatGPT to generate");
    }

    // ---- FR-3 ----

    // @trace FR-3
    @ParameterizedTest
    @ValueSource(strings = {"\"3y\"", "\"1Y\"", "\"\"", "5", "null"})
    void unknownHorizonIsRejected(String raw) throws Exception {
        assertInvalid(body("horizon", raw), "unknown horizon");
    }

    // @trace FR-3
    @Test
    void missingHorizonIsRejected() throws Exception {
        assertInvalid(body("horizon", null), "unknown horizon");
    }

    // @trace FR-3
    @ParameterizedTest
    @ValueSource(strings = {"1d", "1w", "1m", "1y", "5y", "10y", "20y"})
    void everyHorizonCodePassesValidation(String code) throws Exception {
        assertError(startRun(body("horizon", "\"" + code + "\"")), 401, "CHATGPT_NOT_CONNECTED", "Connect ChatGPT to generate");
    }
}
