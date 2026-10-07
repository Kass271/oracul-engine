package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Wildcard-search.md FR-51 range (e), concurrency 4 (the default of {@code oracul.search.query-generation-concurrency}): nine
 * wildcards give nine QUERY_GENERATION calls, started in pipeline order, never more than four open at once.
 */
// @trace FR-51
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=8",
})
class QueryGenerationParallelIT extends AbstractEventIT {

    private static String pipelineOf(StubResponses.Request r) {
        return StubResponses.pipelineOf(r.inputText());
    }

    @Test
    void nineWildcardsAndEveryAnswerHeldHaveExactlyFourCallsOpenAndTheRestWaiting() throws Exception {
        responses.gate(QUERY_GENERATION);
        String sid = connectedSid();
        String id = (String) startOk(sid, F240_BODY).get("id");
        assertThat(responses.awaitArrived(QUERY_GENERATION, 4, Duration.ofSeconds(15))).as("four calls are open").isTrue();
        // the fifth call may only start when one of the four ends: while they are all held no more arrive
        long end = System.currentTimeMillis() + 700;
        while (System.currentTimeMillis() < end) {
            assertThat(responses.arrivedCount(QUERY_GENERATION)).as("never more than 4 open at once").isEqualTo(4);
            Thread.sleep(50);
        }
        assertThat(responses.maxInFlight(QUERY_GENERATION)).isEqualTo(4);
        // started in pipeline order: the first four calls are the calls of W01 ... W04
        assertThat(requests(QUERY_GENERATION).stream().map(QueryGenerationParallelIT::pipelineOf).collect(Collectors.toSet()))
            .containsExactlyInAnyOrder("W01", "W02", "W03", "W04");

        responses.release(QUERY_GENERATION);
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        List<StubResponses.Request> all = requests(QUERY_GENERATION);
        assertThat(all).as("exactly one request per pipeline: 9 in total").hasSize(9);
        assertThat(all.stream().map(QueryGenerationParallelIT::pipelineOf).toList())
            .containsExactlyInAnyOrder("W01", "W02", "W03", "W04", "W05", "W06", "W07", "W08", "W09");
        assertThat(responses.maxInFlight(QUERY_GENERATION)).as("never more than 4 open at once").isEqualTo(4);
        assertThat(all).allSatisfy(r -> assertThat(r.inputText()).contains("Queries: 2"));

        Map<String, Object> research = researchBody(sid, id);
        assertThat(PlanJson.plan(research).get("queryBudget")).isEqualTo(18);
        assertThat(PlanJson.plan(research).get("expansionMode")).isEqualTo("MODEL");
        assertThat(PlanJson.pipelines(research)).hasSize(9).allSatisfy(p -> {
            assertThat(p.get("queryMode")).isEqualTo("MODEL");
            assertThat(PlanJson.queriesOf(p)).hasSize(2);
        });
        assertThat(PlanJson.queries(research)).extracting(q -> q.get("id")).containsExactly(
            "Q01", "Q02", "Q03", "Q04", "Q05", "Q06", "Q07", "Q08", "Q09", "Q10", "Q11", "Q12", "Q13", "Q14", "Q15", "Q16", "Q17", "Q18");
    }

    @Test
    void theSameNineCallsAreAnsweredByTheirOwnPipelineWhateverTheAnswerOrder() throws Exception {
        // later pipelines answer first: the plan still keeps pipeline order and each pipeline its own texts
        responses.responder = req -> {
            Function<StubResponses.Request, StubResponses.Reply> fallback = responses.defaultResponder();
            if (!QUERY_GENERATION.equals(StubResponses.purpose(req))) return fallback.apply(req);
            int w = Integer.parseInt(StubResponses.pipelineOf(req.inputText()).substring(1));
            return StubResponses.delayed(StubResponses.completed(StubResponses.defaultGeneration(req.inputText())), (10 - w) * 40L);
        };
        Ran r = run(F240_BODY);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        List<Map<String, Object>> pipelines = PlanJson.pipelines(researchBody(r.sid(), r.id()));
        assertThat(pipelines).hasSize(9);
        for (int i = 0; i < 9; i++) {
            String w = String.format("W%02d", i + 1);
            assertThat(pipelines.get(i).get("id")).isEqualTo(w);
            assertThat(PlanJson.queriesOf(pipelines.get(i)).stream().map(q -> q.get("text")).toList())
                .containsExactly(w + " stub query 1", w + " stub query 2");
        }
    }

}
