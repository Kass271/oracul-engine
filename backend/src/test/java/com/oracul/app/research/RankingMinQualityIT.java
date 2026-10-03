package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Row #5 of research-pipeline.md "Slice 07_evidence-pack": events below oracul.ranking.min-source-quality are never selected. */
// @trace FR-16
@TestPropertySource(properties = "oracul.ranking.min-source-quality=0.9")
class RankingMinQualityIT extends AbstractEvidenceIT {

    // #5
    @Test
    void anEventBelowTheMinimumSourceQualityIsRankedButNeverSelected() throws Exception {
        Ran r = runV4(A);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        List<Map<String, Object>> events = events(r);
        Map<String, Object> ev2 = event(events, "EV002");
        assertThat(ev2).containsKey("ranking");
        assertThat(ev2.get("excludedReason")).isEqualTo("LOW_SOURCE_QUALITY");
        assertThat(ev2).doesNotContainKey("selection");
        assertThat(event(events, "EV001")).doesNotContainKey("excludedReason");

        Map<String, Object> pack = pack(r);
        assertThat(section(pack, "core")).isEmpty();
        assertThat(section(pack, "supporting")).isEmpty();
        assertThat(section(pack, "counterSignals")).hasSize(1);
        assertThat(section(pack, "counterSignals").get(0)).containsEntry("eventId", "EV001").containsEntry("evidenceId", "E001");
        assertThat(counts(r.run()).get("eventsSelected")).isEqualTo(1);
        assertThat(counts(r.run()).get("counterSignals")).isEqualTo(1);
    }
}
