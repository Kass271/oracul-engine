package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.runs.AbstractRunIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/** phase-02 news-search.md FR-44: no request starts at or after the search-budget end; a budget of 0 sends nothing. */
// @trace FR-44
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=10",
    "oracul.news.search-budget=PT0S",
})
class NewsSearchNoBudgetIT extends AbstractRunIT {

    @Test
    @SuppressWarnings("unchecked")
    void aBudgetOfZeroSendsNoRequestAndEndsWithNewsUnavailable(CapturedOutput out) throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).isEqualTo("FAILED");
        assertThat(run.get("failure")).isEqualTo(json(
            "{\"code\":\"NEWS_UNAVAILABLE\",\"message\":\"ORACUL could not reach its news sources — try again later\"}"));
        assertThat(run.get("stage")).isEqualTo("SEARCHING");
        assertThat(gdelt.requests).isEmpty();
        List<Map<String, Object>> queries =
            (List<Map<String, Object>>) ((Map<String, Object>) researchBody(sid, id).get("searchPlan")).get("queries");
        assertThat(queries).hasSize(20).allSatisfy(q -> assertThat(q.get("status")).isEqualTo("FAILED"));
        assertThat(responses.requests).as("expansion only").hasSize(1);
        assertThat(out.getOut() + out.getErr()).contains("news request skipped: search budget exhausted").doesNotContain("stub query");
    }
}
