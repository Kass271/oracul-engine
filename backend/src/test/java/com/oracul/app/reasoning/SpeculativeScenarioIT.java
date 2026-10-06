package com.oracul.app.reasoning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.oracul.app.research.StubNews;
import com.oracul.app.research.StubResponses;
import com.oracul.app.result.AbstractStoryIT;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * run-control.md FR-47 speculative mode (replaces ReasoningPipeline.Result.EMPTY_PACK): an empty Evidence Pack is written up
 * fully speculatively. The TASK line, the Evidence Guard differences and the invariant "an accepted speculative scenario
 * never cites evidence", all through HTTP and scripted SCENARIO_GENERATION answers.
 */
// @trace FR-47
class SpeculativeScenarioIT extends AbstractStoryIT {

    static final String SPECULATIVE_TASK = "The Evidence Pack is empty: no current news could be used. Write a fully speculative "
        + "scenario: factsUsed, inferences and counterSignalsConsidered are empty arrays, the causal chain has only SPECULATION "
        + "steps followed by the single FUTURE_EVENT, and no Evidence ID appears anywhere.";
    static final String CITE_LINE_START = "Cite Evidence IDs exactly as written in the pack.";
    static final String CRITIC = "SCENARIO_CRITIC";

    // ---- scenario builders (empty pack: no Evidence ID exists) ---------------------------------------------------

    private static ObjectNode base(String input) {
        return ReasoningHarness.tree(ScenarioFixtures.scenarioDefault(input));
    }

    private static ObjectNode step(int order, String cls, String claim, String statement, Integer year) {
        ObjectNode s = MAPPER.createObjectNode();
        s.put("order", order);
        s.put("informationClass", cls);
        if (claim == null) s.putNull("claimId");
        else s.put("claimId", claim);
        s.put("statement", statement);
        s.set("evidenceIds", MAPPER.createArrayNode());
        if (year == null) s.putNull("year");
        else s.put("year", year);
        return s;
    }

    private static ObjectNode speculation(String id, String statement, String... basedOn) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("id", id);
        p.put("statement", statement);
        ArrayNode b = MAPPER.createArrayNode();
        for (String x : basedOn) b.add(x);
        p.set("basedOn", b);
        return p;
    }

    /** No facts, inferences or counter-signals; chain P1, P2, FUTURE_EVENT. */
    private static String fullySpeculative(String input) {
        ObjectNode n = base(input);
        int year = n.get("causalChain").get(3).get("year").asInt();
        n.set("factsUsed", MAPPER.createArrayNode());
        n.set("inferences", MAPPER.createArrayNode());
        n.set("counterSignalsConsidered", MAPPER.createArrayNode());
        ArrayNode p = MAPPER.createArrayNode();
        p.add(speculation("P1", "Ports might automate further."));
        p.add(speculation("P2", "Unrest might follow automation.", "P1"));
        n.set("speculations", p);
        ArrayNode chain = MAPPER.createArrayNode();
        chain.add(step(1, "SPECULATION", "P1", "Ports might automate further.", null));
        chain.add(step(2, "SPECULATION", "P2", "Unrest might follow automation.", null));
        chain.add(step(3, "FUTURE_EVENT", null, "Stub future event.", year));
        n.set("causalChain", chain);
        return MAPPER.writeValueAsString(n);
    }

    /** One fact citing E001 (unknown in an empty pack) and the future event: fewer than 2 steps remain after the removal. */
    private static String factAndFutureEventOnly(String input) {
        ObjectNode n = base(input);
        int year = n.get("causalChain").get(3).get("year").asInt();
        n.set("inferences", MAPPER.createArrayNode());
        n.set("speculations", MAPPER.createArrayNode());
        ArrayNode chain = MAPPER.createArrayNode();
        ObjectNode fact = step(1, "FACT", "F1", "Stub fact citing E001.", null);
        fact.set("evidenceIds", MAPPER.createArrayNode().add("E001"));
        chain.add(fact);
        chain.add(step(2, "FUTURE_EVENT", null, "Stub future event.", year));
        n.set("causalChain", chain);
        return MAPPER.writeValueAsString(n);
    }

    private static String futureEventOutsideTheWindow(String input) {
        ObjectNode n = ReasoningHarness.tree(fullySpeculative(input));
        ((ObjectNode) n.get("futureEvent")).put("date", "1999-01-01");
        ((ObjectNode) n.get("causalChain").get(2)).put("year", 1999);
        return MAPPER.writeValueAsString(n);
    }

    private static String futureEventNotLast(String input) {
        ObjectNode n = ReasoningHarness.tree(fullySpeculative(input));
        ArrayNode chain = (ArrayNode) n.get("causalChain");
        JsonNode fe = chain.get(2);
        JsonNode p2 = chain.get(1);
        ArrayNode swapped = MAPPER.createArrayNode();
        swapped.add(chain.get(0));
        ObjectNode feMoved = (ObjectNode) fe.deepCopy();
        feMoved.put("order", 2);
        ObjectNode p2Moved = (ObjectNode) p2.deepCopy();
        p2Moved.put("order", 3);
        swapped.add(feMoved);
        swapped.add(p2Moved);
        n.set("causalChain", swapped);
        return MAPPER.writeValueAsString(n);
    }

    private static List<String> classes(Map<String, Object> structuredScenario) {
        return list(structuredScenario.get("causalChain")).stream().map(s -> (String) s.get("informationClass")).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> scenarioOf(Map<String, Object> record) {
        return (Map<String, Object>) record.get("structuredScenario");
    }

    private void assertNoEvidenceIdAnywhere(String raw) {
        assertThat(raw).as("an accepted speculative scenario never cites evidence").doesNotContainPattern("\"E\\d{3}\"")
            .doesNotContainPattern("E\\d{3}[,.\\s]");
    }

    // ---- the TASK line -------------------------------------------------------------------------------------------

    static Stream<Arguments> emptyPackClasses() {
        return Stream.of(Arguments.of("no articles", "EMPTY"), Arguments.of("news 503 everywhere", "DOWN"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("emptyPackClasses")
    void anEmptyPackAddsTheSpeculativeTaskLineRightAfterTheCiteLine(String name, String mode) throws Exception {
        news.reset();
        if ("DOWN".equals(mode)) {
            news.responder = req -> StubNews.status(503);
        }
        Ran r = run(A);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(requests(GEN)).as("exactly one SCENARIO_GENERATION request").hasSize(1);
        StubResponses.Request req = requests(GEN).get(0);
        List<String> lines = List.of(req.inputText().split("\n", -1));
        int cite = -1;
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).startsWith(CITE_LINE_START)) cite = i;
        assertThat(cite).as("the Cite Evidence IDs line").isGreaterThan(0);
        assertThat(lines.get(cite + 1)).isEqualTo(SPECULATIVE_TASK);
        assertThat(lines.get(cite + 2)).isEqualTo("<<<ORACUL_UNTRUSTED_DATA name=\"evidence-pack\">>>");
        assertThat(StubResponses.dataBlock(req.inputText(), "evidence-pack"))
            .contains("CORE EVIDENCE\nnone\nSUPPORTING EVIDENCE\nnone\nCOUNTER-SIGNALS\nnone");
        assertThat(instructionsOf(req)).as("instructions are unchanged").isEqualTo(ScenarioFixtures.INSTRUCTIONS);
        assertThat(req.inputText().split(java.util.regex.Pattern.quote(SPECULATIVE_TASK), -1)).hasSize(2);
        assertThat(requests(CRITIC)).as("critic as in normal mode").hasSize(1);
        assertThat(requests(STORY)).hasSize(1);
    }

    @Test
    void aNonEmptyPackNeverGetsTheSpeculativeTaskLine() throws Exception {
        scriptScenario(fx("SC-V4"));
        Ran r = runV4(A);
        assertCompleted(r.run());
        assertThat(requests(GEN)).hasSize(1);
        assertThat(requests(GEN).get(0).inputText()).doesNotContain("The Evidence Pack is empty").doesNotContain(SPECULATIVE_TASK);
        assertThat(r.run().get("evidenceNote")).as("run: " + r.run()).isNull();
        assertThat(r.run().get("suggestedRealism")).isNull();
    }

    // ---- Evidence Guard in speculative mode -----------------------------------------------------------------------

    // SC-DEFAULT-like scenario (F1 citing E001, I1 based on F1, P1, FUTURE_EVENT) -> PASS_WITH_REMOVALS, chain [SPECULATION, FUTURE_EVENT]
    @Test
    void aScenarioThatCitesEvidenceLosesItInSpeculativeModeAndIsAccepted() throws Exception {
        Ran r = run(A); // the default responder answers SC-DEFAULT, which cites E001
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(requests(GEN)).hasSize(1);
        String raw = structuredRaw(r);
        Map<String, Object> rec = json(raw);
        assertThat(rec.get("accepted")).isEqualTo(true);
        List<Map<String, Object>> reports = list(rec.get("guardReports"));
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).get("outcome")).isEqualTo("PASS_WITH_REMOVALS");
        assertThat(classes(scenarioOf(rec))).containsExactly("SPECULATION", "FUTURE_EVENT");
        assertThat(list(scenarioOf(rec).get("causalChain")).stream().map(s -> s.get("order")).toList()).containsExactly(1, 2);
        assertThat(list(scenarioOf(rec).get("factsUsed"))).isEmpty();
        assertThat(list(scenarioOf(rec).get("inferences"))).isEmpty();
        assertThat(list(scenarioOf(rec).get("counterSignalsConsidered"))).isEmpty();
        assertThat(((Map<?, ?>) rec.get("structuredScenario")).get("futureEvent")).isNotNull();
        assertThat(counts(r.run()).get("sourcesUsed")).isEqualTo(0);
        assertThat(r.run().get("hasOpenCriticIssues")).isEqualTo(false);
    }

    // fully speculative scenario (no facts, chain P1, P2, FUTURE_EVENT) -> PASS, nothing removed
    @Test
    void aFullySpeculativeScenarioPassesWithoutRemovals() throws Exception {
        scriptScenario(computed(SpeculativeScenarioIT::fullySpeculative));
        Ran r = run(A);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(requests(GEN)).hasSize(1);
        String raw = structuredRaw(r);
        Map<String, Object> rec = json(raw);
        List<Map<String, Object>> reports = list(rec.get("guardReports"));
        assertThat(reports.get(0).get("outcome")).isEqualTo("PASS");
        assertThat(list(reports.get(0).get("violations"))).isEmpty();
        assertThat(classes(scenarioOf(rec))).containsExactly("SPECULATION", "SPECULATION", "FUTURE_EVENT");
        assertThat(rec.get("accepted")).isEqualTo(true);
        assertNoEvidenceIdAnywhere(raw);
        assertThat(list(result(r).get("sources"))).isEmpty();
        assertThat(map(result(r).get("research")).get("counts")).isEqualTo(counts(r.run()));
        assertThat(counts(r.run()).get("articlesConsidered")).isEqualTo(0);
    }

    static Stream<Arguments> failingShapes() {
        return Stream.of(
            Arguments.of("only FACT and FUTURE_EVENT: fewer than 2 steps remain", "fact-and-event"),
            Arguments.of("FUTURE_EVENT outside the window", "outside-window"),
            Arguments.of("FUTURE_EVENT not last", "not-last"));
    }

    private static String shaped(String shape, String input) {
        return switch (shape) {
            case "fact-and-event" -> factAndFutureEventOnly(input);
            case "outside-window" -> futureEventOutsideTheWindow(input);
            default -> futureEventNotLast(input);
        };
    }

    // a failing speculative scenario requests one regeneration; a good second answer is accepted
    @ParameterizedTest(name = "{0}")
    @MethodSource("failingShapes")
    void aFailingSpeculativeScenarioIsRegeneratedOnce(String name, String shape) throws Exception {
        scriptScenario(computed(input -> shaped(shape, input)), computed(SpeculativeScenarioIT::fullySpeculative));
        Ran r = run(A);
        assertThat(r.run().get("status")).as(name + ": " + r.run()).isEqualTo("COMPLETED");
        assertThat(requests(GEN)).hasSize(2);
        assertThat(attemptReasons(r.id())).containsExactly("INITIAL", "GUARD_REGENERATION");
        assertThat(requests(GEN).get(1).inputText()).contains("Reason: GUARD_REGENERATION").contains(SPECULATIVE_TASK);
        Map<String, Object> rec = structured(r);
        List<Map<String, Object>> reports = list(rec.get("guardReports"));
        assertThat(reports).extracting(x -> x.get("outcome")).containsExactly("FAIL", "PASS");
        assertThat(rec.get("attempt")).isEqualTo(2);
        assertThat(rec.get("accepted")).isEqualTo(true);
    }

    // the guard failing twice in speculative mode rejects the scenario (unchanged rule)
    @ParameterizedTest(name = "twice: {0}")
    @MethodSource("failingShapes")
    void aSpeculativeScenarioThatFailsTheGuardTwiceIsRejected(String name, String shape) throws Exception {
        scriptScenario(computed(input -> shaped(shape, input)), computed(input -> shaped(shape, input)));
        Ran r = run(A);
        assertFailed(r.run(), "SCENARIO_REJECTED", REJECTED, "CONSTRUCTING_SCENARIO", 9);
        assertThat(requests(GEN)).hasSize(2);
        assertThat(requests(CRITIC)).isEmpty();
        assertThat(requests(STORY)).isEmpty();
        assertThat(structured(r).get("accepted")).isEqualTo(false);
    }

    // ---- ALTERNATIVE runs ---------------------------------------------------------------------------------------

    @Test
    void anAlternativeOfANoEvidenceRunCopiesTheNoteAndRunsSpeculatively() throws Exception {
        Ran p = run(A);
        assertThat(p.run().get("status")).isEqualTo("COMPLETED");
        assertThat(noteKind(p.run())).isEqualTo("NO_EVIDENCE");
        int genBefore = requests(GEN).size();
        MvcResult res = mvc.perform(post("/api/runs/" + p.id() + "/alternatives").cookie(new Cookie("ORACUL_SID", p.sid()))).andReturn();
        assertThat(res.getResponse().getStatus()).as(res.getResponse().getContentAsString()).isEqualTo(202);
        Map<String, Object> created = json(res.getResponse().getContentAsString());
        assertThat(created.get("evidenceNote")).isEqualTo(p.run().get("evidenceNote"));
        assertThat(created.get("suggestedRealism")).isNull();
        Map<String, Object> done = awaitRun(p.sid(), (String) created.get("id"), 15_000,
            m -> !"QUEUED".equals(m.get("status")) && !"RUNNING".equals(m.get("status")));
        assertThat(done.get("status")).as("alternative: " + done).isEqualTo("COMPLETED");
        assertThat(done.get("evidenceNote")).isEqualTo(p.run().get("evidenceNote"));
        List<StubResponses.Request> gen = requests(GEN);
        assertThat(gen.size()).isGreaterThan(genBefore);
        assertThat(gen.get(gen.size() - 1).inputText()).as("the alternative runs in the same mode").contains(SPECULATIVE_TASK)
            .contains("futures-to-avoid");
        assertThat(list(result(new Ran(p.sid(), (String) created.get("id"), done)).get("sources"))).isEmpty();
    }
}
