package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * startRun with output.writingStyle / output.language through the real application (slice 02_style-selection):
 * what is stored, what getRun and getScenarioConfiguration return, what a rejected request leaves behind.
 * Same configuration as StartRunIT (runs stay pending), so both share one Spring context.
 */
// @trace FR-63, FR-64
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT30S",
    "oracul.run.executor-threads=32",
})
class StartRunWritingStyleIT extends AbstractRunIT {

    private static final String STYLE_MSG = "Unknown writing style";
    private static final String LANGUAGE_MSG = "Unknown story language";

    @Override
    protected boolean awaitRunsAfterEach() {
        return false; // runs are kept pending on purpose
    }

    private static final List<String> PROFILES = List.of(
        "official-briefing", "neutral-narrative", "poe", "kafka", "wells", "verne", "orwell", "clarke", "harrison");

    private static String modeOf(String profileId) {
        return profileId.equals("official-briefing") ? "OFFICIAL" : "NARRATIVE";
    }

    /** B with the given raw JSON values inside output; a null value leaves the key out. */
    private static String bodyWith(String styleRaw, String languageRaw) {
        StringBuilder out = new StringBuilder("{\"story\":true,\"illustration\":false");
        if (styleRaw != null) out.append(",\"writingStyle\":").append(styleRaw);
        if (languageRaw != null) out.append(",\"language\":").append(languageRaw);
        out.append('}');
        return "{\"realism\":8,\"darkness\":5,\"optimism\":5,\"horizon\":\"1y\",\"wildcards\":[],\"customWildcards\":[],"
            + "\"output\":" + out + "}";
    }

    private static String style(String mode, String id) {
        return "{\"mode\":\"" + mode + "\",\"profileId\":\"" + id + "\"}";
    }

    private void assertError(ResultActions r, int status, String code, String message) throws Exception {
        r.andExpect(status().is(status))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.message").value(message));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> outputOf(Map<String, Object> run) {
        return (Map<String, Object>) ((Map<String, Object>) run.get("configuration")).get("output");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> outputOfConfiguration(Map<String, Object> configuration) {
        return (Map<String, Object>) configuration.get("output");
    }

    private Map<String, Object> scenarioConfiguration(String sid) throws Exception {
        MvcResult r = mvc.perform(get("/api/scenario/configuration").cookie(new Cookie("ORACUL_SID", sid))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return json(r.getResponse().getContentAsString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> storedOutput(String runId) {
        String cfg = jdbc.queryForObject("select cast(configuration as text) from generation_run where id = cast(? as uuid)",
            String.class, runId);
        return (Map<String, Object>) json(cfg).get("output");
    }

    /** The invariant of the spec: the 202 body, getRun, getScenarioConfiguration and the row all agree. */
    private void assertStoredEverywhere(String sid, Map<String, Object> accepted, Map<String, Object> expectedOutput)
        throws Exception {
        String id = (String) accepted.get("id");
        assertThat(outputOf(accepted)).isEqualTo(expectedOutput);
        assertThat(outputOf(json(getRun(sid, id)))).isEqualTo(expectedOutput);
        assertThat(outputOfConfiguration(scenarioConfiguration(sid))).isEqualTo(expectedOutput);
        assertThat(storedOutput(id)).isEqualTo(expectedOutput);
    }

    private static Map<String, Object> expected(String styleRaw, String languageRaw) {
        return json(bodyWith(styleRaw, languageRaw).replaceAll("^.*\"output\":", "").replaceAll("}$", ""));
    }

    // ---- accepted and stored as sent ----

    static Stream<Arguments> profiles() {
        return IntStream.range(0, PROFILES.size())
            .mapToObj(i -> Arguments.of(PROFILES.get(i), i % 2 == 0 ? "en" : "uk"));
    }

    // Ranges & invariants: each of the 9 shipped (mode, profileId) pairs is accepted (202) and stored as sent.
    // @trace FR-63
    @ParameterizedTest(name = "{0} with language {1} is stored as sent")
    @MethodSource("profiles")
    void everyShippedProfileIsAcceptedAndStoredAsSent(String profileId, String language) throws Exception {
        String sid = connectedSid();
        String styleRaw = style(modeOf(profileId), profileId);
        String languageRaw = "\"" + language + "\"";
        Map<String, Object> run = startOk(sid, bodyWith(styleRaw, languageRaw));
        assertThat(run.get("status")).isEqualTo("QUEUED");
        assertStoredEverywhere(sid, run, expected(styleRaw, languageRaw));
        assertThat(outputOf(run).get("writingStyle")).isEqualTo(Map.of("mode", modeOf(profileId), "profileId", profileId));
    }

    // Ranges & invariants: en and uk are stored as sent.
    // @trace FR-64
    @ParameterizedTest(name = "language {0} is stored as sent")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"en", "uk"})
    void aValidLanguageIsStoredAsSent(String language) throws Exception {
        String sid = connectedSid();
        Map<String, Object> run = startOk(sid, bodyWith(null, "\"" + language + "\""));
        assertThat(outputOf(run)).isEqualTo(Map.of("story", true, "illustration", false, "language", language));
        assertStoredEverywhere(sid, run, expected(null, "\"" + language + "\""));
        assertThat(outputOf(run).containsKey("writingStyle")).isFalse();
    }

    // @trace FR-63, FR-64
    @Test
    void absentStyleAndLanguageStayAbsentInTheStoredConfiguration() throws Exception {
        String sid = connectedSid();
        Map<String, Object> run = startOk(sid, B);
        assertThat(run.get("configuration")).isEqualTo(json(B));
        assertThat(outputOf(run)).isEqualTo(Map.of("story", true, "illustration", false));
        assertStoredEverywhere(sid, run, Map.of("story", true, "illustration", false));
    }

    // @trace FR-63, FR-64
    @Test
    void nullStyleAndLanguageAreAcceptedAndStoredAbsent() throws Exception {
        String sid = connectedSid();
        Map<String, Object> run = startOk(sid, bodyWith("null", "null"));
        assertThat(outputOf(run)).isEqualTo(Map.of("story", true, "illustration", false));
        assertThat(outputOf(run).containsKey("writingStyle")).isFalse();
        assertThat(outputOf(run).containsKey("language")).isFalse();
        assertStoredEverywhere(sid, run, Map.of("story", true, "illustration", false));
        assertThat(storedOutputText(run)).doesNotContain("null");
    }

    private String storedOutputText(Map<String, Object> run) {
        return jdbc.queryForObject("select cast(configuration -> 'output' as text) from generation_run where id = cast(? as uuid)",
            String.class, (String) run.get("id"));
    }

    // @trace FR-63
    @Test
    void unknownKeysInsideTheStyleAreIgnoredAndNotStored() throws Exception {
        String sid = connectedSid();
        Map<String, Object> run = startOk(sid, bodyWith("{\"mode\":\"NARRATIVE\",\"profileId\":\"kafka\",\"foo\":1,\"bar\":{}}", "\"uk\""));
        Map<String, Object> out = Map.of("story", true, "illustration", false,
            "writingStyle", Map.of("mode", "NARRATIVE", "profileId", "kafka"), "language", "uk");
        assertThat(outputOf(run)).isEqualTo(out);
        assertStoredEverywhere(sid, run, out);
        assertThat(storedOutputText(run)).doesNotContain("foo").doesNotContain("bar");
    }

    // @trace FR-63
    @Test
    void unknownKeysOfOutputStayDropped() throws Exception {
        String sid = connectedSid();
        String body = bodyWith(style("NARRATIVE", "poe"), "\"en\"").replace("\"illustration\":false", "\"illustration\":false,\"foo\":1");
        Map<String, Object> run = startOk(sid, body);
        assertThat(outputOf(run).containsKey("foo")).isFalse();
        assertThat(outputOf(run).get("writingStyle")).isEqualTo(Map.of("mode", "NARRATIVE", "profileId", "poe"));
    }

    // @trace FR-63
    @Test
    void theNewestRunsChoiceIsWhatTheSessionConfigurationReturns() throws Exception {
        String sid = connectedSid();
        Map<String, Object> first = startOk(sid, bodyWith(style("NARRATIVE", "wells"), "\"uk\""));
        String firstId = (String) first.get("id");
        jdbc.update("update generation_run set status = 'FAILED' where id = cast(? as uuid)", firstId);
        Map<String, Object> second = startOk(sid, bodyWith(style("OFFICIAL", "official-briefing"), "\"en\""));
        assertThat(outputOfConfiguration(scenarioConfiguration(sid))).isEqualTo(outputOf(second));
        assertThat(outputOf(getRunBody(sid, firstId)).get("writingStyle"))
            .isEqualTo(Map.of("mode", "NARRATIVE", "profileId", "wells"));
    }

    private Map<String, Object> getRunBody(String sid, String id) throws Exception {
        return json(getRun(sid, id));
    }

    // @trace FR-63
    @Test
    void aFreshSessionStillGetsTheDefaultsWithoutStyleKeys() throws Exception {
        Map<String, Object> cfg = scenarioConfiguration(newSid());
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) cfg.get("output");
        assertThat(out).isEqualTo(Map.of("story", true, "illustration", false));
    }

    // ---- rejected: nothing stored ----

    static Stream<Arguments> rejectedStyles() {
        return Stream.of(
            Arguments.of("string", "\"kafka\""),
            Arguments.of("number", "5"),
            Arguments.of("empty array", "[]"),
            Arguments.of("empty object", "{}"),
            Arguments.of("mode POETIC", "{\"mode\":\"POETIC\",\"profileId\":\"kafka\"}"),
            Arguments.of("profileId null", "{\"mode\":\"NARRATIVE\",\"profileId\":null}"),
            Arguments.of("profileId Kafka", "{\"mode\":\"NARRATIVE\",\"profileId\":\"Kafka\"}"),
            Arguments.of("profile not in the file", "{\"mode\":\"NARRATIVE\",\"profileId\":\"dickens\"}"),
            Arguments.of("OFFICIAL with kafka", "{\"mode\":\"OFFICIAL\",\"profileId\":\"kafka\"}"),
            Arguments.of("NARRATIVE with official-briefing", "{\"mode\":\"NARRATIVE\",\"profileId\":\"official-briefing\"}"));
    }

    // @trace FR-63
    @ParameterizedTest(name = "rejected style {0}: 400 and no run stored")
    @MethodSource("rejectedStyles")
    void aRejectedStyleStoresNothing(String name, String styleRaw) throws Exception {
        String sid = connectedSid();
        int before = runCount();
        assertError(startRun(sid, bodyWith(styleRaw, null)), 400, "VALIDATION_FAILED", STYLE_MSG);
        assertThat(runCount()).isEqualTo(before);
        assertThat(outputOfConfiguration(scenarioConfiguration(sid))).isEqualTo(Map.of("story", true, "illustration", false));
    }

    static Stream<Arguments> rejectedLanguages() {
        return Stream.of(
            Arguments.of("EN", "\"EN\""),
            Arguments.of("ua", "\"ua\""),
            Arguments.of("de", "\"de\""),
            Arguments.of("empty", "\"\""),
            Arguments.of("number", "1"),
            Arguments.of("array", "[\"uk\"]"),
            Arguments.of("object", "{}"));
    }

    // @trace FR-64
    @ParameterizedTest(name = "rejected language {0}: 400 and no run stored")
    @MethodSource("rejectedLanguages")
    void aRejectedLanguageStoresNothing(String name, String languageRaw) throws Exception {
        String sid = connectedSid();
        int before = runCount();
        assertError(startRun(sid, bodyWith(null, languageRaw)), 400, "VALIDATION_FAILED", LANGUAGE_MSG);
        assertThat(runCount()).isEqualTo(before);
    }

    // @trace FR-63, FR-64
    @Test
    void aBadStyleAndALanguageTogetherReportTheStyleAndStoreNothing() throws Exception {
        String sid = connectedSid();
        int before = runCount();
        assertError(startRun(sid, bodyWith("{\"mode\":\"OFFICIAL\",\"profileId\":\"kafka\"}", "\"de\"")), 400,
            "VALIDATION_FAILED", STYLE_MSG);
        assertThat(runCount()).isEqualTo(before);
    }

    // ---- order against the connection and the active-run rule ----

    // @trace FR-63
    @Test
    void aBadStyleIsReportedBeforeTheActiveRunRule() throws Exception {
        String sid = connectedSid();
        startOk(sid, B);
        int before = runCount();
        assertError(startRun(sid, bodyWith("\"kafka\"", null)), 400, "VALIDATION_FAILED", STYLE_MSG);
        assertError(startRun(sid, bodyWith(null, "\"de\"")), 400, "VALIDATION_FAILED", LANGUAGE_MSG);
        assertThat(runCount()).isEqualTo(before);
    }

    // @trace FR-63, FR-64
    @Test
    void aValidStyleWhileARunIsActiveIsStill409() throws Exception {
        String sid = connectedSid();
        startOk(sid, B);
        int before = runCount();
        assertError(startRun(sid, bodyWith(style("NARRATIVE", "kafka"), "\"uk\"")), 409, "RUN_ALREADY_ACTIVE",
            "A generation is already running");
        assertThat(runCount()).isEqualTo(before);
    }

    // @trace FR-63
    @Test
    void aBadStyleWithoutAnyConnectionIs400Not401() throws Exception {
        int before = runCount();
        assertError(startRun(null, bodyWith("\"kafka\"", null)), 400, "VALIDATION_FAILED", STYLE_MSG);
        assertError(startRun(newSid(), bodyWith(null, "\"de\"")), 400, "VALIDATION_FAILED", LANGUAGE_MSG);
        assertThat(runCount()).isEqualTo(before);
    }

    // @trace FR-63
    @Test
    void aValidStyleWithoutAConnectionIs401AndStoresNothing() throws Exception {
        int before = runCount();
        assertError(startRun(newSid(), bodyWith(style("NARRATIVE", "kafka"), "\"uk\"")), 401, "CHATGPT_NOT_CONNECTED",
            "Connect ChatGPT to generate");
        assertThat(runCount()).isEqualTo(before);
    }

    // @trace FR-63
    @Test
    void theShippedFileDoesNotKnowDickens() throws Exception {
        String sid = connectedSid();
        assertError(startRun(sid, bodyWith(style("NARRATIVE", "dickens"), null)), 400, "VALIDATION_FAILED", STYLE_MSG);
    }
}
