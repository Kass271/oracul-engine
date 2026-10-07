package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** FR-51 range (e), concurrency 1: one QUERY_GENERATION call open at a time, the calls arrive in pipeline order W01 ... W09. */
// @trace FR-51
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=8",
    "oracul.search.query-generation-concurrency=1",
})
class QueryGenerationSerialIT extends AbstractEventIT {

    @Test
    void withConcurrencyOneTheNineCallsRunOneAtATimeInPipelineOrder() throws Exception {
        responses.delay(QUERY_GENERATION, Duration.ofMillis(100)); // an overlap would show in maxInFlight
        Ran r = run(F240_BODY);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        List<String> order = requests(QUERY_GENERATION).stream().map(q -> StubResponses.pipelineOf(q.inputText())).toList();
        assertThat(order).as("started and arriving in pipeline order").containsExactly("W01", "W02", "W03", "W04", "W05", "W06", "W07",
            "W08", "W09");
        assertThat(responses.maxInFlight(QUERY_GENERATION)).isEqualTo(1);
        assertThat(PlanJson.pipelines(researchBody(r.sid(), r.id()))).hasSize(9)
            .allSatisfy(p -> assertThat(p.get("queryMode")).isEqualTo("MODEL"));
    }
}
