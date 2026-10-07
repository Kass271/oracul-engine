package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.runs.AbstractDeadlineIT;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.context.TestPropertySource;

/**
 * NFR-10 part 3 / article-retrieval.md slice 08 range (j), injected clock: the retrieval window is measured on the {@code Clock} and cut by the
 * run's deadline (3 minutes). With the publisher requests held, advancing the {@code MutableClock} by 91 s (the default stage budget is 90 s, the
 * run deadline is not reached) ends the retrieval within a second of real time: every source is NOT_ATTEMPTED and no request starts afterwards.
 */
// @trace FR-54
@TestPropertySource(properties = {
    "oracul.run.executor-threads=8",
})
class ArticleRetrievalDeadlineIT extends AbstractDeadlineIT {

    private int sourceRows(String runId) {
        return jdbc.queryForObject("select count(*) from source where run_id = cast(? as uuid)", Integer.class, runId);
    }

    @Test
    @Timeout(60)
    void aClockAdvancedPastTheStageBudgetEndsTheRetrievalWithinASecondAndNoRequestStartsAfterIt() throws Exception {
        freshStubs();
        news.articleGate = new CountDownLatch(1);
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        long end = System.currentTimeMillis() + 15_000;
        while (news.articleRequests.size() < 4 && System.currentTimeMillis() < end) Thread.sleep(20);
        assertThat(news.articleRequests).as("the four publisher requests are held at the stub").hasSize(4);
        assertThat(sourceRows(id)).as("READING_SOURCES has not committed while the requests are held").isZero();
        int requestsBefore = news.paths.size();

        long advancedAt = System.currentTimeMillis();
        clock.advance(d(91));
        long waitEnd = advancedAt + 10_000;
        while (sourceRows(id) == 0 && System.currentTimeMillis() < waitEnd) Thread.sleep(10);
        long took = System.currentTimeMillis() - advancedAt;
        assertThat(sourceRows(id)).as("committed").isEqualTo(4);
        assertThat(took).as("within 1 s of real time after the clock passed the 90 s budget").isLessThanOrEqualTo(1_000L);

        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).hasSize(4);
        for (Map<String, Object> s : sources) {
            assertThat(s.get("contentStatus")).isEqualTo("NOT_ATTEMPTED");
            assertThat((String) s.get("url")).startsWith(news.baseUrl() + "/articles/");
            assertThat(s.get("publisherHost")).isEqualTo("127.0.0.1");
            assertThat(s.get("metadataFetched")).isEqualTo(false);
            assertThat((List<?>) s.get("excerpts")).isEmpty();
        }
        assertThat(counts(run).get("sourcesWithContent")).isEqualTo(0);
        assertThat(news.articleRequests).as("no article request started after the advance").hasSize(4);
        assertThat(news.paths.stream().skip(requestsBefore).filter(p -> p.startsWith("/articles/") || p.startsWith("/rss/articles/")
            || p.startsWith("/_/"))).as("no retrieval request of any kind starts after the advance").isEmpty();
    }
}
