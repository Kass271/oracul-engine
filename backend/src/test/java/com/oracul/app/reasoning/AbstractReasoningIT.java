package com.oracul.app.reasoning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import com.oracul.app.research.AbstractEvidenceIT;
import com.oracul.app.research.StubResponses;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared driver of the slice 08 tests (scenario-reasoning.md "Slice 08_validated-scenario"): scripted SCENARIO_GENERATION
 * answers keyed by the request number of that purpose, getStructuredScenario, scenario_attempt / model_call inspection.
 */
public abstract class AbstractReasoningIT extends AbstractEvidenceIT {

    protected static final String GEN = "SCENARIO_GENERATION";
    protected static final String PROVIDER_BODY = "{\"error\":\"PROVIDER-SECRET-BODY\"}";
    protected static final String INVALID = "ORACUL could not construct a valid scenario";
    protected static final String REJECTED = "ORACUL could not construct a scenario supported by current evidence";
    protected static final String NOT_READY = "The scenario is not ready yet";
    protected static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** One scripted answer, computed from the request (D of the future date depends on the run's cutoff). */
    protected interface Answer extends Function<StubResponses.Request, StubResponses.Reply> {}

    /** SC-V4 / SC-E099 / SC-NOEV / SC-BAD with D taken from the request. */
    protected static Answer fx(String name) {
        return req -> StubResponses.completed(StubResponses.scenarioFixture(name, req.inputText()));
    }

    protected static Answer text(String outputText) {
        return req -> StubResponses.completed(outputText);
    }

    protected static Answer reply(StubResponses.Reply r) {
        return req -> r;
    }

    protected static Answer computed(Function<String, String> outputTextOfInput) {
        return req -> StubResponses.completed(outputTextOfInput.apply(req.inputText()));
    }

    /** The 1st, 2nd... SCENARIO_GENERATION request is answered by these; later ones get SC-DEFAULT. */
    protected void scriptScenario(Answer... answers) {
        AtomicInteger n = new AtomicInteger();
        Function<StubResponses.Request, StubResponses.Reply> fallback = responses.defaultResponder();
        always(GEN, req -> {
            int k = n.getAndIncrement();
            return k < answers.length ? answers[k].apply(req) : fallback.apply(req);
        });
    }

    protected ResultActions getStructured(String sid, String runId) throws Exception {
        var b = get("/api/runs/" + runId + "/structured-scenario");
        if (sid != null) b.cookie(new Cookie("ORACUL_SID", sid));
        return mvc.perform(b);
    }

    /** Raw body of getStructuredScenario (200 expected). */
    protected String structuredRaw(Ran r) throws Exception {
        MvcResult res = getStructured(r.sid(), r.id()).andReturn();
        assertThat(res.getResponse().getStatus()).as("getStructuredScenario: " + res.getResponse().getContentAsString()).isEqualTo(200);
        return res.getResponse().getContentAsString();
    }

    protected Map<String, Object> structured(Ran r) throws Exception {
        return json(structuredRaw(r));
    }

    protected static void assertScenarioNotReady(ResultActions r) throws Exception {
        assertError(r, 409, "SCENARIO_NOT_READY", NOT_READY);
    }

    protected static void assertRunNotFound(ResultActions r) throws Exception {
        assertError(r, 404, "RUN_NOT_FOUND", "Future not found");
    }

    protected int attemptRows(String runId) {
        return jdbc.queryForObject("select count(*) from scenario_attempt where run_id = cast(? as uuid)", Integer.class, runId);
    }

    protected List<String> attemptReasons(String runId) {
        return jdbc.queryForList("select reason from scenario_attempt where run_id = cast(? as uuid) order by attempt", String.class, runId);
    }

    protected Integer finalAttempt(String runId) {
        return jdbc.queryForObject("select final_attempt from generation_run where id = cast(? as uuid)", Integer.class, runId);
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> list(Object o) {
        return (List<Map<String, Object>>) o;
    }

    protected static Object jsonOf(String raw) {
        return JsonPath.read(raw, "$");
    }

    /** The structuredScenario of a record in its comparable form (nulls and an empty unknowns dropped). */
    protected static JsonNode scenarioOf(String recordRaw) {
        return ReasoningHarness.comparable(MAPPER.readTree(recordRaw).get("structuredScenario").toString());
    }

    protected static List<String> claimIds(Map<String, Object> structuredScenario) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> step : list(structuredScenario.get("causalChain"))) out.add((String) step.get("claimId"));
        return out;
    }

    /** Date part (yyyy-MM-dd) of the cutoff of the run's Evidence Pack. */
    protected String cutoffDate(Ran r) throws Exception {
        return utcDate(pack(r).get("cutoff"));
    }

    /** Names of every JSON key at any depth. */
    protected static List<String> keysOf(String json) {
        List<String> out = new ArrayList<>();
        collect(MAPPER.readTree(json), out);
        return out;
    }

    private static void collect(JsonNode n, List<String> out) {
        if (n.isObject()) {
            for (Map.Entry<String, JsonNode> e : n.properties()) {
                out.add(e.getKey());
                collect(e.getValue(), out);
            }
        } else if (n.isArray()) {
            for (JsonNode c : n) collect(c, out);
        }
    }

    protected static void assertNoToolKeys(StubResponses.Request req) {
        for (String k : keysOf(req.body())) {
            assertThat(k).as("key in request body").isNotEqualTo("tools").isNotEqualTo("tool_choice").doesNotStartWith("web_search");
        }
    }

    protected static void assertFailed(Map<String, Object> run, String code, String message, String stage, int stageIndex) {
        assertThat(run.get("status")).as("run: " + run).isEqualTo("FAILED");
        assertThat(run.get("failure")).isEqualTo(json("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}"));
        assertThat(run.get("stage")).isEqualTo(stage);
        assertThat(run.get("stageIndex")).isEqualTo(stageIndex);
        assertThat(run.get("completedAt")).isNotNull();
    }

    protected static void assertCompleted(Map<String, Object> run) {
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(run.get("stageIndex")).isEqualTo(10);
        assertThat(absent(run, "headline")).isTrue();
    }
}
