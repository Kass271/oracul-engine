package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import com.oracul.app.api.model.Source;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * FR-52 range (e): the outcome of a search — statuses, {@code articlesReturned}, {@code ordered}, every source's
 * {@code queryIds} and the source order — is the same for every concurrency, even when the answers arrive in reverse
 * plan order (Q20 answers first). The expectation is computed from the plan alone, so it holds for concurrency 1 and 8.
 * Subclasses pin {@code oracul.news.google.concurrency}.
 */
abstract class AbstractOrderIT extends AbstractNewsSearchIT {

    private static final int QUERIES = 20;
    private static final String SHARED = "shared-story";

    /** Index (1-based) of the plan query a request belongs to ("alpha<i> beta<i>"). */
    private static int indexOf(StubNews.Request req) {
        String text = req.elements().get(0);
        return Integer.parseInt(text.substring("alpha".length(), text.indexOf(' ')));
    }

    private String link(String name) {
        return news.baseUrl() + "/articles/" + name;
    }

    /** Query i lists its own story; every 4th query also lists the shared story (as its last item); Q07 is empty, Q13 fails. 19 sources stay under the cap of 30. */
    private List<String> expectedNames(int i) {
        List<String> names = new ArrayList<>();
        if (i == 7) return names;
        names.add("ord-" + i + "-a");
        if (i % 4 == 0) names.add(SHARED);
        return names;
    }

    // @trace FR-52
    @Test
    void theOutcomeDoesNotDependOnTheOrderTheAnswersArriveIn() throws Exception {
        news.responder = req -> {
            int i = indexOf(req);
            long delay = (QUERIES + 1 - i) * 15L; // reverse plan order: Q20 fastest
            if (i == 13) return new StubNews.Reply(503, "text/plain", "down", delay);
            StubNews.Reply r = StubNews.rss(expectedNames(i).stream()
                .map(n -> StubNews.rssItem("Story " + n, link(n), StubNews.pubDate(java.time.Instant.now().minusSeconds(3600)), "Reuters",
                    "https://www.reuters.com")).toArray(String[]::new));
            return new StubNews.Reply(r.status(), r.contentType(), r.body(), delay);
        };
        var outcome = search(planOfSize(QUERIES));
        assertThat(news.requests).hasSize(QUERIES);

        List<SearchQuery> q = outcome.plan().getQueries();
        List<String> expectedOrdered = new ArrayList<>();
        for (int i = 1; i <= QUERIES; i++) {
            SearchQuery query = q.get(i - 1);
            List<String> names = expectedNames(i);
            SearchQueryStatus status = i == 13 ? SearchQueryStatus.FAILED : names.isEmpty() ? SearchQueryStatus.EMPTY : SearchQueryStatus.OK;
            assertThat(query.getId()).isEqualTo(String.format("Q%02d", i));
            assertThat(query.getStatus()).as("Q" + i).isEqualTo(status);
            assertThat(query.getArticlesReturned()).as("Q" + i + " articlesReturned").isEqualTo(i == 13 ? 0 : names.size());
            // a FAILED query got no answer: it contributes no items to `ordered` (FR-52)
            if (i != 13) for (String n : names) expectedOrdered.add(query.getId() + " " + link(n));
        }
        assertThat(outcome.ordered().stream().map(a -> a.queryId() + " " + a.article().url()).toList())
            .as("ordered = plan order, then feed order").containsExactlyElementsOf(expectedOrdered);
        assertThat(outcome.articlesRetrieved()).isEqualTo(expectedOrdered.size());
        assertThat(outcome.searches()).isEqualTo(QUERIES);

        List<Source> sources = retrieval.readSources(outcome, HorizonCode._1Y).stream().map(s -> s.source()).toList();
        List<String> expectedUrls = new ArrayList<>();
        for (String entry : expectedOrdered) {
            String url = entry.substring(entry.indexOf(' ') + 1);
            if (!expectedUrls.contains(url)) expectedUrls.add(url);
        }
        assertThat(sources.stream().map(s -> s.getUrl().toString()).toList()).as("source order = first appearance").containsExactlyElementsOf(expectedUrls);
        for (Source s : sources) {
            List<String> ids = new ArrayList<>();
            for (String entry : expectedOrdered) {
                if (entry.endsWith(" " + s.getUrl())) ids.add(entry.substring(0, entry.indexOf(' ')));
            }
            assertThat(s.getQueryIds()).as("queryIds of " + s.getUrl()).containsExactlyElementsOf(ids.stream().sorted().distinct().toList());
        }
        Source shared = sources.stream().filter(s -> s.getUrl().toString().endsWith("/" + SHARED)).findFirst().orElseThrow();
        assertThat(shared.getQueryIds()).containsExactly("Q04", "Q08", "Q12", "Q16", "Q20");
    }
}
