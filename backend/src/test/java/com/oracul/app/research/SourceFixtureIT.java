package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Row 9: fixture F240 (F240_BODY: 9 wildcards x 2 queries = 18) end to end - filtering, de-duplication and the source fields
 * (one request per query, FR-52; topic = topicKey of the first pipeline of the source, FR-50). FR-53 replaces the FR-46 cap:
 * at most 4 per pipeline by relevance, a shared article counted once, 30 by pipeline round robin, Evidence-order numbering,
 * so F240 keeps 28 sources and its per-pipeline fields are the worked fixture of article-retrieval.md.
 */
// @trace FR-13, FR-44, FR-46, FR-50, FR-52, FR-53
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=8",
    "oracul.events.max-sources=1000",
})
class SourceFixtureIT extends AbstractEventIT {

    @Test
    @SuppressWarnings("unchecked")
    void f240KeepsTwentyEightSourcesByRelevanceAndCountsEveryCandidate() throws Exception {
        newsF240();
        String sid = connectedSid();
        String id = (String) startOk(sid, F240_BODY).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        Map<String, Object> counts = (Map<String, Object>) run.get("counts");
        assertThat(counts.get("searches")).isEqualTo(18);
        assertThat(counts.get("articlesRetrieved")).as("every raw entry is still counted").isEqualTo(240);
        assertThat(counts.get("articlesConsidered")).as("FR-53: distinct normalised links of all candidates").isEqualTo(205);
        assertThat(counts.get("sourcesKept")).as("FR-53: round 1 keeps only the shared link, rounds 2-4 nine each").isEqualTo(28);
        assertThat(news.requests).as("18 queries go out as 18 requests").hasSize(18);
        assertThat(news.requests.stream().map(r -> r.elements().size()).distinct().toList()).as("one text per request").containsExactly(1);

        Map<String, Object> research = researchBody(sid, id);
        List<Map<String, Object>> queries = PlanJson.queries(research);
        List<Map<String, Object>> pipelines = PlanJson.pipelines(research);
        assertThat(queries).hasSize(18);
        assertThat(pipelines).as("nine pipelines of two queries").hasSize(9);
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

        List<String> kept = F240Support.keptNames(9, false);
        assertThat(kept).as("S001 = shared, the own sources of Wk are S(3k-1)...S(3k+1)").hasSize(28);
        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).as("rows in source = listRunSources = sourcesKept").hasSize(28);
        assertThat(jdbc.queryForObject("select count(*) from source where run_id = cast(? as uuid)", Integer.class, id)).isEqualTo(28);
        String base = news.baseUrl() + "/articles/";
        java.util.Set<Object> urls = new java.util.HashSet<>();
        for (int i = 0; i < sources.size(); i++) {
            Map<String, Object> s = sources.get(i);
            String want = kept.get(i);
            assertThat(s.get("id")).as("S-ids are S001...S028 without gaps").isEqualTo(String.format("S%03d", i + 1));
            String url = (String) s.get("url");
            assertThat(url).as("source " + s.get("id") + " is the kept candidate " + want).isEqualTo(base + want);
            assertThat(url).doesNotContain("utm_").doesNotContain("#");
            assertThat(urls.add(url)).as("unique url " + url).isTrue();
            assertThat((List<?>) s.get("pipelineIds")).as("pipelineIds of " + s.get("id")).isNotEmpty();
            assertThat(s.get("publisher")).isEqualTo("Stub Site");
            assertThat((String) s.get("title")).isNotBlank().contains("Article r");
            assertThat(OffsetDateTime.parse((String) s.get("publishedAt")).toInstant())
                .isBetween(Instant.now().minus(2, ChronoUnit.DAYS), Instant.now());
            assertThat(OffsetDateTime.parse((String) s.get("retrievedAt"))).isNotNull();
            assertThat(s.get("summary")).isEqualTo("Summary of " + want);
            assertThat(s.get("sourceType")).isEqualTo("NEWS");
            assertThat(s.get("sourceQuality")).isEqualTo(0.85);
            assertThat(s.get("metadataFetched")).isEqualTo(true);
            assertThat(s.get("publisherUrl")).as("the item's <source url>").isEqualTo("https://www.reuters.com");
            assertThat((List<?>) s.get("entities")).isEqualTo(List.of("Entity " + s.get("id")));
            List<String> ids = (List<String>) s.get("queryIds");
            assertThat(ids).isNotEmpty().isSorted().doesNotHaveDuplicates();
            if (!"shared".equals(want)) {
                int r = Integer.parseInt(want.substring(1, want.indexOf('-')));
                int k = (r + 1) / 2;
                assertThat(ids).as("queryIds of " + s.get("id")).isEqualTo(List.of(String.format("Q%02d", r)));
                assertThat(s.get("pipelineIds")).isEqualTo(List.of(String.format("W%02d", k)));
                assertThat(s.get("topic")).as("topic of its first pipeline").isEqualTo(PlanSupport.TEN_WILDCARDS.get(k - 1));
            }
        }
        Map<String, Object> shared = sources.get(0);
        assertThat(shared.get("url")).isEqualTo(base + "shared");
        assertThat((List<?>) shared.get("queryIds")).as("the shared article keeps all 18 queries").hasSize(18);
        List<String> allPipelines = new ArrayList<>();
        for (int k = 1; k <= 9; k++) allPipelines.add(String.format("W%02d", k));
        assertThat(shared.get("pipelineIds")).as("found by all nine pipelines").isEqualTo(allPipelines);
        assertThat(shared.get("topic")).as("topicKey of the first pipeline").isEqualTo(PlanSupport.TEN_WILDCARDS.get(0));
        assertThat(sources.stream().map(s -> (String) s.get("url")).filter(u -> u.contains("-a2")).count())
            .as("unusable-link / 200-day-old / blank-title a2 articles never become sources").isEqualTo(0);

        // per pipeline: candidatesConsidered and sourceIds (the S-ids of its group, in group order)
        for (int k = 1; k <= 9; k++) {
            Map<String, Object> p = pipelines.get(k - 1);
            assertThat(p.get("candidatesConsidered")).as("W" + k + " candidatesConsidered").isEqualTo(k <= 3 ? 25 : 23);
            List<String> group = F240Support.group(k, 9, false);
            List<String> expectedIds = new ArrayList<>();
            for (String name : group) expectedIds.add(String.format("S%03d", kept.indexOf(name) + 1));
            assertThat(p.get("sourceIds")).as("W" + k + " sourceIds").isEqualTo(expectedIds);
        }
        assertThat(pipelines.get(0).get("sourceIds")).isEqualTo(List.of("S001", "S002", "S003", "S004"));
        assertThat(pipelines.get(8).get("sourceIds")).isEqualTo(List.of("S001", "S026", "S027", "S028"));

        assertThat(news.articleRequests).as("only the 28 kept sources are fetched, the shared one once").hasSize(28);
        assertThat(responses.requests.stream().map(StubResponses::purpose).filter("EVENT_NORMALIZATION"::equals).count())
            .as("events are built from the kept sources only: 28 sources fit one batch").isEqualTo(1);
    }
}
