package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Row 23 of research-pipeline.md "Slice 06_events": the source cap picks by quality, then recency, then id. */
// @trace FR-14
@TestPropertySource(properties = "oracul.events.max-sources=2")
class EventSourceOrderIT extends AbstractEventIT {

    // #23
    @Test
    void theCapKeepsTheBestSourcesQualityFirstThenTheMostRecent() throws Exception {
        List<Art> arts = List.of(
            new Art("who-vaccine", "who.int", "WHO approves new pandemic vaccine"),
            new Art("reuters-vaccine", "reuters.com", "Regulators approve pandemic vaccine"),
            new Art("local-vaccine", "example-news.com", "Pandemic vaccine gets approval"),
            new Art("robot-strike", "reuters.com", "Dock workers strike over humanoid robots", Duration.ofHours(2)));
        gdeltArticles(arts);
        Ran r = run(A);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(counts(r.run()).get("articlesConsidered")).isEqualTo(4);

        List<StubResponses.Request> norm = requests(NORMALIZATION);
        assertThat(norm).hasSize(1);
        // S001 has quality 0.95; S004 beats S002 (both 0.85) by recency (2 hours old against 1 day)
        assertThat(lines(StubResponses.dataBlock(norm.get(0).inputText(), "sources"))).as("exactly two source lines").hasSize(2);
        assertThat(StubResponses.sourceIds(norm.get(0).inputText())).containsExactly("S001", "S004");

        assertThat(counts(r.run()).get("uniqueEvents")).isEqualTo(2);
        List<Map<String, Object>> events = events(r);
        assertThat(events).hasSize(2);
        assertThat(event(events, "EV001").get("sourceIds")).isEqualTo(List.of("S001"));
        assertThat(event(events, "EV002").get("sourceIds")).isEqualTo(List.of("S004"));
        assertThat(source(r, "S001").get("entities")).isEqualTo(List.of("Entity S001"));
        assertThat(source(r, "S004").get("entities")).isEqualTo(List.of("Entity S004"));
        assertThat(source(r, "S002").get("entities")).isEqualTo(List.of());
        assertThat(source(r, "S003").get("entities")).isEqualTo(List.of());
        assertThat(requests(CLASSIFICATION)).hasSize(1);
        assertThat(StubResponses.eventIds(requests(CLASSIFICATION).get(0).inputText())).containsExactly("EV001", "EV002");
    }
}
