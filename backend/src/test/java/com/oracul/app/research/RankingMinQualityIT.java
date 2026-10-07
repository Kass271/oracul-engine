package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Row #5 of research-pipeline.md "Slice 07_evidence-pack": events below oracul.ranking.min-source-quality are ranked but
 * excluded. wildcard-evidence.md slice 06 (FR-57): the pack no longer depends on it - every kept source is in its section.
 */
// @trace FR-16, FR-57
@TestPropertySource(properties = "oracul.ranking.min-source-quality=0.9")
class RankingMinQualityIT extends AbstractEvidenceIT {

    // #5
    @Test
    void anEventBelowTheMinimumSourceQualityIsRankedAndExcludedButItsSourcesStayInThePack() throws Exception {
        Ran r = runV4(A);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        List<Map<String, Object>> events = events(r);
        Map<String, Object> ev2 = event(events, "EV002");
        assertThat(ev2).containsKey("ranking");
        assertThat(ev2.get("excludedReason")).isEqualTo("LOW_SOURCE_QUALITY");
        assertThat(ev2).doesNotContainKey("selection");
        assertThat(event(events, "EV001")).doesNotContainKey("excludedReason").doesNotContainKey("selection");

        Map<String, Object> pack = pack(r);
        assertThat(section(pack, "core")).isEmpty();
        assertThat(section(pack, "supporting")).isEmpty();
        assertThat(section(pack, "counterSignals")).isEmpty();
        List<Map<String, Object>> sections = wildcardSections(pack);
        assertThat(sections).hasSize(2);
        assertThat(items(sections.get(0)).stream().map(i -> i.get("evidenceId")).toList()).containsExactly("E001", "E002", "E003", "E004");
        assertThat(items(sections.get(1))).isEmpty();
        assertThat(counts(r.run()).get("eventsSelected")).isEqualTo(4);
        assertThat(counts(r.run()).get("counterSignals")).isEqualTo(0);
    }
}
