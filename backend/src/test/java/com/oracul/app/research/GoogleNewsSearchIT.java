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
 * phase-02 news-search.md FR-48 "Google News RSS", as changed by phase-03 FR-49 (no fallback provider), search stage
 * (SourceRetrieval.search): the request per group, the group sizes, the answer classes (each alone, a failed group is
 * FAILED after exactly one request), the item cap, and the log lines.
 * Google answers come from the in-process stub (never the real Google, NFR-7).
 */
// @trace FR-48, FR-49
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "oracul.news.google.timeout=PT1S",
})
class GoogleNewsSearchIT extends AbstractNewsSearchIT {

    private static final String ACCEPT = "application/rss+xml, application/xml, text/xml";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; ORACUL/1.0)";

    private static String item(String title) {
        return StubNews.rssItem(title, "https://news.example.org/" + Math.abs(title.hashCode()), StubNews.pubDate(Instant.now().minusSeconds(3600)),
            "Reuters", "https://www.reuters.com");
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

    // `q` per horizon: all 7 codes
    @ParameterizedTest(name = "horizon {0} -> when:{1}d")
    @MethodSource("horizons")
    void theRequestHasExactlyTheSpecifiedShapeForEveryHorizon(HorizonCode horizon, int days) throws Exception {
        news.responder = req -> StubNews.rss();
        retrieval.search(planOfSize(8), horizon);
        assertThat(news.requests).as("one Google request per group").hasSize(4);
        assertThat(news.paths).as("only /rss/search").containsOnly("/rss/search");
        for (StubNews.Request r : news.requests) {
            assertThat(r.params().keySet()).as("exactly four parameters, each once").containsExactlyInAnyOrder("q", "hl", "gl", "ceid");
            assertThat(r.rawQuery().split("&")).hasSize(4);
            assertThat(r.params().get("hl")).isEqualTo("en-US");
            assertThat(r.params().get("gl")).isEqualTo("US");
            assertThat(r.params().get("ceid")).isEqualTo("US:en");
            assertThat(r.q()).matches("^\\(.+( OR .+)+\\) when:" + days + "d$");
            assertThat(r.elements()).hasSize(2);
            assertThat(r.rawQuery()).as("URL-encoded").doesNotContain(" ");
            assertThat(r.headers().get("accept")).isEqualTo(ACCEPT);
            assertThat(r.headers().get("user-agent")).isEqualTo(USER_AGENT);
        }
        assertThat(news.requests.get(0).q()).isEqualTo("(\"alpha1 beta1\" OR \"alpha2 beta2\") when:" + days + "d");
        assertThat(news.requests.get(3).q()).isEqualTo("(\"alpha7 beta7\" OR \"alpha8 beta8\") when:" + days + "d");
    }

    static Stream<Arguments> elementShapes() {
        return Stream.of(
            Arguments.of("one token", List.of("vaccines"), "vaccines when:90d"),
            Arguments.of("one phrase", List.of("mRNA vaccine approval"), "\"mRNA vaccine approval\" when:90d"),
            Arguments.of("quotes and parentheses become spaces", List.of("\"quoted\" (paren) text"), "\"quoted paren text\" when:90d"),
            Arguments.of("OR and AND are removed", List.of("fusion OR fission"), "\"fusion fission\" when:90d"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("elementShapes")
    void oneElementHasNoParenthesesAndElementsAreCleaned(String name, List<String> texts, String expectedQ) throws Exception {
        news.responder = req -> StubNews.rss();
        retrieval.search(planOf(texts), HorizonCode._1Y);
        assertThat(news.requests).hasSize(1);
        assertThat(news.requests.get(0).q()).isEqualTo(expectedQ);
        assertThat(news.requests.get(0).params().keySet()).containsExactlyInAnyOrder("q", "hl", "gl", "ceid");
    }

    @Test
    void anEmptyElementIsNotSentAndStaysEmpty() throws Exception {
        news.responder = req -> StubNews.rss();
        var outcome = search(planOf(List.of("   ", "\"()\"")));
        assertThat(news.requests).isEmpty();
        assertThat(outcome.plan().getQueries()).allSatisfy(q -> assertThat(q.getStatus()).isEqualTo(SearchQueryStatus.EMPTY));
    }

    // ---- answer classes, each alone -------------------------------------------------------------------------------

    static Stream<Arguments> failures() {
        Function<StubNews.Request, StubNews.Reply> notXml = r -> StubNews.rssBody("not xml");
        return Stream.of(
            Arguments.of("HTTP 404", (Function<StubNews.Request, StubNews.Reply>) r -> StubNews.status(404)),
            Arguments.of("HTTP 429", (Function<StubNews.Request, StubNews.Reply>) r -> StubNews.status(429)),
            Arguments.of("HTTP 500", (Function<StubNews.Request, StubNews.Reply>) r -> StubNews.status(500)),
            Arguments.of("HTTP 503", (Function<StubNews.Request, StubNews.Reply>) r -> StubNews.status(503)),
            Arguments.of("timeout at google.timeout", (Function<StubNews.Request, StubNews.Reply>) r ->
                new StubNews.Reply(200, "application/rss+xml", "<rss version=\"2.0\"><channel></channel></rss>", 1600)),
            Arguments.of("connection dropped", (Function<StubNews.Request, StubNews.Reply>) r -> StubNews.drop()),
            Arguments.of("200 not xml", notXml),
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

    @ParameterizedTest(name = "Google {0}: one Google request, no other provider, the group is FAILED")
    @MethodSource("failures")
    void aFailedGoogleGroupIsFailedAfterExactlyOneRequest(String name, Function<StubNews.Request, StubNews.Reply> reply) throws Exception {
        news.responder = reply;
        var outcome = search(planOfSize(1));
        assertThat(news.requests).as(name + ": Google is never retried").hasSize(1);
        assertThat(news.paths).as(name + ": no request to any other route").containsOnly("/rss/search");
        assertThat(outcome.plan().getQueries().get(0).getStatus()).as("no fallback: the group is FAILED").isEqualTo(SearchQueryStatus.FAILED);
        assertThat(outcome.plan().getQueries().get(0).getArticlesReturned()).isEqualTo(0);
        assertThat(outcome.allFailed()).isTrue();
    }

    @Test
    void aFeedWithItemsIsAnswered() throws Exception {
        news.responder = req -> feed(3, "alpha1 beta1");
        var outcome = search(planOfSize(1));
        assertThat(news.requests).hasSize(1);
        assertThat(news.paths).containsOnly("/rss/search");
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
        assertThat(news.paths).as("a group Google answered with zero items triggers no other request").containsOnly("/rss/search");
        assertThat(outcome.plan().getQueries().get(0).getStatus()).isEqualTo(SearchQueryStatus.EMPTY);
        assertThat(outcome.plan().getQueries().get(0).getArticlesReturned()).isEqualTo(0);
        assertThat(outcome.allFailed()).isFalse();
    }

    // ---- the groups: G = min(4, n), contiguous, balanced, larger first; items used = min(250, 25 x size) ------------------

    static Stream<Arguments> sendableCounts() {
        return Stream.of(0, 1, 2, 3, 4, 5, 8, 9, 17, 18, 20).map(Arguments::of);
    }

    /** Expected group sizes: G = min(4, n), sizes differ by at most 1, the larger groups first. */
    static List<Integer> expectedSizes(int n) {
        int g = Math.min(4, n);
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < g; i++) sizes.add(n / g + (i < n % g ? 1 : 0));
        return sizes;
    }

    // @trace FR-49
    @ParameterizedTest(name = "{0} sendable queries")
    @MethodSource("sendableCounts")
    void theQueriesAreSplitIntoMinFourContiguousBalancedGroupsAndEachGroupUsesMinOf250And25PerQuery(int n) throws Exception {
        news.responder = req -> feed(300, "g" + req.elements().get(0).replace(' ', '-'));
        var plan = planOfSize(n);
        var outcome = search(plan);
        List<Integer> sizes = expectedSizes(n);
        assertThat(news.requests).as("G = min(4, " + n + ") requests").hasSize(sizes.size());
        if (n == 0) {
            assertThat(news.paths).as("n = 0: nothing is sent").isEmpty();
        } else {
            assertThat(news.paths).as("no other request").containsOnly("/rss/search");
        }

        List<String> texts = plan.getQueries().stream().map(SearchQuery::getText).toList();
        List<List<String>> expectedGroups = new ArrayList<>();
        int from = 0;
        for (int size : sizes) {
            expectedGroups.add(texts.subList(from, from + size));
            from += size;
        }
        assertThat(news.requests.stream().map(StubNews.Request::elements).toList())
            .as("contiguous groups in plan order, each query in exactly one group").containsExactlyInAnyOrderElementsOf(expectedGroups);

        from = 0;
        for (int size : sizes) {
            int used = 0;
            for (int i = from; i < from + size; i++) used += outcome.plan().getQueries().get(i).getArticlesReturned();
            assertThat(used).as("items used by the group of " + size).isEqualTo(Math.min(250, 25 * size));
            from += size;
        }
        assertThat(outcome.plan().getQueries()).hasSize(n);
        assertThat(outcome.searches()).isEqualTo(n);
    }

    // ---- the item cap: min(250, 25 x group size) -----------------------------------------------------------------

    @Test
    void aFeedOf130ItemsForAFiveElementGroupGives125EntriesAndOneElementGives25() throws Exception {
        news.responder = req -> feed(130, req.elements().get(0));
        var outcome = search(planOfSize(20)); // 4 groups of 5 elements
        assertThat(news.requests).hasSize(4);
        assertThat(news.requests).allSatisfy(r -> assertThat(r.elements()).hasSize(5));
        assertThat(outcome.articlesRetrieved()).as("4 groups x 125").isEqualTo(500);
        assertThat(outcome.plan().getQueries().stream().mapToInt(SearchQuery::getArticlesReturned).sum()).isEqualTo(500);

        news.reset();
        news.responder = req -> feed(30, "solo");
        var one = search(planOf(List.of("solo")));
        assertThat(one.articlesRetrieved()).as("one element: 25").isEqualTo(25);
    }

    // ---- one failed group ---------------------------------------------------------------------------------------------

    @Test
    void onlyTheGroupWhoseGoogleRequestFailedIsFailed() throws Exception {
        news.responder = req -> req.number() == 2 ? StubNews.status(503) : feed(2, req.elements().get(0));
        var outcome = search(planOfSize(8));
        assertThat(news.requests).as("one Google request per group").hasSize(4);
        assertThat(news.paths).containsOnly("/rss/search");
        List<SearchQuery> q = outcome.plan().getQueries();
        assertThat(q.subList(0, 2)).allSatisfy(x -> assertThat(x.getStatus()).isIn(SearchQueryStatus.OK, SearchQueryStatus.EMPTY));
        assertThat(q.subList(2, 4)).as("group 2 failed: no fallback").allSatisfy(x -> {
            assertThat(x.getStatus()).isEqualTo(SearchQueryStatus.FAILED);
            assertThat(x.getArticlesReturned()).isEqualTo(0);
        });
        assertThat(q.subList(4, 8)).allSatisfy(x -> assertThat(x.getStatus()).isIn(SearchQueryStatus.OK, SearchQueryStatus.EMPTY));
        assertThat(outcome.articlesRetrieved()).as("sum of the entries of the answered groups").isEqualTo(2 + 2 + 2);
        assertThat(outcome.allFailed()).isFalse();
    }

    @Test
    void everyGroupFailingFailsEveryQuery() throws Exception {
        news.responder = req -> StubNews.status(503);
        var outcome = search(planOfSize(20));
        assertThat(news.requests).as("exactly one request per group").hasSize(4);
        assertThat(news.paths).containsOnly("/rss/search");
        assertThat(outcome.plan().getQueries()).hasSize(20).allSatisfy(q -> {
            assertThat(q.getStatus()).isEqualTo(SearchQueryStatus.FAILED);
            assertThat(q.getArticlesReturned()).isEqualTo(0);
        });
        assertThat(outcome.articlesRetrieved()).isEqualTo(0);
        assertThat(outcome.allFailed()).isTrue();
    }

    // ---- nothing starts once the run guard says no (deadline / STOP) -------------------------------------------------

    @Test
    void noRequestStartsOnceTheGuardSaysNoAndTheUnstartedGroupsAreFailed() throws Exception {
        news.responder = req -> StubNews.status(503);
        var outcome = retrieval.search(planOfSize(8), HorizonCode._1Y, () -> news.requests.size() < 3);
        assertThat(news.requests).as("the guard let exactly three requests start").hasSize(3);
        assertThat(news.paths).containsOnly("/rss/search");
        assertThat(outcome.plan().getQueries()).as("failed or never started: FAILED").allSatisfy(x ->
            assertThat(x.getStatus()).isEqualTo(SearchQueryStatus.FAILED));
        assertThat(outcome.allFailed()).isTrue();
    }

    // ---- log lines: never the query text, the q value or the body ----------------------------------------------------------

    @Test
    void failuresAreLoggedWithoutTheQueryTextTheQValueOrTheBody(CapturedOutput out) throws Exception {
        news.responder = req -> req.number() == 1 ? StubNews.status(503)
            : req.number() == 2 ? StubNews.rssBody("<html>PROVIDER-SECRET-BODY</html>")
            : req.number() == 3 ? StubNews.drop() : StubNews.rss();
        search(planOfSize(8));
        String log = out.getOut() + out.getErr();
        assertThat(log).contains("google news request failed: status=503");
        assertThat(log).contains("google news answer is not RSS");
        assertThat(log).containsPattern("google news request failed: [A-Za-z]+Exception|google news request failed: [A-Za-z]+Error");
        assertThat(log.split("news group failed", -1).length - 1).as("one WARN per failed group (groups 1, 2 and 3)").isEqualTo(3);
        assertThat(log).doesNotContain("news group falling back");
        assertThat(log).doesNotContain("PROVIDER-SECRET-BODY").doesNotContain("alpha1").doesNotContain("beta1").doesNotContain("when:");
    }
}
