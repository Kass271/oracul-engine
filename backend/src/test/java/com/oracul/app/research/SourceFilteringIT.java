package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.runs.AbstractRunIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** FR-13 filtering and URL normalization edge cases (Google News items: no language, age by pubDate), end to end through listRunSources (UrlNormalizer, SourceFilter). */
// @trace FR-13
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=11",
})
class SourceFilteringIT extends AbstractRunIT {

    private static String seen(long days) {
        return StubNews.pubDate(Instant.now().minus(days, ChronoUnit.DAYS));
    }

    /** One Google News item; the pubDate is passed as text so that an unparseable or missing one can be tested. */
    private String art(String url, String title, String pubDate) {
        return StubNews.rssItem(title, url, pubDate, "Reuters", "https://www.reuters.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtersDropTheRightArticlesAndNormalizeTheKeptUrls() throws Exception {
        String base = news.baseUrl();
        int port = news.port();
        List<String> first = new ArrayList<>(List.of(
            art("HTTP://127.0.0.1:80/Path/A?utm_source=x&b=1#f", "Default port", seen(1)),
            art("http://LocalHost:" + port + "/articles/host-case?utm_campaign=c", "Host case", seen(1)),
            art(base + "/articles/utm-only?UTM_source=a&utm_medium=b", "Only utm", seen(1)),
            art(base + "/articles/no-lang", "No language", seen(1)),
            art(base + "/articles/lower-lang", "Lower language", seen(1)),
            art(base + "/articles/bad-date", "Bad date", "garbage"),
            art(base + "/articles/no-date", "No date", null),
            art(base + "/articles/recent", "Recent", seen(80)),
            art(base + "/articles/spaces", "  Many   spaces \t here ", seen(1)),
            art(base + "/articles/dup", "Duplicate", seen(1)),
            art("", "Blank url", seen(1)),
            art("ftp://127.0.0.1/file", "Not http", seen(1)),
            art("javascript:alert(1)", "Script url", seen(1)),
            art(base + "/articles/blank-title", "   ", seen(1)),
            art(base + "/articles/old", "Too old", seen(100))));
        StubNews.Reply firstAnswer = StubNews.rss(first.toArray(String[]::new));
        StubNews.Reply secondAnswer = StubNews.rss(art(base + "/articles/dup?utm_x=1", "Duplicate again", seen(1)));
        news.responder = req -> switch (req.number()) {
            case 1 -> firstAnswer;
            case 2 -> secondAnswer;
            default -> StubNews.rss();
        };
        String sid = connectedSid();
        String id = (String) startOk(sid, B).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        Map<String, Object> counts = (Map<String, Object>) run.get("counts");
        assertThat(counts.get("articlesRetrieved")).isEqualTo(16);
        assertThat(counts.get("articlesConsidered")).isEqualTo(10);

        Map<String, Map<String, Object>> byUrl = new HashMap<>();
        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).hasSize(10);
        for (int i = 0; i < sources.size(); i++) {
            assertThat(sources.get(i).get("id")).isEqualTo(String.format("S%03d", i + 1));
            byUrl.put((String) sources.get(i).get("url"), sources.get(i));
        }
        assertThat(byUrl.keySet()).containsExactlyInAnyOrder(
            "http://127.0.0.1/Path/A?b=1",
            "http://localhost:" + port + "/articles/host-case",
            base + "/articles/utm-only",
            base + "/articles/no-lang",
            base + "/articles/lower-lang",
            base + "/articles/bad-date",
            base + "/articles/no-date",
            base + "/articles/recent",
            base + "/articles/spaces",
            base + "/articles/dup");
        assertThat(byUrl.get(base + "/articles/spaces").get("title")).isEqualTo("Many spaces here");
        assertThat(byUrl.get(base + "/articles/bad-date").get("publishedAt")).as("unparseable pubDate: no publishedAt").isNull();
        assertThat(byUrl.get(base + "/articles/no-date").get("publishedAt")).isNull();
        assertThat(byUrl.get(base + "/articles/recent").get("publishedAt")).isNotNull();
        List<String> dupIds = (List<String>) byUrl.get(base + "/articles/dup").get("queryIds");
        assertThat(dupIds).as("the second query that returned the url is appended").hasSize(2).doesNotHaveDuplicates().isSorted();
    }
}
