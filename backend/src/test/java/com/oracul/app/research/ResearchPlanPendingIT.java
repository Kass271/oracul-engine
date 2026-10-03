package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.runs.AbstractRunIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Row 7: the plan is stored before searching, observable while the stage stays RESEARCH_STRATEGY. */
// @trace FR-12
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
        List<Map<String, Object>> queries = (List<Map<String, Object>>) plan.get("queries");
        assertThat(queries).hasSize(20);
        for (Map<String, Object> q : queries) {
            assertThat(q.get("status")).isEqualTo("PENDING");
            assertThat(q.get("articlesReturned")).isEqualTo(0);
        }
        assertThat(json(getSources(sid, id))).isEqualTo(json("{\"items\":[]}"));
        assertThat(gdelt.requests).as("searching has not started").isEmpty();
        assertThat(research.get("counts")).isEqualTo(json(ZERO_COUNTS));
    }
}
