package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.result.AbstractStoryIT;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** generation-runs.md "Slice 17_alternative-future" AlternativeRunIT row 2: stage progression of an alternative run. */
// @trace FR-30
@TestPropertySource(properties = "oracul.run.min-stage-duration=PT2S")
class AlternativeRunStagesIT extends AbstractStoryIT {

    @Override
    protected boolean awaitRunsAfterEach() {
        return true;
    }

    @Test
    void anAlternativeRunShowsOnlyTheStages7To10() throws Exception {
        news.reset();
        newsArticles(v4());
        script(NORMALIZATION, N_V4);
        script(CLASSIFICATION, C_V4);
        String sid0 = connectedSid();
        String pid = (String) startOk(sid0, A).get("id");
        Ran p = new Ran(sid0, pid, awaitRun(sid0, pid, 90_000, m -> !"QUEUED".equals(m.get("status")) && !"RUNNING".equals(m.get("status"))));
        assertThat(p.run().get("status")).isEqualTo("COMPLETED");
        var post = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/runs/" + p.id() + "/alternatives")
            .cookie(new jakarta.servlet.http.Cookie("ORACUL_SID", p.sid()));
        var res = mvc.perform(post).andReturn().getResponse();
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(202);
        String id = (String) json(res.getContentAsString()).get("id");
        List<Integer> seen = new ArrayList<>();
        long end = System.currentTimeMillis() + 40_000;
        Map<String, Object> last = Map.of();
        while (System.currentTimeMillis() < end) {
            var r = getRun(p.sid(), id).andReturn();
            if (r.getResponse().getStatus() == 200) {
                last = json(r.getResponse().getContentAsString());
                seen.add(((Number) last.get("stageIndex")).intValue());
                if (!"QUEUED".equals(last.get("status")) && !"RUNNING".equals(last.get("status"))) break;
            }
            Thread.sleep(200);
        }
        assertThat(last.get("status")).isEqualTo("COMPLETED");
        assertThat(seen).isSubsetOf(0, 7, 8, 9, 10);
        assertThat(seen).isSorted();
        assertThat(seen).contains(7);
    }
}
