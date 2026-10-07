package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.ArticleContentStatus;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.PipelineQuery;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import com.oracul.app.api.model.Source;
import com.oracul.app.api.model.SourceType;
import com.oracul.app.api.model.WildcardPipeline;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-02 news-search.md FR-48 steps 3, 4 and 8 as changed by FR-52 and (R2) FR-54: what a Google News RSS item becomes — title
 * cleaning, attribution to the query whose own request returned the item (no longer by title), pubDate and link classes,
 * the retrieval that decodes a Google link into the publisher URL and reads the publisher page (or does not), publisher and
 * publisherUrl, source type and quality from the publisher host. The search stage sends one request per planned query.
 */
// @trace FR-48, FR-52, FR-54
@TestPropertySource(properties = {
    "oracul.news.google.timeout=PT1S",
})
class GoogleNewsSourceIT extends AbstractNewsSearchIT {

    private static final String REUTERS = "https://www.reuters.com";

    private String base() {
        return news.baseUrl();
    }

    private static String recent() {
        return StubNews.pubDate(Instant.now().minus(1, ChronoUnit.DAYS));
    }

    /** The feed is answered to the request of the query with this text; every other query gets an empty feed. */
    private static java.util.function.Function<StubNews.Request, StubNews.Reply> queryAnswers(String text, StubNews.Reply answer) {
        return req -> req.elements().contains(text) ? answer : StubNews.rss();
    }

    /** Search + readSources over a feed of the given items for the first query of the plan; one request per query, no other request. */
    private List<Source> sources(SearchPlanHolder plan, String... items) throws Exception {
        String first = plan.plan().getQueries().get(0).getText();
        news.responder = queryAnswers(first, StubNews.rss(items));
        var outcome = search(plan.plan());
        assertThat(news.requests).as("one request per planned query").hasSize(plan.plan().getQueries().size());
        assertThat(news.paths).as("only Google News and article requests").allSatisfy(p -> assertThat(p).doesNotStartWith("/api/v2/doc"));
        List<Source> out = new ArrayList<>();
        for (var st : retrieval.readSources(outcome, plan.horizon())) out.add(st.source());
        return out;
    }

    private record SearchPlanHolder(com.oracul.app.api.model.SearchPlan plan, HorizonCode horizon) {}

    private SearchPlanHolder one() {
        return new SearchPlanHolder(planOf(List.of("solar flare")), HorizonCode._1Y);
    }

    private List<Source> oneItem(String link, String title, String pubDate, String source, String sourceUrl) throws Exception {
        return sources(one(), StubNews.rssItem(title, link, pubDate, source, sourceUrl));
    }

    // ---- title cleaning ------------------------------------------------------------------------------------------

    static Stream<Arguments> titles() {
        return Stream.of(
            Arguments.of("the source suffix is removed", "Vaccine approved - Reuters", "Reuters", "Vaccine approved"),
            Arguments.of("only the last suffix is removed", "A - B - Reuters", "Reuters", "A - B"),
            Arguments.of("no suffix: unchanged", "Vaccine approved", "Reuters", "Vaccine approved"),
            Arguments.of("another source: unchanged", "Vaccine approved - Reuters", "BBC", "Vaccine approved - Reuters"),
            Arguments.of("source text missing: unchanged", "Vaccine approved - Reuters", null, "Vaccine approved - Reuters"),
            Arguments.of("blanks around the title and the source text", "  Vaccine approved - Reuters  ", " Reuters ", "Vaccine approved"),
            Arguments.of("the suffix needs the separator", "Vaccine approved Reuters", "Reuters", "Vaccine approved Reuters"));
    }

    @ParameterizedTest(name = "{0}: [{1}] / {2} -> [{3}]")
    @MethodSource("titles")
    void theTitleLosesItsSourceSuffixExactlyOnce(String name, String title, String source, String expected) throws Exception {
        List<Source> s = oneItem(base() + "/articles/title", title, recent(), source, REUTERS);
        assertThat(s).hasSize(1);
        assertThat(s.get(0).getTitle()).isEqualTo(expected);
    }

    // FR-52: an item belongs to the query whose request returned it, whatever its title says
    @Test
    void anItemBelongsToTheQueryWhoseRequestReturnedItNotToTheOneItsTitleNames() throws Exception {
        List<String> elements = List.of("solar eclipse", "wind farm", "mars rover", "fusion plant");
        // every title names "solar eclipse" (query 1), but the answers are returned by queries 2, 3 and 4
        news.responder = req -> {
            int idx = elements.indexOf(req.elements().get(0));
            if (idx <= 0) return StubNews.rss();
            return StubNews.rss(
                StubNews.rssItem("Solar eclipse draws crowds " + idx + " - Reuters", base() + "/articles/e" + idx + "a", recent(), "Reuters", REUTERS),
                StubNews.rssItem("Solar eclipse again " + idx + " - Reuters", base() + "/articles/e" + idx + "b", recent(), "Reuters", REUTERS));
        };
        var outcome = search(planOf(elements));
        assertThat(news.requests).hasSize(4);
        int[] expected = {0, 2, 2, 2};
        for (int i = 0; i < elements.size(); i++) {
            SearchQuery q = outcome.plan().getQueries().get(i);
            assertThat(q.getArticlesReturned()).as(q.getId()).isEqualTo(expected[i]);
            assertThat(q.getStatus()).isEqualTo(expected[i] > 0 ? SearchQueryStatus.OK : SearchQueryStatus.EMPTY);
        }
        assertThat(outcome.articles().get("Q02").get(0).title()).isEqualTo("Solar eclipse draws crowds 1");
        assertThat(outcome.ordered().stream().map(SourceRetrieval.Attributed::queryId).toList())
            .containsExactly("Q02", "Q02", "Q03", "Q03", "Q04", "Q04");
        assertThat(outcome.articlesRetrieved()).isEqualTo(6);
    }

    @Test
    void theCleanedTitleIsWhatTheOutcomeHolds() throws Exception {
        news.responder = queryAnswers("reuters wire", StubNews.rss(StubNews.rssItem("Solar eclipse draws crowds - Reuters wire",
            base() + "/articles/clean", recent(), "Reuters wire", REUTERS)));
        var outcome = search(planOf(List.of("reuters wire", "solar eclipse")));
        assertThat(news.requests).hasSize(2);
        List<SearchQuery> q = outcome.plan().getQueries();
        assertThat(q.get(0).getStatus()).as("the query that returned it, not the one the cleaned title matches").isEqualTo(SearchQueryStatus.OK);
        assertThat(q.get(1).getStatus()).isEqualTo(SearchQueryStatus.EMPTY);
        assertThat(outcome.articles().get("Q01").get(0).title()).isEqualTo("Solar eclipse draws crowds");
    }

    // ---- pubDate ---------------------------------------------------------------------------------------------------

    @Test
    void anRfc1123PubDateIsThePublishedAt() throws Exception {
        Instant t = Instant.now().minus(30, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        List<Source> s = oneItem(base() + "/articles/pub", "Dated story", StubNews.pubDate(t), "Reuters", REUTERS);
        assertThat(s).hasSize(1);
        assertThat(s.get(0).getPublishedAt().toInstant()).isEqualTo(t);
    }

    @ParameterizedTest(name = "pubDate [{0}] is unparsable: publishedAt absent, the item is not age-filtered")
    @MethodSource("badDates")
    void anUnparsablePubDateLeavesPublishedAtAbsentAndKeepsTheItem(String pubDate) throws Exception {
        List<Source> s = oneItem(base() + "/articles/nodate", "Undated story", pubDate, "Reuters", REUTERS);
        assertThat(s).hasSize(1);
        assertThat(s.get(0).getPublishedAt()).isNull();
    }

    static Stream<String> badDates() {
        return Stream.of("", "yesterday", "2026-10-03", "Sat, 31 Feb 2026 10:00:00 GMT");
    }

    @Test
    void anItemOlderThanTheHorizonDaysIsDropped() throws Exception {
        Instant old = Instant.now().minus(200, ChronoUnit.DAYS);
        assertThat(oneItem(base() + "/articles/old", "Old story", StubNews.pubDate(old), "Reuters", REUTERS))
            .as("200 days old at horizon 1y (90 days)").isEmpty();
        news.reset();
        assertThat(oneItem(base() + "/articles/fresh", "Fresh story", StubNews.pubDate(Instant.now().minus(80, ChronoUnit.DAYS)), "Reuters", REUTERS))
            .as("80 days old is inside the 90 days").hasSize(1);
    }

    @Test
    void theAgeFilterOfShortHorizonsFollowsTheirDays() throws Exception {
        var plan = new SearchPlanHolder(planOf(List.of("solar flare")), HorizonCode._1W);
        assertThat(sources(plan, StubNews.rssItem("Eight days", base() + "/articles/d8", StubNews.pubDate(Instant.now().minus(8, ChronoUnit.DAYS)),
            "Reuters", REUTERS))).as("8 days old at horizon 1w (7 days)").isEmpty();
        news.reset();
        assertThat(sources(plan, StubNews.rssItem("Five days", base() + "/articles/d5", StubNews.pubDate(Instant.now().minus(5, ChronoUnit.DAYS)),
            "Reuters", REUTERS))).hasSize(1);
        news.reset();
        var month = new SearchPlanHolder(planOf(List.of("solar flare")), HorizonCode._1M);
        assertThat(sources(month, StubNews.rssItem("Ten days", base() + "/articles/d10", StubNews.pubDate(Instant.now().minus(10, ChronoUnit.DAYS)),
            "Reuters", REUTERS))).as("10 days old at horizon 1m (14 days)").hasSize(1);
    }

    // ---- link classes -----------------------------------------------------------------------------------------------------

    static Stream<Arguments> links() {
        return Stream.of(
            Arguments.of("http link", "http://127.0.0.1:{port}/articles/l1", true),
            Arguments.of("https link", "https://127.0.0.1:1/articles/l2", true),
            Arguments.of("missing link", null, false),
            Arguments.of("empty link", "", false),
            Arguments.of("javascript: link", "javascript:alert(1)", false),
            Arguments.of("relative link", "/articles/relative", false),
            Arguments.of("scheme-less link", "www.example.org/story", false),
            Arguments.of("ftp link", "ftp://127.0.0.1/story", false));
    }

    @ParameterizedTest(name = "{0}: kept={2}")
    @MethodSource("links")
    void onlyAbsoluteHttpAndHttpsLinksBecomeSources(String name, String link, boolean kept) throws Exception {
        String url = link == null ? null : link.replace("{port}", String.valueOf(news.port()));
        List<Source> s = oneItem(url, "Story " + name, recent(), "Reuters", REUTERS);
        assertThat(s).as(name).hasSize(kept ? 1 : 0);
    }

    // ---- publisher, publisherUrl, type and quality --------------------------------------------------------------------

    static Stream<Arguments> publishers() {
        // name, source@url, source text, expected publisher, expected publisherUrl, type, quality
        return Stream.of(
            Arguments.of("Reuters", "https://www.reuters.com", "Reuters", "Reuters", REUTERS, SourceType.NEWS, 0.85),
            Arguments.of("WHO", "https://www.who.int/news", "WHO", "WHO", "https://www.who.int/news", SourceType.OFFICIAL, 0.95),
            Arguments.of("a blog", "https://example.medium.com", "A blog", "A blog", "https://example.medium.com", SourceType.BLOG, 0.35),
            Arguments.of("a research site", "https://www.nature.com", "Nature", "Nature", "https://www.nature.com", SourceType.RESEARCH, 0.9),
            Arguments.of("source text missing: host of source@url", "https://www.reuters.com", null, "www.reuters.com", REUTERS, SourceType.NEWS, 0.85),
            Arguments.of("both missing: host of the link", null, null, "127.0.0.1", null, SourceType.NEWS, 0.6),
            Arguments.of("source@url relative: no publisherUrl, quality of the link host", "/relative", "Some Daily", "Some Daily", null,
                SourceType.NEWS, 0.6),
            Arguments.of("source@url missing", null, "Some Daily", "Some Daily", null, SourceType.NEWS, 0.6),
            Arguments.of("source@url not http(s)", "ftp://files.example.org", "Some Daily", "Some Daily", null, SourceType.NEWS, 0.6));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("publishers")
    void publisherPublisherUrlTypeAndQualityComeFromTheSourceElement(String name, String sourceUrl, String sourceText, String publisher,
                                                                     String publisherUrl, SourceType type, double quality) throws Exception {
        List<Source> s = oneItem(base() + "/articles/pub-" + Math.abs(name.hashCode()), "Story about publishers", recent(), sourceText, sourceUrl);
        assertThat(s).hasSize(1);
        Source src = s.get(0);
        assertThat(src.getPublisher()).as(name + ": publisher").isEqualTo(publisher);
        assertThat(src.getSourceType()).as(name).isEqualTo(type);
        assertThat(src.getSourceQuality()).isEqualTo(quality);
        if (publisherUrl == null) {
            assertThat(src.getPublisherUrl()).isNull();
        } else {
            assertThat(src.getPublisherUrl()).hasToString(publisherUrl);
        }
    }

    // ---- the article retrieval: decoding a Google link (R2, article-retrieval.md FR-54) --------------------------------------

    @Test
    void aGoogleLinkIsDecodedToThePublisherPageAndRead() throws Exception {
        news.site("resolved-1", "Resolved Site");
        List<Source> s = oneItem(base() + "/rss/articles/resolved-1", "Resolved story - Reuters", recent(), "Reuters", REUTERS);
        assertThat(s).hasSize(1);
        Source src = s.get(0);
        assertThat(src.getUrl()).as("the decoded publisher URL, normalised").hasToString(base() + "/articles/resolved-1");
        assertThat(src.getPublisherHost()).isEqualTo("127.0.0.1");
        assertThat(src.getContentStatus()).isEqualTo(ArticleContentStatus.RETRIEVED);
        assertThat(src.getMetadataFetched()).isTrue();
        assertThat(src.getPublisher()).as("og:site_name of the publisher page").isEqualTo("Resolved Site");
        assertThat(src.getSummary()).as("the description of the page").isEqualTo("Summary of resolved-1");
        assertThat(src.getPublisherUrl()).hasToString(REUTERS);
        assertThat(src.getSourceType()).isEqualTo(SourceType.NEWS);
        assertThat(src.getSourceQuality()).isEqualTo(0.85);
        assertThat(src.getTitle()).isEqualTo("Resolved story");
        assertThat(news.articleRequests).containsExactly("resolved-1");
        assertThat(news.decodeRequests).hasSize(1);
        assertThat(news.decodeRequests.get(0).id()).isEqualTo("resolved-1");
        assertThat(news.googlePageRequests.stream().map(StubNews.GooglePageHit::redirect).toList()).as("302 step, then the page").containsExactly(true, false);
    }

    @Test
    void aLinkOnTheGoogleHostWhosePageHasNoAttributesKeepsTheGoogleLink() throws Exception {
        String link = base() + "/articles/direct-1";
        List<Source> s = oneItem(link, "Direct story - Reuters", recent(), "Reuters", REUTERS);
        assertThat(s).hasSize(1);
        Source src = s.get(0);
        assertThat(src.getUrl()).as("no data-n-a-* attributes on the page: decode failed, the Google link stays").hasToString(link);
        assertThat(src.getContentStatus()).isEqualTo(ArticleContentStatus.DECODE_FAILED);
        assertThat(src.getPublisherHost()).isNull();
        assertThat(src.getMetadataFetched()).isFalse();
        assertThat(src.getPublisher()).isEqualTo("Reuters");
        assertThat(src.getSummary()).as("no snippet: the title").isEqualTo("Direct story");
        assertThat(src.getPublisherUrl()).hasToString(REUTERS);
        assertThat(news.decodeRequests).as("nothing to decode").isEmpty();
    }

    static Stream<Arguments> publisherOutcomes() {
        // name, feed link path, expected status, expected metadataFetched
        return Stream.of(
            Arguments.of("decoded page answers 404", "/rss/articles/gone-404", ArticleContentStatus.PAGE_FAILED, false),
            Arguments.of("decoded page is a PDF", "/rss/articles/pdf", ArticleContentStatus.PAGE_FAILED, false),
            // 3 s page under the 8 s article-fetch-timeout (it was a failure under the phase-01 3 s)
            Arguments.of("decoded page needs 3 s", "/rss/articles/slow", ArticleContentStatus.RETRIEVED, true));
    }

    @ParameterizedTest(name = "{0}: {2}, publisher URL kept")
    @MethodSource("publisherOutcomes")
    void theDecodedPublisherUrlIsKeptWhetherOrNotThePageWasRead(String name, String path, ArticleContentStatus status, boolean metadata) throws Exception {
        news.pages.put("gone-404", new StubNews.Page(404, "text/html", "<html>gone</html>"));
        String name2 = path.substring(path.lastIndexOf('/') + 1);
        List<Source> s = oneItem(base() + path, "Publisher story - Reuters", recent(), "Reuters", REUTERS);
        assertThat(s).as(name).hasSize(1);
        Source src = s.get(0);
        assertThat(src.getUrl()).as("the publisher URL, not the Google link").hasToString(base() + "/articles/" + name2);
        assertThat(src.getPublisherHost()).isEqualTo("127.0.0.1");
        assertThat(src.getContentStatus()).as(name).isEqualTo(status);
        assertThat(src.getMetadataFetched()).isEqualTo(metadata);
        assertThat(src.getPublisherUrl()).hasToString(REUTERS);
        if (!metadata) {
            assertThat(src.getPublisher()).isEqualTo("Reuters");
            assertThat(src.getSummary()).isEqualTo("Publisher story");
        }
    }

    static Stream<Arguments> googlePageFailures() {
        return Stream.of(
            Arguments.of("six same-host redirects", "/redirect/6"),
            Arguments.of("a redirect chain that ends on a page without attributes", "/redirect/3"));
    }

    @ParameterizedTest(name = "{0}: DECODE_FAILED, the Google link is kept")
    @MethodSource("googlePageFailures")
    void aGooglePageThatCannotBeReadKeepsTheGoogleLink(String name, String path) throws Exception {
        String link = base() + path;
        List<Source> s = oneItem(link, "Unresolved story - Reuters", recent(), "Reuters", REUTERS);
        assertThat(s).as(name).hasSize(1);
        Source src = s.get(0);
        assertThat(src.getUrl()).hasToString(link);
        assertThat(src.getContentStatus()).isEqualTo(ArticleContentStatus.DECODE_FAILED);
        assertThat(src.getPublisherHost()).isNull();
        assertThat(src.getMetadataFetched()).isFalse();
        assertThat(src.getPublisher()).isEqualTo("Reuters");
        assertThat(src.getSummary()).isEqualTo("Unresolved story");
        assertThat(src.getPublisherUrl()).hasToString(REUTERS);
        assertThat(news.decodeRequests).as("no attributes, no decode call").isEmpty();
        assertThat(news.articleRequests).as("never a publisher fetch").isEmpty();
    }

    // R2 "a resolved URL already taken keeps its Google link" -> merge: two links decoding to one publisher URL are one source
    @Test
    void twoLinksThatDecodeToOnePublisherUrlAreOneSourceListingBothQueries() throws Exception {
        var plan = new SearchPlanHolder(planOf(List.of("alpha one", "beta two")), HorizonCode._1Y);
        news.decoded.put("m2", base() + "/articles/m1");
        news.responder = req -> req.elements().contains("alpha one")
            ? StubNews.rss(StubNews.rssItem("First story - Reuters", base() + "/rss/articles/m1", recent(), "Reuters", REUTERS))
            : StubNews.rss(StubNews.rssItem("Second story - Reuters", base() + "/rss/articles/m2", recent(), "Reuters", REUTERS));
        var outcome = search(plan.plan());
        List<Source> s = new ArrayList<>();
        for (var st : retrieval.readSources(outcome, plan.horizon())) s.add(st.source());
        assertThat(s).as("merged before numbering").hasSize(1);
        Source src = s.get(0);
        assertThat(src.getId()).isEqualTo("S001");
        assertThat(src.getUrl()).hasToString(base() + "/articles/m1");
        assertThat(src.getTitle()).as("the earliest source in kept order keeps its fields").isEqualTo("First story");
        assertThat(src.getQueryIds()).as("sorted union of the merged sources").isEqualTo(List.of("Q01", "Q02"));
        assertThat(src.getContentStatus()).isEqualTo(ArticleContentStatus.RETRIEVED);
        assertThat(news.decodeRequests).as("the decode endpoint is called once per kept Google link").hasSize(2);
    }

    // spec range (i): two links of two different pipelines that decode to one publisher URL
    @Test
    void twoLinksOfTwoPipelinesThatDecodeToOnePublisherUrlAreOneSourceOfBothPipelines() throws Exception {
        SearchPlan plan = PlanSupport.plan(PlanSupport.cfgOfSize(2, 0));
        assertThat(plan.getPipelines()).as("a plan with at least two pipelines").hasSizeGreaterThanOrEqualTo(2);
        WildcardPipeline w1 = plan.getPipelines().get(0);
        WildcardPipeline w2 = plan.getPipelines().get(1);
        PipelineQuery q1 = w1.getQueries().get(0);
        PipelineQuery q2 = w2.getQueries().get(0);
        // the page of the merged source holds one paragraph per pipeline, each carrying the whole first query text of that pipeline
        String para1 = q1.getText() + " is the subject of this paragraph, written for the first pipeline and its own readers alone.";
        String para2 = q2.getText() + " is the subject of this paragraph, written for the second pipeline and its own readers alone.";
        news.pages.put("m1", new StubNews.Page(200, "text/html", "<!doctype html><html><head><title>Merged</title></head><body><article>"
            + "<p>" + StubNews.P1 + "</p><p>" + para1 + "</p><p>" + para2 + "</p><p>" + StubNews.P3 + "</p></article></body></html>"));
        // m2 (found by the second pipeline) decodes to the publisher URL of m1; each pipeline has one more own article
        news.decoded.put("m2", base() + "/articles/m1");
        news.responder = req -> {
            if (req.elements().contains(q1.getText())) {
                return StubNews.rss(
                    StubNews.rssItem("First pipeline story - Reuters", base() + "/rss/articles/m1", recent(), "Reuters", REUTERS),
                    StubNews.rssItem("First pipeline own report - Reuters", base() + "/rss/articles/x1", recent(), "Reuters", REUTERS));
            }
            if (req.elements().contains(q2.getText())) {
                return StubNews.rss(
                    StubNews.rssItem("Second pipeline story - Reuters", base() + "/rss/articles/m2", recent(), "Reuters", REUTERS),
                    StubNews.rssItem("Second pipeline own report - Reuters", base() + "/rss/articles/y1", recent(), "Reuters", REUTERS));
            }
            return StubNews.rss();
        };
        var outcome = search(plan);
        SourceRetrieval.Read read = retrieval.read(outcome, HorizonCode._1Y, () -> true);
        List<Source> s = read.sources().stream().map(SourceRepository.Stored::source).toList();

        // m2 is merged into m1: three sources, ids contiguous in order
        assertThat(s).as("m1 and m2 merged, x1 and y1 kept").hasSize(3);
        assertThat(s).extracting(Source::getId).containsExactly("S001", "S002", "S003");
        assertThat(s.stream().map(x -> x.getUrl().toString()).distinct().count()).as("stored urls distinct").isEqualTo(3L);
        Source merged = s.stream().filter(x -> x.getUrl().toString().equals(base() + "/articles/m1")).findFirst().orElseThrow();
        Source own1 = s.stream().filter(x -> x.getUrl().toString().equals(base() + "/articles/x1")).findFirst().orElseThrow();
        Source own2 = s.stream().filter(x -> x.getUrl().toString().equals(base() + "/articles/y1")).findFirst().orElseThrow();
        assertThat(s.stream().map(x -> x.getUrl().toString())).as("the merged-away link is no source").noneMatch(u -> u.endsWith("/m2"));
        assertThat(merged.getPipelineIds()).as("the union of the pipelineIds, ascending").isEqualTo(List.of(w1.getId(), w2.getId()));
        assertThat(merged.getQueryIds()).as("both queries that returned it, sorted").isEqualTo(
            java.util.stream.Stream.of(q1.getId(), q2.getId()).sorted().toList());
        assertThat(own1.getPipelineIds()).isEqualTo(List.of(w1.getId()));
        assertThat(own2.getPipelineIds()).isEqualTo(List.of(w2.getId()));

        // both pipelines list the survivor; the merged-away source is in neither list, no id twice
        List<WildcardPipeline> pipelines = read.plan().getPipelines();
        WildcardPipeline r1 = pipelines.stream().filter(p -> p.getId().equals(w1.getId())).findFirst().orElseThrow();
        WildcardPipeline r2 = pipelines.stream().filter(p -> p.getId().equals(w2.getId())).findFirst().orElseThrow();
        assertThat(r1.getSourceIds()).as(w1.getId() + " sourceIds").containsExactlyInAnyOrder(merged.getId(), own1.getId());
        assertThat(r2.getSourceIds()).as(w2.getId() + " sourceIds: the survivor replaces the merged-away source").containsExactlyInAnyOrder(
            merged.getId(), own2.getId());
        assertThat(r1.getSourceIds()).doesNotHaveDuplicates();
        assertThat(r2.getSourceIds()).doesNotHaveDuplicates();
        assertThat(java.util.stream.Stream.concat(r1.getSourceIds().stream(), r2.getSourceIds().stream()).distinct().toList())
            .as("every listed id is a source, every source is listed").containsExactlyInAnyOrderElementsOf(s.stream().map(Source::getId).toList());
        for (Source x : s) {
            for (WildcardPipeline p : pipelines) {
                assertThat(p.getSourceIds().contains(x.getId())).as(p.getId() + " lists " + x.getId() + " iff its pipelineIds contain it")
                    .isEqualTo(x.getPipelineIds().contains(p.getId()));
            }
        }
        assertThat(read.sourcesWithContent()).as("every row RETRIEVED").isEqualTo(3);
        assertThat(s).allSatisfy(x -> assertThat(x.getContentStatus()).isEqualTo(ArticleContentStatus.RETRIEVED));
        assertThat(news.articleRequests).as("the page of the merged source was fetched").contains("m1");

        // extraction runs per pipeline of the merged source: an excerpt for each pipeline that got a fragment, own paragraph strongest
        assertThat(merged.getExcerpts()).as("one excerpt per pipeline").extracting(e -> e.getPipelineId()).containsExactly(w1.getId(), w2.getId());
        assertThat(merged.getExcerpts().get(0).getFragments().get(0)).as(w1.getId() + " strongest fragment").isEqualTo(para1);
        assertThat(merged.getExcerpts().get(1).getFragments().get(0)).as(w2.getId() + " strongest fragment").isEqualTo(para2);
        for (var e : merged.getExcerpts()) {
            assertThat(merged.getPipelineIds()).as("excerpt pipelineId in the source's pipelineIds").contains(e.getPipelineId());
        }
    }

    @Test
    void twoGoogleLinksOfOneArticleWithTheSameIdAreOneSource() throws Exception {
        List<Source> s = sources(one(),
            StubNews.rssItem("First story - Reuters", base() + "/rss/articles/same", recent(), "Reuters", REUTERS),
            StubNews.rssItem("Second story - Reuters", base() + "/rss/articles/same?second=1", recent(), "Reuters", REUTERS));
        assertThat(s).hasSize(1);
        assertThat(s.get(0).getId()).isEqualTo("S001");
        assertThat(s.get(0).getUrl()).hasToString(base() + "/articles/same");
        assertThat(s.get(0).getMetadataFetched()).isTrue();
        assertThat(s.stream().map(x -> x.getUrl().toString()).distinct().count()).isEqualTo(s.size());
    }

    @Test
    void googleSourcesCarryTopicAndQueryIds() throws Exception {
        var plan = new SearchPlanHolder(planOf(List.of("alpha one", "beta two")), HorizonCode._1Y);
        news.responder = req -> req.elements().contains("alpha one")
            ? StubNews.rss(StubNews.rssItem("Alpha one arrives - Reuters", base() + "/articles/q-a", recent(), "Reuters", REUTERS))
            : StubNews.rss(StubNews.rssItem("Beta two arrives - Reuters", base() + "/articles/q-b", recent(), "Reuters", REUTERS));
        var outcome = search(plan.plan());
        List<Source> s = new ArrayList<>();
        for (var st : retrieval.readSources(outcome, plan.horizon())) s.add(st.source());
        assertThat(s).extracting(Source::getId).containsExactly("S001", "S002");
        assertThat(s.get(0).getQueryIds()).as("plan order, then feed order: the item of query 1 first").isEqualTo(List.of("Q01"));
        assertThat(s.get(1).getQueryIds()).isEqualTo(List.of("Q02"));
        assertThat(s.get(0).getTopic()).isNotNull();
    }

    @Test
    void anArticleReturnedByTwoQueriesIsOneSourceListingBoth() throws Exception {
        var plan = new SearchPlanHolder(planOf(List.of("alpha one", "beta two", "gamma three")), HorizonCode._1Y);
        news.responder = req -> req.elements().contains("gamma three") ? StubNews.rss()
            : StubNews.rss(StubNews.rssItem("Common story - Reuters", base() + "/articles/q-common?utm_source=q" + req.number(), recent(), "Reuters", REUTERS));
        var outcome = search(plan.plan());
        List<Source> s = new ArrayList<>();
        for (var st : retrieval.readSources(outcome, plan.horizon())) s.add(st.source());
        assertThat(s).hasSize(1);
        assertThat(s.get(0).getQueryIds()).as("exactly the queries whose own answer held its URL, sorted").isEqualTo(List.of("Q01", "Q02"));
        assertThat(outcome.articlesRetrieved()).as("both answers are counted").isEqualTo(2);
    }
}
