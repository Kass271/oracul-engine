package com.oracul.app.reasoning;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.oracul.app.research.StubGdelt;
import com.oracul.app.research.StubResponses;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ArrayNode;

/** Rows #5-#7, #13, #16, #17 of scenario-reasoning.md "Slice 08_validated-scenario": structured output and getStructuredScenario. */
// @trace FR-20
class StructuredScenarioIT extends AbstractReasoningIT {

    // #5
    @Test
    void aValidScenarioIsStoredAcceptedAndServed() throws Exception {
        scriptScenario(fx("SC-V4"));
        Ran r = runV4(A);
        assertCompleted(r.run());
        String raw = structuredRaw(r);
        Map<String, Object> rec = json(raw);
        assertThat(rec.keySet()).containsExactlyInAnyOrder("runId", "evidencePackId", "attempt", "accepted", "structuredScenario",
            "guardReports", "criticReports", "generationAttempts", "attempts");
        assertThat(rec.get("runId")).isEqualTo(r.id());
        assertThat(rec.get("evidencePackId")).isEqualTo(r.run().get("evidencePackId"));
        assertThat(rec.get("attempt")).isEqualTo(1);
        assertThat(rec.get("accepted")).isEqualTo(true);
        String d = StubResponses.futureDate(requests(GEN).get(0).inputText());
        assertThat(scenarioOf(raw)).isEqualTo(ReasoningHarness.comparable(ScenarioFixtures.scV4(d)));
        assertThat(rec.get("guardReports")).isEqualTo(jsonOf("[{\"outcome\":\"PASS\",\"violations\":[],\"attempt\":1}]"));
        assertThat(rec.get("criticReports")).isEqualTo(List.of());
        assertThat(rec.get("generationAttempts")).isEqualTo(1);
        assertThat(rec.get("attempts")).isEqualTo(jsonOf("[{\"attempt\":1,\"reason\":\"INITIAL\",\"parsed\":true,\"schemaErrors\":[]}]"));
        assertThat(counts(r.run()).get("sourcesUsed")).isEqualTo(2);
        assertThat(attemptRows(r.id())).isEqualTo(1);
        assertThat(finalAttempt(r.id())).isEqualTo(1);
    }

    @Test
    void theRecordIsServedForTheRunOfTheSessionOnly() throws Exception {
        scriptScenario(fx("SC-V4"));
        Ran r = runV4(A);
        assertCompleted(r.run());
        assertThat(getStructured(r.sid(), r.id()).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(structuredRaw(r), "$.structuredScenario.futureEvent.title")).isEqualTo("Robots replace striking dock workers");
    }

    // #6
    @Test
    void anInvalidFirstAnswerGetsOneSchemaCorrection() throws Exception {
        scriptScenario(text("not json"), fx("SC-V4"));
        Ran r = runV4(A);
        assertCompleted(r.run());
        List<StubResponses.Request> gen = requests(GEN);
        assertThat(gen).hasSize(2);
        String t2 = gen.get(1).inputText();
        assertThat(t2).contains("Attempt: 2 | Reason: SCHEMA_CORRECTION");
        assertThat(t2).contains("Your previous answer was invalid. Fix the errors listed in schema-errors and return the complete scenario again.");
        assertThat(StubResponses.dataBlock(t2, "schema-errors")).isEqualTo("output is not valid JSON");
        assertThat(instructionsOf(gen.get(1))).isEqualTo(instructionsOf(gen.get(0)));
        Map<String, Object> rec = structured(r);
        assertThat(rec.get("attempt")).isEqualTo(2);
        assertThat(rec.get("accepted")).isEqualTo(true);
        assertThat(rec.get("generationAttempts")).isEqualTo(2);
        assertThat(rec.get("attempts")).isEqualTo(jsonOf("[{\"attempt\":1,\"reason\":\"INITIAL\",\"parsed\":false,"
            + "\"schemaErrors\":[\"output is not valid JSON\"]},{\"attempt\":2,\"reason\":\"SCHEMA_CORRECTION\",\"parsed\":true,\"schemaErrors\":[]}]"));
        assertThat(rec.get("guardReports")).isEqualTo(jsonOf("[{\"outcome\":\"PASS\",\"violations\":[],\"attempt\":2}]"));
        assertThat(finalAttempt(r.id())).isEqualTo(2);
        assertThat(attemptRows(r.id())).isEqualTo(2);
    }

    // #7
    @ParameterizedTest(name = "invalid twice: {0}")
    @ValueSource(strings = {"one-candidate", "empty-text", "incomplete", "unknown-key"})
    void anInvalidSecondAnswerFailsTheRunWithInvalidScenario(String kind) throws Exception {
        Answer bad = switch (kind) {
            case "one-candidate" -> computed(input -> {
                var n = ReasoningHarness.tree(ScenarioFixtures.scV4(StubResponses.futureDate(input)));
                ((ArrayNode) n.get("candidateFutures")).remove(1);
                return MAPPER.writeValueAsString(n);
            });
            case "empty-text" -> text("");
            case "incomplete" -> reply(new StubResponses.Reply(200, "{\"id\":\"resp_1\",\"status\":\"incomplete\",\"output\":[]}", 0));
            default -> computed(input -> {
                var n = ReasoningHarness.tree(ScenarioFixtures.scV4(StubResponses.futureDate(input)));
                n.set("tools", MAPPER.createArrayNode());
                return MAPPER.writeValueAsString(n);
            });
        };
        scriptScenario(bad, bad);
        Ran r = runV4(A);
        assertFailed(r.run(), "INVALID_SCENARIO", INVALID, "EXPLORING_FUTURES", 7);
        assertThat(requests(GEN)).hasSize(2);
        assertScenarioNotReady(getStructured(r.sid(), r.id()));
        assertThat(attemptRows(r.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from scenario_attempt where run_id = cast(? as uuid) and structured_scenario is null",
            Integer.class, r.id())).isEqualTo(2);
        assertThat(finalAttempt(r.id())).isNull();
        assertThat(counts(r.run()).get("sourcesUsed")).isEqualTo(0);
        int status = startRun(r.sid(), B).andReturn().getResponse().getStatus();
        assertThat(status).as("slot released").isEqualTo(202);
    }

    // #13
    @Test
    void aSecondSchemaCorrectionIsNeverMadeAfterAGuardRegeneration() throws Exception {
        scriptScenario(text("not json"), fx("SC-BAD"), text("not json"));
        Ran r = runV4(A);
        assertFailed(r.run(), "INVALID_SCENARIO", INVALID, "CONSTRUCTING_SCENARIO", 9);
        assertThat(requests(GEN)).as("no second SCHEMA_CORRECTION").hasSize(3);
        assertThat(attemptReasons(r.id())).containsExactly("INITIAL", "SCHEMA_CORRECTION", "GUARD_REGENERATION");
        assertThat(finalAttempt(r.id())).isNull();
    }

    // #16 (the pinned-run case is StructuredScenarioPendingIT)
    @Test
    void anEmptyPackCompletesWithoutAScenario() throws Exception {
        Ran r = run(A); // default GDELT {}: no sources, no events, empty pack
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        assertThat(requests(GEN)).isEmpty();
        assertThat(attemptRows(r.id())).isZero();
        assertScenarioNotReady(getStructured(r.sid(), r.id()));
    }

    // #16
    @Test
    void aRunWithoutNewsHasNoScenario() throws Exception {
        gdelt.responder = req -> StubGdelt.status(503);
        Ran r = run(A);
        assertThat(r.run().get("status")).isEqualTo("FAILED");
        assertThat(((Map<?, ?>) r.run().get("failure")).get("code")).isEqualTo("NEWS_UNAVAILABLE");
        assertScenarioNotReady(getStructured(r.sid(), r.id()));
    }

    // #17
    @ParameterizedTest(name = "unknown run [{0}]")
    @ValueSource(strings = {NO_RUN, "abc", "12345"})
    void anUnknownOrMalformedRunIs404(String id) throws Exception {
        String sid = connectedSid();
        assertRunNotFound(getStructured(sid, id));
    }

    // #17
    @Test
    void aRunOfAnotherSessionIs404() throws Exception {
        scriptScenario(fx("SC-V4"));
        Ran r = runV4(A);
        assertCompleted(r.run());
        String other = connectedSid();
        assertRunNotFound(getStructured(other, r.id()));
        assertThat(getStructured(r.sid(), r.id()).andReturn().getResponse().getStatus()).isEqualTo(200);
    }
}
