package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** getRun row #3: observed stage transitions are monotonic and use the fixed (stage, index, label) rows. */
// @trace FR-24
@TestPropertySource(properties = "oracul.run.placeholder-stage-delay=PT1S")
class GetRunProgressIT extends AbstractRunIT {

    private static final List<List<Object>> TABLE = List.of(
        List.of("UNDERSTANDING", 1, "Understanding your future…"),
        List.of("RESEARCH_STRATEGY", 2, "Building research strategy…"),
        List.of("SEARCHING", 3, "Searching current events…"),
        List.of("READING_SOURCES", 4, "Reading relevant sources…"),
        List.of("CONNECTING_SIGNALS", 5, "Connecting signals…"),
        List.of("RANKING", 6, "Ranking evidence…"),
        List.of("EXPLORING_FUTURES", 7, "Exploring possible futures…"),
        List.of("CHALLENGING_ASSUMPTIONS", 8, "Challenging assumptions…"),
        List.of("CONSTRUCTING_SCENARIO", 9, "Constructing scenario…"),
        List.of("WRITING_STORY", 10, "Writing from the future…"));

    @Test
    void stageIndexNeverDecreasesAndEveryObservationIsATableRow() throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, B).get("id");
        int last = 0;
        Set<Object> stages = new LinkedHashSet<>();
        List<Integer> seen = new ArrayList<>();
        long end = System.currentTimeMillis() + 25_000;
        boolean terminal = false;
        while (System.currentTimeMillis() < end && !terminal) {
            MvcResult r = getRun(sid, id).andReturn();
            assertThat(r.getResponse().getStatus()).isEqualTo(200);
            Map<String, Object> run = json(r.getResponse().getContentAsString());
            int idx = ((Number) run.get("stageIndex")).intValue();
            assertThat(idx).as("stageIndex never decreases").isGreaterThanOrEqualTo(last);
            last = idx;
            seen.add(idx);
            if (idx > 0) {
                assertThat(TABLE).contains(List.of(run.get("stage"), idx, run.get("stageLabel")));
                stages.add(run.get("stage"));
            } else {
                assertThat(run.get("status")).isEqualTo("QUEUED");
            }
            terminal = "COMPLETED".equals(run.get("status"));
            Thread.sleep(200);
        }
        assertThat(terminal).as("run completed within 25 s; seen=" + seen).isTrue();
        assertThat(stages.size()).as("distinct stages observed").isGreaterThanOrEqualTo(5);
    }
}
