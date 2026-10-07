package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/**
 * wildcard-search.md FR-52 (slice 03): one bare {@code GET /rss/search} per planned query, dispatched in parallel under
 * 8 permits, with the answer classes, the one 429 retry (default wait PT2S), the item cap and the log lines. Through the
 * existing seam {@code SourceRetrieval.search(plan, horizon)}; Google answers come from the in-process stub (NFR-7).
 */
// @trace FR-52
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "oracul.news.google.concurrency=8",
    "oracul.news.google.timeout=PT1S",
})
class ParallelSearchIT extends AbstractOrderIT {

    private static final String ACCEPT = "application/rss+xml, application/xml, text/xml";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; ORACUL/1.0)";

    private static String item(String title) {
        return StubNews.rssItem(title, "https://news.example.org/" + Math.abs(title.hashCode()),
            StubNews.pubDate(Instant.now().minusSeconds(3600)), "Reuters", "https://www.reuters.com");
    }

    private static StubNews.Reply feed(int items, String titlePrefix) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= items; i++) out.add(item(titlePrefix + " story " + i));
        return StubNews.rss(out.toArray(String[]::new));
    }

    // ---- the request ---------------------------------------------------------------------------------------------

    static Stream<Arguments> horizons() {
        return Stream.of(
            Arguments.of(HorizonCode._1D, 7), Arguments.of(HorizonCode._1W, 7), Arguments.of(HorizonCode._1M, 14),
            Arguments.of(HorizonCode._1Y, 90), Arguments.of(HorizonCode._5Y, 90), Arguments.of(HorizonCode._10Y, 90),
            Arguments.of(HorizonCode._20Y, 90));
    }

    // `q` per horizon: all 7 codes; parameters exactly q, hl, gl, ceid; requests = planned queries; no OR / quotes / parentheses
    @ParameterizedTest(name = "horizon {0} -> when:{1}d")
    @MethodSource("horizons")
    void everyQueryIsOneBareRequestWithExactlyTheSpecifiedShape(HorizonCode horizon, int days) throws Exception {
        retrieval.search(planOfSize(8), horizon);
        assertThat(news.requests).as("one Google request per planned query").hasSize(8);
        assertThat(news.paths).as("only /rss/search").containsOnly("/rss/search");
        for (StubNews.Request r : news.requests) {
            assertThat(r.params().keySet()).as("exactly four parameters, each once").containsExactlyInAnyOrder("q", "hl", "gl", "ceid");
            assertThat(r.rawQuery().split("&")).hasSize(4);
            assertThat(r.params().get("hl")).isEqualTo("en-US");
            assertThat(r.params().get("gl")).isEqualTo("US");
            assertThat(r.params().get("ceid")).isEqualTo("US:en");
            assertThat(r.q()).matches("^alpha\\d+ beta\\d+ when:" + days + "d$");
            assertThat(r.q()).doesNotContain(" OR ").doesNotContain("\"").doesNotContain("(").doesNotContain(")");
            assertThat(r.elements()).hasSize(1);
            assertThat(r.rawQuery()).as("URL-encoded").doesNotContain(" ");
            assertThat(r.headers().get("accept")).isEqualTo(ACCEPT);
            assertThat(r.headers().get("user-agent")).isEqualTo(USER_AGENT);
        }
        assertThat(news.requests.stream().map(StubNews.Request::q).sorted().toList())
            .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 8).mapToObj(i -> "alpha" + i + " beta" + i + " when:" + days + "d").sorted().toList());
    }

    static Stream<Arguments> sentTexts() {
        return Stream.of(
            Arguments.of("one token", "vaccines", "vaccines when:90d"),
            Arguments.of("one phrase is not quoted", "mRNA vaccine approval", "mRNA vaccine approval when:90d"),
            Arguments.of("quotes and parentheses become spaces", "\"quoted\" (paren) text", "quoted paren text when:90d"),
            Arguments.of("OR is removed", "fusion OR fission", "fusion fission when:90d"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sentTexts")
    void thePlannedTextIsSentCleanedAndNeverQuoted(String name, String text, String expectedQ) throws Exception {
        retrieval.search(planOf(List.of(text)), HorizonCode._1Y);
        assertThat(news.requests).hasSize(1);
        assertThat(news.requests.get(0).q()).isEqualTo(expectedQ);
    }

    // (f) counts.searches = planned queries, also the unsendable ones
    @Test
    void anUnsendableTextIsNotSentStaysEmptyAndStillCountsAsASearch() throws Exception {
        var outcome = search(planOf(List.of("OR AND NOT", "real topic")));
        assertThat(news.requests).as("only the sendable text").hasSize(1);
        assertThat(outcome.plan().getQueries().get(0).getStatus()).isEqualTo(SearchQueryStatus.EMPTY);
        assertThat(outcome.plan().getQueries().get(0).getArticlesReturned()).isZero();
        assertThat(outcome.searches()).as("counts.searches = planned queries").isEqualTo(2);
    }

    @Test
    void twentyQueriesAreSentTogetherWithoutSpacing() throws Exception {
        search(planOfSize(20));
        assertThat(news.requests).hasSize(20);
        long first = news.requests.stream().mapToLong(StubNews.Request::arrivedNanos).min().orElseThrow();
        long last = news.requests.stream().mapToLong(StubNews.Request::arrivedNanos).max().orElseThrow();
        assertThat((last - first) / 1_000_000).as("no spacing: all 20 arrive within 1.5 s").isLessThan(1500);
    }

    // ---- answer classes, each alone -------------------------------------------------------------------------------

    static Stream<Arguments> failures() {
        return Stream.of(
            Arguments.of("HTTP 404", (Function<StubNews.Request, StubNews.Reply>) r -> StubNews.status(404)),
            Arguments.of("HTTP 500", (Function<StubNews.Request, StubNews.Reply>) r -> StubNews.status(500)),
            Arguments.of("HTTP 503", (Function<StubNews.Request, StubNews.Reply>) r -> StubNews.status(503)),
            Arguments.of("timeout at google.timeout", (Function<StubNews.Request, StubNews.Reply>) r ->
                new StubNews.Reply(200, "application/rss+xml", "<rss version=\"2.0\"><channel></channel></rss>", 1600)),
            Arguments.of("connection dropped", (Function<StubNews.Request, StubNews.Reply>) r -> StubNews.drop()),
            Arguments.of("200 not xml", (Function<StubNews.Request, StubNews.Reply>) r -> StubNews.rssBody("not xml")),
            Arguments.of("200 truncated xml", (Function<StubNews.Request, StubNews.Reply>) r ->
                StubNews.rssBody("<rss version=\"2.0\"><channel><item><title>broken")),
            Arguments.of("200 root html", (Function<StubNews.Request, StubNews.Reply>) r ->
                StubNews.rssBody("<html><body>Before you continue to Google</body></html>")),
            Arguments.of("200 rss without channel", (Function<StubNews.Request, StubNews.Reply>) r ->
                StubNews.rssBody("<rss version=\"2.0\"></rss>")),
            Arguments.of("200 with a DOCTYPE (XXE guard)", (Function<StubNews.Request, StubNews.Reply>) r ->
                StubNews.rssBody("<?xml version=\"1.0\"?><!DOCTYPE rss [<!ENTITY x \"y\">]><rss version=\"2.0\"><channel>"
                    + item("alpha1 beta1 story") + "</channel></rss>")));
    }

    @ParameterizedTest(name = "Google {0}: exactly one request, the query is FAILED")
    @MethodSource("failures")
    void aQueryWithoutAnAnswerIsFailedAfterExactlyOneRequest(String name, Function<StubNews.Request, StubNews.Reply> reply) throws Exception {
        news.responder = reply;
        var outcome = search(planOfSize(1));
        assertThat(news.requests).as(name + ": not retried").hasSize(1);
        assertThat(news.paths).containsOnly("/rss/search");
        SearchQuery q = outcome.plan().getQueries().get(0);
        assertThat(q.getStatus()).isEqualTo(SearchQueryStatus.FAILED);
        assertThat(q.getArticlesReturned()).isZero();
        assertThat(outcome.allFailed()).isTrue();
    }

    @Test
    void aFeedWithThreeItemsIsOkWithThree() throws Exception {
        news.responder = req -> feed(3, "alpha1 beta1");
        var outcome = search(planOfSize(1));
        assertThat(news.requests).hasSize(1);
        SearchQuery q = outcome.plan().getQueries().get(0);
        assertThat(q.getStatus()).isEqualTo(SearchQueryStatus.OK);
        assertThat(q.getArticlesReturned()).isEqualTo(3);
        assertThat(outcome.articlesRetrieved()).isEqualTo(3);
    }

    @Test
    void aFeedWithoutItemsIsEmpty() throws Exception {
        news.responder = req -> StubNews.rss();
        var outcome = search(planOfSize(1));
        assertThat(news.requests).hasSize(1);
        assertThat(outcome.plan().getQueries().get(0).getStatus()).isEqualTo(SearchQueryStatus.EMPTY);
        assertThat(outcome.allFailed()).isFalse();
    }

    static Stream<Arguments> itemCounts() {
        return Stream.of(0, 1, 99, 100, 101, 130).map(n -> Arguments.of(n, Math.min(n, 100)));
    }

    // at most the first 100 items of an answer are read
    @ParameterizedTest(name = "{0} items answered -> articlesReturned {1}")
    @MethodSource("itemCounts")
    void atMostTheFirstHundredItemsAreRead(int answered, int expected) throws Exception {
        news.responder = req -> feed(answered, "alpha1 beta1");
        var outcome = search(planOfSize(1));
        SearchQuery q = outcome.plan().getQueries().get(0);
        assertThat(q.getArticlesReturned()).isEqualTo(expected);
        assertThat(q.getStatus()).isEqualTo(expected == 0 ? SearchQueryStatus.EMPTY : SearchQueryStatus.OK);
        assertThat(outcome.articles().get(q.getId())).hasSize(expected);
        assertThat(outcome.articlesRetrieved()).isEqualTo(expected);
    }

    // ---- 429: one retry after rate-limit-wait (default PT2S) --------------------------------------------------------------

    @Test
    void a429ThenA200IsOkAfterExactlyTwoRequestsTheRetryTwoSecondsLater() throws Exception {
        news.responder = StubNews.rateLimitedOnce(req -> feed(3, "alpha1 beta1"));
        var outcome = search(planOfSize(1));
        assertThat(news.requests).as("the 429 and one identical retry").hasSize(2);
        assertThat(news.requests.get(1).rawQuery()).as("the identical request").isEqualTo(news.requests.get(0).rawQuery());
        long gapMs = (news.requests.get(1).arrivedNanos() - news.finishedNanos.get(news.requests.get(0).number())) / 1_000_000;
        assertThat(gapMs).as("the retry waits rate-limit-wait (PT2S) after the 429 answer").isGreaterThanOrEqualTo(1900);
        SearchQuery q = outcome.plan().getQueries().get(0);
        assertThat(q.getStatus()).isEqualTo(SearchQueryStatus.OK);
        assertThat(q.getArticlesReturned()).isEqualTo(3);
    }

    @Test
    void aSecondTooManyRequestsFailsTheQueryAfterExactlyTwoRequests() throws Exception {
        news.responder = req -> StubNews.tooMany();
        var outcome = search(planOfSize(1));
        assertThat(news.requests).hasSize(2);
        assertThat(outcome.plan().getQueries().get(0).getStatus()).isEqualTo(SearchQueryStatus.FAILED);
        assertThat(outcome.allFailed()).isTrue();
    }

    @Test
    void aRetryThatFailsOtherwiseIsFailedAndNothingIsRetriedTwice() throws Exception {
        news.responder = req -> req.number() == 1 ? StubNews.tooMany() : StubNews.status(503);
        var outcome = search(planOfSize(1));
        assertThat(news.requests).as("the 429, one retry, nothing more").hasSize(2);
        assertThat(outcome.plan().getQueries().get(0).getStatus()).isEqualTo(SearchQueryStatus.FAILED);
    }

    @Test
    void theWaitOfOneRateLimitedQueryDoesNotHoldBackTheOthers() throws Exception {
        // Q01 is rate-limited once; the other three answer at once, so they do not wait for the 2 s of Q01
        Function<StubNews.Request, StubNews.Reply> limitedOnce = StubNews.rateLimitedOnce(r -> feed(1, "one"));
        news.responder = req -> req.elements().get(0).startsWith("alpha1 ") ? limitedOnce.apply(req) : feed(1, "other");
        long t0 = System.nanoTime();
        var outcome = search(planOfSize(4));
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertThat(news.requests).as("4 queries + 1 retry").hasSize(5);
        assertThat(ms).isLessThan(4500);
        assertThat(outcome.plan().getQueries()).allSatisfy(q -> assertThat(q.getStatus()).isEqualTo(SearchQueryStatus.OK));
    }

    // ---- one failed query, the others go on ------------------------------------------------------------------------------

    @Test
    void onlyTheQueryWhoseRequestFailedIsFailedAndNoRequestIsAskedTwice() throws Exception {
        news.responder = req -> req.elements().get(0).startsWith("alpha3 ") ? StubNews.status(503) : feed(2, req.elements().get(0));
        var outcome = search(planOfSize(8));
        assertThat(news.requests).as("one request per query").hasSize(8);
        List<SearchQuery> q = outcome.plan().getQueries();
        for (int i = 0; i < 8; i++) {
            assertThat(q.get(i).getStatus()).as("Q" + (i + 1)).isEqualTo(i == 2 ? SearchQueryStatus.FAILED : SearchQueryStatus.OK);
            assertThat(q.get(i).getArticlesReturned()).isEqualTo(i == 2 ? 0 : 2);
        }
        assertThat(outcome.articlesRetrieved()).isEqualTo(14);
        assertThat(outcome.allFailed()).isFalse();
    }

    @Test
    void everyQueryFailingFailsEveryQuery() throws Exception {
        news.responder = req -> StubNews.status(503);
        var outcome = search(planOfSize(20));
        assertThat(news.requests).as("exactly one request per query").hasSize(20);
        assertThat(outcome.plan().getQueries()).hasSize(20).allSatisfy(q -> assertThat(q.getStatus()).isEqualTo(SearchQueryStatus.FAILED));
        assertThat(outcome.allFailed()).isTrue();
    }

    // each item belongs to the query whose own request returned it (not to a query whose words its title contains)
    @Test
    void anItemBelongsToTheQueryWhoseRequestReturnedIt() throws Exception {
        news.responder = req -> {
            // the title of every item names query 1, whichever query returned it
            String own = req.elements().get(0);
            return StubNews.rss(StubNews.rssItem("alpha1 beta1 headline for " + own, "https://news.example.org/" + own.replace(' ', '-'),
                StubNews.pubDate(Instant.now().minusSeconds(3600)), "Reuters", "https://www.reuters.com"));
        };
        var outcome = search(planOfSize(3));
        for (int i = 1; i <= 3; i++) {
            String id = String.format("Q%02d", i);
            assertThat(outcome.articles().get(id)).as(id).hasSize(1);
            assertThat(outcome.articles().get(id).get(0).url()).isEqualTo("https://news.example.org/alpha" + i + "-beta" + i);
        }
        assertThat(outcome.ordered().stream().map(SourceRetrieval.Attributed::queryId).toList()).containsExactly("Q01", "Q02", "Q03");
    }

    // ---- log lines: never the query text, the q value or the body ----------------------------------------------------------

    @Test
    void failuresAreLoggedWithoutTheQueryTextTheQValueOrTheBody(CapturedOutput out) throws Exception {
        news.responder = req -> switch (req.elements().get(0).charAt(5)) {
            case '1' -> StubNews.status(503);
            case '2' -> StubNews.rssBody("<html>PROVIDER-SECRET-BODY</html>");
            case '3' -> StubNews.drop();
            default -> StubNews.rss();
        };
        search(planOfSize(5));
        String log = out.getOut() + out.getErr();
        assertThat(log).contains("google news request failed: status=503");
        assertThat(log).contains("google news answer is not RSS");
        assertThat(log).containsPattern("google news request failed: [A-Za-z]+Exception|google news request failed: [A-Za-z]+Error");
        assertThat(log).as("no group line any more").doesNotContain("news group failed").doesNotContain("news group falling back");
        assertThat(log).doesNotContain("PROVIDER-SECRET-BODY").doesNotContain("alpha1").doesNotContain("beta1").doesNotContain("when:");
    }

    @Test
    void aRateLimitedRequestLogsTheRetryLineWithoutTheQuery(CapturedOutput out) throws Exception {
        news.responder = StubNews.rateLimitedOnce(req -> StubNews.rss());
        search(planOfSize(1));
        String log = out.getOut() + out.getErr();
        assertThat(log).contains("google news request rate-limited, retrying once");
        assertThat(log).doesNotContain("alpha1").doesNotContain("when:");
    }
}
