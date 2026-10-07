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

/** SourceQualityTable.classify examples of research-pipeline.md, observed through source.sourceType / sourceQuality. */
// @trace FR-13, FR-53
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=12",
})
class SourceQualityIT extends AbstractRunIT {

    /** domain, expected sourceType, expected sourceQuality. */
    private static final Object[][] ROWS = {
        {"who.int", "OFFICIAL", 0.95}, {"cdc.gov", "OFFICIAL", 0.95}, {"army.mil", "OFFICIAL", 0.95},
        {"data.gov.uk", "OFFICIAL", 0.95}, {"europa.eu", "OFFICIAL", 0.95}, {"ec.europa.eu", "OFFICIAL", 0.95},
        {"un.org", "OFFICIAL", 0.95},
        {"nature.com", "RESEARCH", 0.9}, {"ox.ac.uk", "RESEARCH", 0.9}, {"mit.edu", "RESEARCH", 0.9},
        {"cell.com", "RESEARCH", 0.9}, {"arxiv.org", "RESEARCH", 0.9},
        {"www.reuters.com", "NEWS", 0.85}, {"uk.reuters.com", "NEWS", 0.85}, {"bbc.co.uk", "NEWS", 0.85},
        {"WWW.Reuters.COM", "NEWS", 0.85},
        {"example-news.com", "NEWS", 0.6}, {"myblog.com", "NEWS", 0.6},
        {"foo.substack.com", "BLOG", 0.35}, {"blog.example.com", "BLOG", 0.35}, {"blogger.net", "BLOG", 0.35},
        {"medium.com", "BLOG", 0.35},
        {"", "NEWS", 0.6},
    };

    @Test
    @SuppressWarnings("unchecked")
    void everyExampleDomainIsClassified() throws Exception {
        String base = news.baseUrl();
        List<String> articles = new ArrayList<>();
        Map<String, Object[]> byUrl = new HashMap<>();
        String seen = StubNews.pubDate(Instant.now().minus(1, ChronoUnit.DAYS));
        for (int i = 0; i < ROWS.length; i++) {
            String domain = (String) ROWS[i][0];
            byUrl.put(base + "/articles/q" + i, ROWS[i]);
            // the link resolves (302) to the article page; the publisher domain comes from <source url>; no source for ""
            articles.add(StubNews.rssItem("Title q" + i, base + "/rss/articles/q" + i, seen,
                domain.isEmpty() ? null : domain, domain.isEmpty() ? null : "https://" + domain));
        }
        // FR-53: at most 4 sources per pipeline, so the 23 articles are spread over six pipelines (the first query of each)
        List<List<String>> perPipeline = new ArrayList<>();
        for (int from = 0; from < articles.size(); from += 4) perPipeline.add(articles.subList(from, Math.min(from + 4, articles.size())));
        assertThat(perPipeline).hasSize(6);
        news.responder = StubNews.firstQueryItems(perPipeline);
        String sid = connectedSid();
        String id = (String) startOk(sid, AbstractEventIT.wildcardsBody(6)).get("id");
        assertThat(awaitDone(sid, id).get("status")).isEqualTo("COMPLETED");
        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).hasSize(ROWS.length);
        for (Map<String, Object> s : sources) {
            Object[] row = byUrl.get((String) s.get("url"));
            assertThat(row).as("known url " + s.get("url")).isNotNull();
            assertThat(s.get("sourceType")).as("type of '" + row[0] + "'").isEqualTo(row[1]);
            assertThat(s.get("sourceQuality")).as("quality of '" + row[0] + "'").isEqualTo(row[2]);
        }
    }
}
