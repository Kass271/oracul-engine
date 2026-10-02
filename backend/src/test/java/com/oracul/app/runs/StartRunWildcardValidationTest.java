package com.oracul.app.runs;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// @trace FR-4
@WebMvcTest
class StartRunWildcardValidationTest {

    private static final List<String> ALL_IDS = List.of(
        "ai-agi-breakthrough", "ai-stagnation", "ai-loss-of-control",
        "robotics-massive-automation", "robotics-humanoid-boom", "robotics-robot-uprising",
        "biology-new-pandemic", "biology-dangerous-mutation", "biology-medical-breakthrough",
        "biology-synthetic-biology",
        "political-democracy-strengthens", "political-authoritarian-expansion",
        "political-international-institutions", "political-global-fragmentation",
        "economy-global-boom", "economy-global-recession", "economy-financial-crisis",
        "energy-fusion-breakthrough", "energy-cheap-energy", "energy-energy-crisis",
        "environment-extreme-climate-event", "environment-climate-stabilization",
        "environment-ecosystem-collapse",
        "space-major-discovery", "space-asteroid-threat", "space-moon-settlement", "space-mars-breakthrough",
        "extreme-alien-contact", "extreme-unknown-intelligence", "extreme-unexplained-phenomenon");

    private static final String INTENSITY = "wildcard intensity must be between 1 and 10";

    @Autowired
    private MockMvc mvc;

    private static String body(String wildcardsJson) {
        return "{\"realism\":8,\"darkness\":5,\"optimism\":5,\"horizon\":\"1y\",\"wildcards\":" + wildcardsJson
            + ",\"customWildcards\":[],\"output\":{\"story\":true,\"illustration\":false}}";
    }

    private static String bodyWith(String field, String raw, String wildcardsJson) {
        String b = body(wildcardsJson);
        return b.replaceFirst("\"" + field + "\":[^,]+", "\"" + field + "\":" + raw);
    }

    private static String wc(String id, String intensityRaw) {
        return intensityRaw == null
            ? "{\"wildcardId\":\"" + id + "\"}"
            : "{\"wildcardId\":\"" + id + "\",\"intensity\":" + intensityRaw + "}";
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

    private void assertValid(String wildcardsJson) throws Exception {
        assertError(body(wildcardsJson), 401, "CHATGPT_NOT_CONNECTED", "Connect ChatGPT to generate");
    }

    private void assertInvalid(String wildcardsJson, String message) throws Exception {
        assertError(body(wildcardsJson), 400, "VALIDATION_FAILED", message);
    }

    // ---- valid inputs reach the connection check ----

    // @trace FR-4
    @Test
    void validWildcardsPassValidation() throws Exception {
        assertValid("[" + wc("biology-new-pandemic", "8") + "," + wc("robotics-humanoid-boom", "6") + "]");
    }

    // @trace FR-4
    @Test
    void allThirtyCatalogueIdsPassValidation() throws Exception {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < ALL_IDS.size(); i++) items.add(wc(ALL_IDS.get(i), i % 2 == 0 ? "1" : "10"));
        assertValid("[" + String.join(",", items) + "]");
    }

    // @trace FR-4
    @ParameterizedTest
    @ValueSource(strings = {"1", "10"})
    void intensityBoundsAreAccepted(String raw) throws Exception {
        assertValid("[" + wc("biology-new-pandemic", raw) + "]");
    }

    // @trace FR-4
    @Test
    void unknownExtraPropertyInAnElementIsIgnored() throws Exception {
        assertValid("[{\"wildcardId\":\"biology-new-pandemic\",\"intensity\":5,\"foo\":1}]");
    }

    // ---- unknown id ----

    // @trace FR-4
    @ParameterizedTest
    @ValueSource(strings = {"biology-zombies", "Biology-New-Pandemic"})
    void unknownWildcardIdIsRejectedVerbatim(String id) throws Exception {
        assertInvalid("[" + wc(id, "5") + "]", "unknown wildcard: " + id);
    }

    // @trace FR-4
    @Test
    void emptyWildcardIdIsUnknown() throws Exception {
        assertInvalid("[" + wc("", "5") + "]", "unknown wildcard: ");
    }

    // @trace FR-4
    @Test
    void overlongWildcardIdIsUnknown() throws Exception {
        String id = "x".repeat(70);
        assertInvalid("[" + wc(id, "5") + "]", "unknown wildcard: " + id);
    }

    // @trace FR-4
    @ParameterizedTest
    @ValueSource(strings = {"{\"intensity\":5}", "{\"wildcardId\":null,\"intensity\":5}",
        "{\"wildcardId\":7,\"intensity\":5}"})
    void missingNullOrNonStringIdIsInvalid(String element) throws Exception {
        assertInvalid("[" + element + "]", "wildcards[0].wildcardId is invalid");
    }

    // ---- duplicate ----

    // @trace FR-4
    @Test
    void duplicateWildcardIdIsRejected() throws Exception {
        assertInvalid("[" + wc("biology-new-pandemic", "5") + "," + wc("biology-new-pandemic", "7") + "]",
            "duplicate wildcard: biology-new-pandemic");
    }

    // @trace FR-4
    @Test
    void thirtyOneEntriesReportTheDuplicate() throws Exception {
        List<String> items = new ArrayList<>();
        for (String id : ALL_IDS) items.add(wc(id, "5"));
        items.add(wc("biology-new-pandemic", "5"));
        assertInvalid("[" + String.join(",", items) + "]", "duplicate wildcard: biology-new-pandemic");
    }

    // ---- intensity ----

    // @trace FR-4
    @ParameterizedTest
    @ValueSource(strings = {"0", "11", "-1", "null", "5.5", "\"8\"", "true"})
    void badIntensityIsRejected(String raw) throws Exception {
        assertInvalid("[" + wc("biology-new-pandemic", raw) + "]", INTENSITY);
    }

    // @trace FR-4
    @Test
    void missingIntensityIsRejected() throws Exception {
        assertInvalid("[" + wc("biology-new-pandemic", null) + "]", INTENSITY);
    }

    // ---- precedence ----

    // @trace FR-4
    @Test
    void firstFailingElementWins() throws Exception {
        assertInvalid("[" + wc("biology-new-pandemic", "11") + "," + wc("biology-zombies", "5") + "]", INTENSITY);
    }

    // @trace FR-4
    @Test
    void idIsCheckedBeforeIntensity() throws Exception {
        assertInvalid("[" + wc("biology-zombies", "11") + "]", "unknown wildcard: biology-zombies");
    }

    // @trace FR-4
    @Test
    void duplicateIsCheckedBeforeIntensity() throws Exception {
        assertInvalid("[" + wc("biology-new-pandemic", "5") + "," + wc("biology-new-pandemic", "0") + "]",
            "duplicate wildcard: biology-new-pandemic");
    }

    // ---- wildcards container ----

    // @trace FR-4
    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "\"x\"", "[1]", "[null]"})
    void invalidWildcardsContainerIsRejected(String raw) throws Exception {
        assertInvalid(raw, "wildcards is invalid");
    }

    // @trace FR-4
    @Test
    void earlierFieldErrorsTakePrecedenceOverWildcards() throws Exception {
        assertError(bodyWith("realism", "0", "[" + wc("biology-zombies", "5") + "]"), 400, "VALIDATION_FAILED",
            "realism must be between 1 and 10");
        assertError(bodyWith("horizon", "\"3y\"", "[" + wc("biology-zombies", "5") + "]"), 400,
            "VALIDATION_FAILED", "unknown horizon");
    }
}
