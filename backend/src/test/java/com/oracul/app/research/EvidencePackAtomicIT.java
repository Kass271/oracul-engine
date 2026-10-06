package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Row #13 of research-pipeline.md "Slice 07_evidence-pack": while stage RANKING runs the pack is either 409 or complete;
 * once the run exposes evidencePackId the counts and the pack agree.
 */
// @trace FR-18
@TestPropertySource(properties = "oracul.run.min-stage-duration=PT2S")
class EvidencePackAtomicIT extends AbstractEvidenceIT {

    @Test
    void packCountsAndRunAreCommittedTogether() throws Exception {
        news.reset();
        newsArticles(v4());
        script(NORMALIZATION, N_V4);
        script(CLASSIFICATION, C_V4);
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");

        long end = System.currentTimeMillis() + 40_000;
        boolean seenRanking = false;
        Map<String, Object> run = Map.of();
        while (System.currentTimeMillis() < end) {
            run = json(getRun(sid, id).andReturn().getResponse().getContentAsString());
            MvcResult packResult = getPack(sid, id).andReturn();
            int status = packResult.getResponse().getStatus();
            String body = packResult.getResponse().getContentAsString();
            if ("RANKING".equals(run.get("stage"))) seenRanking = true;
            if (status == 200) {
                // a complete pack: the run row is committed in the same transaction
                Map<String, Object> after = json(getRun(sid, id).andReturn().getResponse().getContentAsString());
                assertThat(after.get("evidencePackId")).as("pack visible => run points at it").isEqualTo(JsonPath.read(body, "$.id"));
                int items = ((List<?>) JsonPath.read(body, "$.core")).size() + ((List<?>) JsonPath.read(body, "$.supporting")).size()
                    + ((List<?>) JsonPath.read(body, "$.counterSignals")).size();
                assertThat(counts(after).get("eventsSelected")).isEqualTo(items);
                assertThat(counts(after).get("counterSignals")).isEqualTo(((List<?>) JsonPath.read(body, "$.counterSignals")).size());
                run = after;
                break;
            }
            assertThat(status).as("not complete => 409: " + body).isEqualTo(409);
            if (run.get("evidencePackId") != null) {
                // the run row was committed after our pack read: the pack must exist now
                assertThat(getPack(sid, id).andReturn().getResponse().getStatus()).isEqualTo(200);
            }
            Thread.sleep(200);
        }
        assertThat(run.get("evidencePackId")).as("run exposed evidencePackId in time; last=" + run).isNotNull();
        assertThat(seenRanking).as("the run was observed in stage RANKING").isTrue();
        Map<String, Object> done = awaitRun(sid, id, 30_000, m -> "COMPLETED".equals(m.get("status")));
        assertThat(counts(done).get("eventsSelected")).isEqualTo(2);
    }
}
