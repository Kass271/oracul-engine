package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.runs.AbstractRunIT;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Row 9: fixture F240 (budget 18) end to end - filtering, de-duplication and the source fields. */
// @trace FR-13
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=8",
    "oracul.research.query-budget=18",
})
class SourceFixtureIT extends AbstractRunIT {

    private String f240(StubGdelt.Request req) {
        int r = req.number();
        int n = r <= 6 ? 14 : 13;
        String base = gdelt.baseUrl() + "/articles/";
        Instant day = Instant.now().minus(1, ChronoUnit.DAYS);
        List<String> out = new ArrayList<>();
        for (int a = 1; a <= n; a++) {
            String url = base + "r" + r + "-a" + a;
            String title = "Article r" + r + "-a" + a;
            String language = "English";
            Instant seen = day;
            if (a == 1) url = base + "shared?utm_source=q" + r + "#top";
            if (a == 2 && r <= 5) title = "  ";
            if (a == 2 && r >= 6 && r <= 10) language = "French";
            if (a == 2 && r >= 11 && r <= 15) seen = Instant.now().minus(200, ChronoUnit.DAYS);
            if (a == 2 && r >= 16) url = base + "r" + r + "-a3#dup";
            out.add(StubGdelt.article(url, title, "reuters.com", language, StubGdelt.seendate(seen)));
        }
        return StubGdelt.articles(out);
    }

    @Test
    @SuppressWarnings("unchecked")
    void f240ProducesTheExpectedSourcesAndCounts() throws Exception {
        gdelt.responder = req -> StubGdelt.json(f240(req));
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        Map<String, Object> counts = (Map<String, Object>) run.get("counts");
        assertThat(counts.get("searches")).isEqualTo(18);
        assertThat(counts.get("articlesRetrieved")).isEqualTo(240);
        assertThat(counts.get("articlesConsidered")).isEqualTo(205);
        assertThat(gdelt.requests).hasSize(18);

        List<Map<String, Object>> queries = (List<Map<String, Object>>) ((Map<String, Object>) researchBody(sid, id).get("searchPlan")).get("queries");
        assertThat(queries).hasSize(18);
        int returned = 0;
        for (Map<String, Object> q : queries) {
            assertThat(q.get("status")).isEqualTo("OK");
            int n = ((Number) q.get("articlesReturned")).intValue();
            assertThat(n).isIn(13, 14);
            returned += n;
        }
        assertThat(returned).isEqualTo(240);

        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).hasSize(205);
        String base = gdelt.baseUrl() + "/articles/";
        java.util.Set<Object> urls = new java.util.HashSet<>();
        for (int i = 0; i < sources.size(); i++) {
            Map<String, Object> s = sources.get(i);
            assertThat(s.get("id")).isEqualTo(String.format("S%03d", i + 1));
            String url = (String) s.get("url");
            assertThat(url).startsWith(base).doesNotContain("utm_").doesNotContain("#");
            assertThat(urls.add(url)).as("unique url " + url).isTrue();
            assertThat(s.get("publisher")).isEqualTo("Stub Site");
            assertThat((String) s.get("title")).isNotBlank().startsWith("Article r");
            assertThat(OffsetDateTime.parse((String) s.get("publishedAt")).toInstant())
                .isBetween(Instant.now().minus(2, ChronoUnit.DAYS), Instant.now());
            assertThat(OffsetDateTime.parse((String) s.get("retrievedAt"))).isNotNull();
            assertThat(s.get("summary")).isEqualTo("Summary of " + url.substring(url.lastIndexOf('/') + 1));
            assertThat(s.get("sourceType")).isEqualTo("NEWS");
            assertThat(s.get("sourceQuality")).isEqualTo(0.85);
            assertThat(s.get("metadataFetched")).isEqualTo(true);
            assertThat((List<?>) s.get("entities")).isEmpty();
            assertThat((List<?>) s.get("queryIds")).isNotEmpty();
            List<String> ids = (List<String>) s.get("queryIds");
            assertThat(ids).isSorted().doesNotHaveDuplicates();
        }
        Map<String, Object> shared = sources.stream().filter(s -> (base + "shared").equals(s.get("url"))).findFirst().orElseThrow();
        assertThat((List<?>) shared.get("queryIds")).hasSize(18);
        assertThat(sources.stream().map(s -> (String) s.get("url")).filter(u -> u.contains("-a2") && !u.contains("#"))
            .count()).as("French / 200-day-old / blank-title a2 articles never become sources").isEqualTo(0);
    }
}
