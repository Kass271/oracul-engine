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
 * phase-02 news-search.md FR-48 "Google News RSS as the main news source, GDELT as fallback", search stage (SourceRetrieval.search):
 * the request per group, the answer classes (each alone), the item cap, per-group fallback to GDELT, and the log lines.
 * Google answers come from the in-process stub (never the real Google, NFR-7).
 */
// @trace FR-48
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "oracul.news.request-spacing=PT0S",
    "oracul.news.rate-limit-wait=PT0S",
    "oracul.news.max-requests=4",
    "oracul.news.google.timeout=PT1S",
})
class GoogleNewsSearchIT extends AbstractNewsSearchIT {

    private static final String ACCEPT = "application/rss+xml, application/xml, text/xml";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; ORACUL/1.0)";

    private static String item(String title) {
        return StubGdelt.rssItem(title, "https://news.example.org/" + Math.abs(title.hashCode()), StubGdelt.pubDate(Instant.now().minusSeconds(3600)),
            "Reuters", "https://www.reuters.com");
    }

    private static StubGdelt.Reply feed(int items, String titlePrefix) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= items; i++) out.add(item(titlePrefix + " story " + i));
        return StubGdelt.rss(out.toArray(String[]::new));
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
        gdelt.rssResponder = req -> StubGdelt.rss();
        retrieval.search(planOfSize(8), horizon);
        assertThat(gdelt.rssRequests).as("one Google request per group").hasSize(4);
        assertThat(gdelt.requests).as("an answered group is not sent to GDELT").isEmpty();
        for (StubGdelt.RssRequest r : gdelt.rssRequests) {
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
        assertThat(gdelt.rssRequests.get(0).q()).isEqualTo("(\"alpha1 beta1\" OR \"alpha2 beta2\") when:" + days + "d");
        assertThat(gdelt.rssRequests.get(3).q()).isEqualTo("(\"alpha7 beta7\" OR \"alpha8 beta8\") when:" + days + "d");
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
    void oneElementHasNoParenthesesAndElementsAreCleanedLikeForGdelt(String name, List<String> texts, String expectedQ) throws Exception {
        gdelt.rssResponder = req -> StubGdelt.rss();
        retrieval.search(planOf(texts), HorizonCode._1Y);
        assertThat(gdelt.rssRequests).hasSize(1);
        assertThat(gdelt.rssRequests.get(0).q()).isEqualTo(expectedQ);
        assertThat(gdelt.rssRequests.get(0).params().keySet()).containsExactlyInAnyOrder("q", "hl", "gl", "ceid");
    }

    @Test
    void anEmptyElementIsNotSentAndStaysEmpty() throws Exception {
        gdelt.rssResponder = req -> StubGdelt.rss();
        var outcome = search(planOf(List.of("   ", "\"()\"")));
        assertThat(gdelt.rssRequests).isEmpty();
        assertThat(gdelt.requests).isEmpty();
        assertThat(outcome.plan().getQueries()).allSatisfy(q -> assertThat(q.getStatus()).isEqualTo(SearchQueryStatus.EMPTY));
    }

    // ---- answer classes, each alone -------------------------------------------------------------------------------

    static Stream<Arguments> failures() {
        Function<StubGdelt.RssRequest, StubGdelt.Reply> notXml = r -> StubGdelt.rssBody("not xml");
        return Stream.of(
            Arguments.of("HTTP 404", (Function<StubGdelt.RssRequest, StubGdelt.Reply>) r -> StubGdelt.status(404)),
            Arguments.of("HTTP 429", (Function<StubGdelt.RssRequest, StubGdelt.Reply>) r -> StubGdelt.status(429)),
            Arguments.of("HTTP 500", (Function<StubGdelt.RssRequest, StubGdelt.Reply>) r -> StubGdelt.status(500)),
            Arguments.of("HTTP 503", (Function<StubGdelt.RssRequest, StubGdelt.Reply>) r -> StubGdelt.status(503)),
            Arguments.of("timeout at google.timeout", (Function<StubGdelt.RssRequest, StubGdelt.Reply>) r ->
                new StubGdelt.Reply(200, "application/rss+xml", "<rss version=\"2.0\"><channel></channel></rss>", 1600)),
            Arguments.of("connection dropped", (Function<StubGdelt.RssRequest, StubGdelt.Reply>) r -> StubGdelt.drop()),
            Arguments.of("200 not xml", notXml),
            Arguments.of("200 truncated xml", (Function<StubGdelt.RssRequest, StubGdelt.Reply>) r ->
                StubGdelt.rssBody("<rss version=\"2.0\"><channel><item><title>broken")),
            Arguments.of("200 root html", (Function<StubGdelt.RssRequest, StubGdelt.Reply>) r ->
                StubGdelt.rssBody("<html><body>Before you continue to Google</body></html>")),
            Arguments.of("200 rss without channel", (Function<StubGdelt.RssRequest, StubGdelt.Reply>) r ->
                StubGdelt.rssBody("<rss version=\"2.0\"></rss>")),
            Arguments.of("200 with a DOCTYPE (XXE guard)", (Function<StubGdelt.RssRequest, StubGdelt.Reply>) r ->
                StubGdelt.rssBody("<?xml version=\"1.0\"?><!DOCTYPE rss [<!ENTITY x \"y\">]><rss version=\"2.0\"><channel>"
                    + item("alpha1 beta1 story") + "</channel></rss>")));
    }

    @ParameterizedTest(name = "Google {0}: one Google request, then one GDELT request, never a second Google one")
    @MethodSource("failures")
    void aFailedGoogleGroupGoesToGdeltExactlyOnce(String name, Function<StubGdelt.RssRequest, StubGdelt.Reply> reply) throws Exception {
        gdelt.rssResponder = reply;
        gdelt.responder = req -> StubGdelt.json("{}");
        var outcome = search(planOfSize(1));
        assertThat(gdelt.rssRequests).as(name + ": Google is never retried").hasSize(1);
        assertThat(gdelt.requests).as(name + ": the group falls back to GDELT").hasSize(1);
        assertThat(gdelt.requests.get(0).elements()).isEqualTo(gdelt.rssRequests.get(0).elements());
        assertThat(gdelt.requests.get(0).params().get("query")).endsWith(" sourcelang:english");
        assertThat(outcome.plan().getQueries().get(0).getStatus()).as("GDELT answered (empty): the group is answered").isEqualTo(SearchQueryStatus.EMPTY);
    }

    @Test
    void aFailedGoogleGroupAndAFailedGdeltFallbackFailTheGroup() throws Exception {
        gdelt.rssResponder = req -> StubGdelt.status(503);
        gdelt.responder = req -> StubGdelt.status(503);
        var outcome = search(planOfSize(1));
        assertThat(gdelt.rssRequests).hasSize(1);
        assertThat(gdelt.requests).hasSize(1);
        assertThat(outcome.plan().getQueries().get(0).getStatus()).isEqualTo(SearchQueryStatus.FAILED);
        assertThat(outcome.plan().getQueries().get(0).getArticlesReturned()).isEqualTo(0);
        assertThat(outcome.allFailed()).isTrue();
    }

    @Test
    void aFeedWithItemsIsAnsweredAndNeverSentToGdelt() throws Exception {
        gdelt.rssResponder = req -> feed(3, "alpha1 beta1");
        var outcome = search(planOfSize(1));
        assertThat(gdelt.rssRequests).hasSize(1);
        assertThat(gdelt.requests).isEmpty();
        SearchQuery q = outcome.plan().getQueries().get(0);
        assertThat(q.getStatus()).isEqualTo(SearchQueryStatus.OK);
        assertThat(q.getArticlesReturned()).isEqualTo(3);
        assertThat(outcome.articlesRetrieved()).isEqualTo(3);
    }

    @Test
    void aFeedWithoutItemsIsEmptyAndNeverSentToGdelt() throws Exception {
        gdelt.rssResponder = req -> StubGdelt.rss();
        var outcome = search(planOfSize(1));
        assertThat(gdelt.rssRequests).hasSize(1);
        assertThat(gdelt.requests).as("a group Google answered with zero items is not sent to GDELT").isEmpty();
        assertThat(outcome.plan().getQueries().get(0).getStatus()).isEqualTo(SearchQueryStatus.EMPTY);
        assertThat(outcome.plan().getQueries().get(0).getArticlesReturned()).isEqualTo(0);
        assertThat(outcome.allFailed()).isFalse();
    }

    // ---- the item cap: min(250, 25 x group size) -----------------------------------------------------------------

    @Test
    void aFeedOf130ItemsForAFiveElementGroupGives125EntriesAndOneElementGives25() throws Exception {
        gdelt.rssResponder = req -> feed(130, req.elements().get(0));
        var outcome = search(planOfSize(20)); // 4 groups of 5 elements
        assertThat(gdelt.rssRequests).hasSize(4);
        assertThat(gdelt.rssRequests).allSatisfy(r -> assertThat(r.elements()).hasSize(5));
        assertThat(outcome.articlesRetrieved()).as("4 groups x 125").isEqualTo(500);
        assertThat(outcome.plan().getQueries().stream().mapToInt(SearchQuery::getArticlesReturned).sum()).isEqualTo(500);

        gdelt.reset();
        gdelt.rssResponder = req -> feed(30, "solo");
        var one = search(planOf(List.of("solo")));
        assertThat(one.articlesRetrieved()).as("one element: 25").isEqualTo(25);
    }

    // ---- fallback of a single group ---------------------------------------------------------------------------------

    @Test
    void onlyTheGroupWhoseGoogleRequestFailedIsSentToGdelt() throws Exception {
        gdelt.rssResponder = req -> req.number() == 2 ? StubGdelt.status(503) : feed(2, req.elements().get(0));
        gdelt.responder = req -> StubGdelt.json(StubGdelt.articles(List.of(
            article(gdelt.baseUrl() + "/articles/fallback-a", req.elements().get(0) + " fallback"))));
        var outcome = search(planOfSize(8));
        assertThat(gdelt.rssRequests).as("one Google request per group, in group order").hasSize(4);
        assertThat(gdelt.requests).as("exactly one GDELT request: group 2").hasSize(1);
        assertThat(gdelt.requests.get(0).elements()).isEqualTo(gdelt.rssRequests.get(1).elements());
        List<SearchQuery> q = outcome.plan().getQueries();
        assertThat(q.subList(0, 2)).allSatisfy(x -> assertThat(x.getStatus()).isIn(SearchQueryStatus.OK, SearchQueryStatus.EMPTY));
        assertThat(q.get(2).getStatus()).as("group 2 answered by GDELT").isEqualTo(SearchQueryStatus.OK);
        assertThat(q.get(2).getArticlesReturned()).isEqualTo(1);
        assertThat(q.get(3).getStatus()).isEqualTo(SearchQueryStatus.EMPTY);
        assertThat(outcome.articlesRetrieved()).as("Σ entries of the answered groups").isEqualTo(2 + 2 + 1 + 2);
    }

    @Test
    void everyGroupFailingOnBothProvidersFailsEveryQuery() throws Exception {
        gdelt.rssResponder = req -> StubGdelt.status(503);
        gdelt.responder = req -> StubGdelt.status(503);
        var outcome = search(planOfSize(20));
        assertThat(gdelt.rssRequests).hasSize(4);
        assertThat(gdelt.requests).hasSize(4);
        assertThat(outcome.plan().getQueries()).hasSize(20).allSatisfy(q -> {
            assertThat(q.getStatus()).isEqualTo(SearchQueryStatus.FAILED);
            assertThat(q.getArticlesReturned()).isEqualTo(0);
        });
        assertThat(outcome.articlesRetrieved()).isEqualTo(0);
    }

    // ---- nothing starts once the run guard says no (deadline / STOP) -------------------------------------------------

    @Test
    void noRequestOfEitherProviderStartsOnceTheGuardSaysNo() throws Exception {
        gdelt.rssResponder = req -> StubGdelt.status(503);
        gdelt.responder = req -> StubGdelt.json("{}");
        var outcome = retrieval.search(planOfSize(8), HorizonCode._1Y, () -> gdelt.rssRequests.size() + gdelt.requests.size() < 3);
        assertThat(gdelt.rssRequests.size() + gdelt.requests.size()).as("the guard let exactly three requests start").isEqualTo(3);
        assertThat(gdelt.rssRequests).hasSize(2); // group 1: Google, GDELT; group 2: Google; its GDELT fallback is not started
        assertThat(gdelt.requests).hasSize(1);
        List<SearchQuery> q = outcome.plan().getQueries();
        assertThat(q.subList(0, 2)).allSatisfy(x -> assertThat(x.getStatus()).isEqualTo(SearchQueryStatus.EMPTY));
        assertThat(q.subList(2, 8)).allSatisfy(x -> assertThat(x.getStatus()).as("failed or never sent").isEqualTo(SearchQueryStatus.FAILED));
    }

    // ---- log lines: never the query text, the q value or the body ----------------------------------------------------------

    @Test
    void failuresAreLoggedWithoutTheQueryTextTheQValueOrTheBody(CapturedOutput out) throws Exception {
        gdelt.rssResponder = req -> req.number() == 1 ? StubGdelt.status(503)
            : req.number() == 2 ? StubGdelt.rssBody("<html>PROVIDER-SECRET-BODY</html>")
            : req.number() == 3 ? StubGdelt.drop() : StubGdelt.rss();
        gdelt.responder = req -> StubGdelt.json("{}");
        search(planOfSize(8));
        String log = out.getOut() + out.getErr();
        assertThat(log).contains("google news request failed: status=503");
        assertThat(log).contains("google news answer is not RSS");
        assertThat(log).containsPattern("google news request failed: [A-Za-z]+Exception|google news request failed: [A-Za-z]+Error");
        assertThat(log).contains("news group falling back to GDELT");
        assertThat(log).doesNotContain("PROVIDER-SECRET-BODY").doesNotContain("alpha1").doesNotContain("beta1").doesNotContain("when:");
    }
}
