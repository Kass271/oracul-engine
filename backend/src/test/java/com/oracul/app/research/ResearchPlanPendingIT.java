package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.runs.AbstractRunIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** FR-50 step 5: the pipeline plan is committed before any Google request, observable while the stage stays RESEARCH_STRATEGY. */
// @trace FR-12, FR-50
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT1H",
    "oracul.run.min-stage-duration=PT1H",
    "oracul.run.executor-threads=33",
})
class ResearchPlanPendingIT extends AbstractRunIT {

    @Override
    protected boolean awaitRunsAfterEach() {
        return false; // runs are kept pending on purpose
    }

    @Test
    @SuppressWarnings("unchecked")
    void planIsStoredBeforeSearchingWithEveryQueryPending() throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> research = awaitPlan(sid, id, 5000);
        Map<String, Object> run = json(getRun(sid, id));
        assertThat(run.get("stage")).isEqualTo("RESEARCH_STRATEGY");
        assertThat(run.get("status")).isEqualTo("RUNNING");
        Map<String, Object> plan = (Map<String, Object>) research.get("searchPlan");
        assertThat(plan.get("queries")).as("the queries live in the pipelines").isEqualTo(List.of());
        assertThat(plan.get("intents")).isEqualTo(List.of());
        assertThat(plan.get("buckets")).isEqualTo(List.of());
        assertThat(plan.get("queryBudget")).isEqualTo(6);
        List<Map<String, Object>> pipelines = PlanJson.pipelines(research);
        assertThat(pipelines).as("body A: two pipelines").hasSize(2);
        for (Map<String, Object> p : pipelines) {
            assertThat(PlanJson.queriesOf(p)).as("3 queries per pipeline").hasSize(3);
            for (Map<String, Object> q : PlanJson.queriesOf(p)) {
                assertThat(q.get("status")).isEqualTo("PENDING");
                assertThat(q.get("articlesReturned")).isEqualTo(0);
            }
            assertThat(p).doesNotContainKey("candidatesConsidered").doesNotContainKey("sourceIds");
        }
        assertThat(json(getSources(sid, id))).isEqualTo(json("{\"items\":[]}"));
        assertThat(news.requests).as("searching has not started").isEmpty();
        assertThat(research.get("counts")).isEqualTo(json(ZERO_COUNTS));
    }
}
