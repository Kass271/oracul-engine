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
 * once the run exposes evidencePackId the counts and the pack agree (wildcard-evidence.md slice 06: eventsSelected = the
 * distinct Evidence IDs of the wildcard sections, counterSignals 0, final eventsSelected 4).
 */
// @trace FR-18, FR-57
@TestPropertySource(properties = "oracul.run.min-stage-duration=PT2S")
class EvidencePackAtomicIT extends AbstractEvidenceIT {

    @Test
    void packCountsAndRunAreCommittedTogether() throws Exception {
        news.reset();
        newsArticlesPerPipeline(v4(), 4);
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
                // wildcard-evidence.md slice 06: eventsSelected = the distinct Evidence IDs of the sections, counterSignals = 0
                List<?> ids = JsonPath.read(body, "$.wildcardSections[*].items[*].evidenceId");
                assertThat(counts(after).get("eventsSelected")).isEqualTo((int) ids.stream().distinct().count());
                assertThat(counts(after).get("counterSignals")).isEqualTo(0);
                assertThat(((List<?>) JsonPath.read(body, "$.core"))).isEmpty();
                assertThat(((List<?>) JsonPath.read(body, "$.counterSignals"))).isEmpty();
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
        assertThat(counts(done).get("eventsSelected")).isEqualTo(4);
        assertThat(counts(done).get("counterSignals")).isEqualTo(0);
    }
}
