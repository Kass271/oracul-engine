package com.oracul.app.runs;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// @trace FR-6
@WebMvcTest
class StartRunOutputValidationTest {

    private static final String OK_MSG = "Connect ChatGPT to generate";
    private static final String STORY = "Story output is required";
    private static final String ILLUSTRATION = "Illustration is not available yet (MVP+1)";

    @Autowired
    private MockMvc mvc;

    /** output raw JSON; null removes the key. */
    private static String body(String outputRaw) {
        return "{\"realism\":8,\"darkness\":5,\"optimism\":5,\"horizon\":\"1y\",\"wildcards\":[],\"customWildcards\":[]"
            + (outputRaw == null ? "" : ",\"output\":" + outputRaw) + "}";
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

    static Stream<Arguments> outputs() {
        return Stream.of(
            Arguments.of("{\"story\":true,\"illustration\":false}", null),
            Arguments.of("{\"story\":true,\"illustration\":false,\"foo\":1}", null),
            Arguments.of("{\"story\":false,\"illustration\":false}", STORY),
            Arguments.of("{\"story\":null,\"illustration\":false}", STORY),
            Arguments.of("{\"story\":\"true\",\"illustration\":false}", STORY),
            Arguments.of("{\"story\":1,\"illustration\":false}", STORY),
            Arguments.of("{\"illustration\":false}", STORY),
            Arguments.of("null", STORY),
            Arguments.of("\"x\"", STORY),
            Arguments.of("[]", STORY),
            Arguments.of("{}", STORY),
            Arguments.of(null, STORY),
            Arguments.of("{\"story\":false,\"illustration\":true}", STORY),
            Arguments.of("{\"story\":true,\"illustration\":true}", ILLUSTRATION),
            Arguments.of("{\"story\":true,\"illustration\":null}", ILLUSTRATION),
            Arguments.of("{\"story\":true,\"illustration\":\"false\"}", ILLUSTRATION),
            Arguments.of("{\"story\":true,\"illustration\":0}", ILLUSTRATION),
            Arguments.of("{\"story\":true}", ILLUSTRATION));
    }

    // @trace FR-6
    @ParameterizedTest(name = "output {0} -> {1}")
    @MethodSource("outputs")
    void outputClasses(String output, String message) throws Exception {
        if (message == null) assertError(body(output), 401, "CHATGPT_NOT_CONNECTED", OK_MSG);
        else assertError(body(output), 400, "VALIDATION_FAILED", message);
    }

    // @trace FR-6
    @Test
    void customWildcardViolationComesBeforeOutputViolation() throws Exception {
        String json = "{\"realism\":8,\"darkness\":5,\"optimism\":5,\"horizon\":\"1y\",\"wildcards\":[],"
            + "\"customWildcards\":[{\"label\":\"\",\"intensity\":5}],"
            + "\"output\":{\"story\":false,\"illustration\":true}}";
        assertError(json, 400, "VALIDATION_FAILED", "Wildcard name must be 1–40 characters");
    }
}
