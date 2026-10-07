package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Row 9: fixture F240 (F240_BODY: 9 wildcards x 2 queries = 18) end to end - filtering, de-duplication and the source fields
 * (one request per query, FR-52; topic = topicKey of the query's pipeline, FR-50).
 */
// @trace FR-13, FR-44, FR-46, FR-50, FR-52
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=8",
    "oracul.events.max-sources=1000",
})
class SourceFixtureIT extends AbstractEventIT {

    @Test
    @SuppressWarnings("unchecked")
    void f240KeepsThirtySourcesSpreadOverTheTopicsAndCountsEveryEntry() throws Exception {
        newsF240();
        String sid = connectedSid();
        String id = (String) startOk(sid, F240_BODY).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        Map<String, Object> counts = (Map<String, Object>) run.get("counts");
        assertThat(counts.get("searches")).isEqualTo(18);
        assertThat(counts.get("articlesRetrieved")).as("every raw entry is still counted").isEqualTo(240);
        assertThat(counts.get("articlesConsidered")).as("news-search.md FR-46: at most 30 kept").isEqualTo(30);
        assertThat(news.requests).as("18 queries go out as 18 requests").hasSize(18);
        assertThat(news.requests.stream().map(r -> r.elements().size()).distinct().toList()).as("one text per request").containsExactly(1);

        Map<String, Object> research = researchBody(sid, id);
        List<Map<String, Object>> queries = PlanJson.queries(research);
        assertThat(queries).hasSize(18);
        assertThat(PlanJson.pipelines(research)).as("nine pipelines of two queries").hasSize(9);
        List<String> topicOfQuery = new ArrayList<>();
        for (Map<String, Object> p : PlanJson.pipelines(research)) {
            for (int k = 0; k < PlanJson.queriesOf(p).size(); k++) topicOfQuery.add(F240Support.topicOf(p));
        }
        int returned = 0;
        for (int i = 0; i < queries.size(); i++) {
            Map<String, Object> q = queries.get(i);
            assertThat(q.get("status")).isEqualTo("OK");
            int n = ((Number) q.get("articlesReturned")).intValue();
            // every block belongs to the query whose request returned it (FR-52), blank titles included
            int expected = (i + 1) <= 6 ? 14 : 13;
            assertThat(n).as("articlesReturned of query " + (i + 1)).isEqualTo(expected);
            returned += n;
        }
        assertThat(returned).isEqualTo(240);

        // the specified selection, restated by the test oracle over the 205 usable candidates
        List<CapOracle.Cand> usable = F240Support.usableCandidates(topicOfQuery);
        assertThat(usable).as("205 usable candidates").hasSize(205);
        List<Integer> keptIdx = CapOracle.select(usable);
        assertThat(keptIdx).hasSize(30);

        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).as("FR-46: rows in source = listRunSources = 30").hasSize(30);
        assertThat(jdbc.queryForObject("select count(*) from source where run_id = cast(? as uuid)", Integer.class, id)).isEqualTo(30);
        String base = news.baseUrl() + "/articles/";
        java.util.Set<Object> urls = new java.util.HashSet<>();
        for (int i = 0; i < sources.size(); i++) {
            Map<String, Object> s = sources.get(i);
            CapOracle.Cand want = usable.get(keptIdx.get(i));
            assertThat(s.get("id")).as("S-ids are S001...S030 without gaps").isEqualTo(String.format("S%03d", i + 1));
            String url = (String) s.get("url");
            assertThat(url).as("source " + s.get("id") + " is the kept candidate " + want.name()).isEqualTo(base + want.name());
            assertThat(url).doesNotContain("utm_").doesNotContain("#");
            assertThat(urls.add(url)).as("unique url " + url).isTrue();
            assertThat(s.get("topic")).isEqualTo(want.topic());
            assertThat((List<?>) s.get("pipelineIds")).as("pipelineIds of " + s.get("id")).isNotEmpty();
            assertThat(s.get("publisher")).isEqualTo("Stub Site");
            assertThat((String) s.get("title")).isNotBlank().contains("Article r");
            assertThat(OffsetDateTime.parse((String) s.get("publishedAt")).toInstant())
                .isBetween(Instant.now().minus(2, ChronoUnit.DAYS), Instant.now());
            assertThat(OffsetDateTime.parse((String) s.get("retrievedAt"))).isNotNull();
            assertThat(s.get("summary")).isEqualTo("Summary of " + url.substring(url.lastIndexOf('/') + 1));
            assertThat(s.get("sourceType")).isEqualTo("NEWS");
            assertThat(s.get("sourceQuality")).isEqualTo(0.85);
            assertThat(s.get("metadataFetched")).isEqualTo(true);
            assertThat(s.get("publisherUrl")).as("the item's <source url>").isEqualTo("https://www.reuters.com");
            assertThat((List<?>) s.get("entities")).isEqualTo(List.of("Entity " + s.get("id")));
            List<String> ids = (List<String>) s.get("queryIds");
            assertThat(ids).isNotEmpty().isSorted().doesNotHaveDuplicates();
            if (!"shared".equals(want.name())) {
                // a kept source keeps the query ids it had before the cap: only its own query listed it
                int r = Integer.parseInt(want.name().substring(1, want.name().indexOf('-')));
                assertThat(ids).as("queryIds of " + s.get("id")).isEqualTo(List.of(String.format("Q%02d", r)));
            }
        }
        Map<String, Object> shared = sources.stream().filter(s -> (base + "shared").equals(s.get("url"))).findFirst().orElseThrow();
        assertThat((List<?>) shared.get("queryIds")).as("the shared article ranks first in its topic and keeps all 18 queries").hasSize(18);
        assertThat(sources.stream().map(s -> (String) s.get("url")).filter(u -> u.contains("-a2") && !u.contains("#"))
            .count()).as("unusable-link / 200-day-old / blank-title a2 articles never become sources").isEqualTo(0);

        // topic spread: for any two topics that still have unkept candidates the kept counts differ by at most 1
        Map<String, Long> keptPerTopic = sources.stream().collect(java.util.stream.Collectors.groupingBy(s -> (String) s.get("topic"),
            java.util.stream.Collectors.counting()));
        Map<String, Long> usablePerTopic = usable.stream().collect(java.util.stream.Collectors.groupingBy(CapOracle.Cand::topic,
            java.util.stream.Collectors.counting()));
        for (String t : usablePerTopic.keySet()) {
            for (String u : usablePerTopic.keySet()) {
                long kt = keptPerTopic.getOrDefault(t, 0L);
                long ku = keptPerTopic.getOrDefault(u, 0L);
                if (kt < usablePerTopic.get(t) && ku < usablePerTopic.get(u)) {
                    assertThat(Math.abs(kt - ku)).as("kept " + t + "=" + kt + " vs " + u + "=" + ku).isLessThanOrEqualTo(1);
                }
            }
        }
        assertThat(news.articleRequests).as("only the 30 kept candidates are fetched").hasSize(30);
        assertThat(responses.requests.stream().map(StubResponses::purpose).filter("EVENT_NORMALIZATION"::equals).count())
            .as("events are built from the kept sources only: 30 sources fit one batch").isEqualTo(1);
    }
}
