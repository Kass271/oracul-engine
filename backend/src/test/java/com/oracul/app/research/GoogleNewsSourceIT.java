package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import com.oracul.app.api.model.Source;
import com.oracul.app.api.model.SourceType;
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
 * phase-02 news-search.md FR-48 steps 3, 4 and 8: what a Google News RSS item becomes — title cleaning, attribution on the
 * cleaned title, pubDate and link classes, the article fetch that resolves a Google link (or does not), publisher and
 * publisherUrl, source type and quality from the publisher host. The search stage always sends min(4, n) group requests,
 * so a plan whose elements must share a group is padded with filler queries (4 x k queries: group 1 = the k real ones).
 */
// @trace FR-48
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

    /**
     * A plan whose first {@code real.size()} queries share group 1: one real element needs no padding, k >= 2 elements are
     * followed by 3k filler queries (4 groups of k elements; the fillers match no title).
     */
    private static com.oracul.app.api.model.SearchPlan sharedGroupPlan(List<String> real) {
        List<String> texts = new ArrayList<>(real);
        if (real.size() > 1) {
            for (int i = 0; i < 3 * real.size(); i++) texts.add("filler" + i + " zzz");
        }
        return planOf(texts);
    }

    /** The feed is answered to the request of group 1 (the one holding the first element); every other group gets an empty feed. */
    private static java.util.function.Function<StubNews.Request, StubNews.Reply> groupOneAnswers(String firstElement, StubNews.Reply answer) {
        return req -> req.elements().contains(firstElement) ? answer : StubNews.rss();
    }

    /** Search + readSources over a feed of the given items for the plan; one Google request per group, no other request. */
    private List<Source> sources(SearchPlanHolder plan, String... items) throws Exception {
        String first = plan.plan().getQueries().get(0).getText();
        news.responder = groupOneAnswers(first, StubNews.rss(items));
        var outcome = search(plan.plan());
        assertThat(news.requests).hasSize(Math.min(4, plan.plan().getQueries().size()));
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

    @Test
    void attributionUsesTheCleanedTitle() throws Exception {
        // uncleaned, the title contains the phrase of element 1 ("reuters wire") and would be attributed there
        var plan = new SearchPlanHolder(sharedGroupPlan(List.of("reuters wire", "solar eclipse")), HorizonCode._1Y);
        news.responder = groupOneAnswers("reuters wire", StubNews.rss(StubNews.rssItem("Solar eclipse draws crowds - Reuters wire",
            base() + "/articles/clean", recent(), "Reuters wire", REUTERS)));
        var outcome = search(plan.plan());
        assertThat(news.requests).as("4 groups of 2").hasSize(4);
        List<SearchQuery> q = outcome.plan().getQueries();
        assertThat(q.get(0).getStatus()).isEqualTo(SearchQueryStatus.EMPTY);
        assertThat(q.get(1).getStatus()).as("cleaned title 'Solar eclipse draws crowds' matches element 2").isEqualTo(SearchQueryStatus.OK);
        assertThat(q.get(1).getArticlesReturned()).isEqualTo(1);
        assertThat(outcome.articles().get("Q02").get(0).title()).isEqualTo("Solar eclipse draws crowds");
    }

    @Test
    void entriesOfAnAnsweredGroupAreAttributedByPhraseThenOverlapThenFirstElement() throws Exception {
        List<String> elements = List.of("solar eclipse", "wind farm", "mars rover", "fusion plant");
        List<String> titles = List.of("Solar eclipse draws crowds", "Wind farm approved", "Rover on Mars wakes", "Unrelated headline",
            "Fusion plant opens", "Wind farm and fusion plant", "The solar eclipse", "Nothing to see");
        String[] items = new String[titles.size()];
        for (int i = 0; i < titles.size(); i++) {
            items[i] = StubNews.rssItem(titles.get(i) + " - Reuters", base() + "/articles/e" + i, recent(), "Reuters", REUTERS);
        }
        news.responder = groupOneAnswers("solar eclipse", StubNews.rss(items));
        var outcome = search(sharedGroupPlan(elements));
        int[] expected = {0, 1, 2, 0, 3, 1, 0, 0}; // phrase / overlap / first element
        int[] counts = new int[elements.size()];
        for (int e : expected) counts[e]++;
        for (int i = 0; i < elements.size(); i++) {
            SearchQuery q = outcome.plan().getQueries().get(i);
            assertThat(q.getArticlesReturned()).as(q.getId()).isEqualTo(counts[i]);
            assertThat(q.getStatus()).isEqualTo(counts[i] > 0 ? SearchQueryStatus.OK : SearchQueryStatus.EMPTY);
        }
        assertThat(outcome.articlesRetrieved()).isEqualTo(titles.size());
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

    // ---- the article fetch: resolving a Google link ------------------------------------------------------------------------

    @Test
    void aRedirectToAnHtmlPageResolvesTheLink() throws Exception {
        news.site("resolved-1", "Resolved Site");
        List<Source> s = oneItem(base() + "/rss/articles/resolved-1", "Resolved story - Reuters", recent(), "Reuters", REUTERS);
        assertThat(s).hasSize(1);
        Source src = s.get(0);
        assertThat(src.getUrl()).as("the final URL, normalised").hasToString(base() + "/articles/resolved-1");
        assertThat(src.getMetadataFetched()).isTrue();
        assertThat(src.getPublisher()).as("og:site_name of the page").isEqualTo("Resolved Site");
        assertThat(src.getSummary()).as("the description of the page").isEqualTo("Summary of resolved-1");
        assertThat(src.getPublisherUrl()).hasToString(REUTERS);
        assertThat(src.getSourceType()).isEqualTo(SourceType.NEWS);
        assertThat(src.getSourceQuality()).isEqualTo(0.85);
        assertThat(src.getTitle()).isEqualTo("Resolved story");
        assertThat(news.articleRequests).contains("resolved-1");
    }

    @Test
    void anAnswerWithoutARedirectKeepsTheGoogleLink() throws Exception {
        String link = base() + "/articles/direct-1";
        List<Source> s = oneItem(link, "Direct story - Reuters", recent(), "Reuters", REUTERS);
        assertThat(s).hasSize(1);
        Source src = s.get(0);
        assertThat(src.getUrl()).as("no redirect: real Google answers its own page").hasToString(link);
        assertThat(src.getMetadataFetched()).isFalse();
        assertThat(src.getPublisher()).isEqualTo("Reuters");
        assertThat(src.getSummary()).as("the title").isEqualTo("Direct story");
        assertThat(src.getPublisherUrl()).hasToString(REUTERS);
    }

    static Stream<Arguments> unresolved() {
        return Stream.of(
            Arguments.of("redirect to a 404", "/rss/articles/gone-404"),
            Arguments.of("redirect to a page that outlasts the fetch timeout", "/rss/articles/slow"),
            Arguments.of("redirect to a PDF", "/rss/articles/pdf"),
            Arguments.of("six redirects", "/redirect/6"));
    }

    @ParameterizedTest(name = "{0}: the Google link is kept, metadataFetched false")
    @MethodSource("unresolved")
    void aFetchThatDoesNotEndInAnHtmlPageKeepsTheGoogleLink(String name, String path) throws Exception {
        news.pages.put("gone-404", new StubNews.Page(404, "text/html", "<html>gone</html>"));
        String link = base() + path;
        List<Source> s = oneItem(link, "Unresolved story - Reuters", recent(), "Reuters", REUTERS);
        assertThat(s).as(name).hasSize(1);
        Source src = s.get(0);
        assertThat(src.getUrl()).hasToString(link);
        assertThat(src.getMetadataFetched()).isFalse();
        assertThat(src.getPublisher()).isEqualTo("Reuters");
        assertThat(src.getSummary()).isEqualTo("Unresolved story");
        assertThat(src.getPublisherUrl()).hasToString(REUTERS);
    }

    @Test
    void aResolvedUrlThatAnEarlierSourceAlreadyHasIsNotUsedTwice() throws Exception {
        List<Source> s = sources(one(),
            StubNews.rssItem("First story - Reuters", base() + "/rss/articles/same", recent(), "Reuters", REUTERS),
            StubNews.rssItem("Second story - Reuters", base() + "/rss/articles/same?second=1", recent(), "Reuters", REUTERS));
        assertThat(s).hasSize(2);
        assertThat(s.get(0).getId()).isEqualTo("S001");
        assertThat(s.get(0).getUrl()).hasToString(base() + "/articles/same");
        assertThat(s.get(0).getMetadataFetched()).isTrue();
        assertThat(s.get(1).getId()).isEqualTo("S002");
        assertThat(s.get(1).getUrl().toString()).as("the later source keeps its Google link: urls stay unique per run")
            .startsWith(base() + "/rss/articles/same").isNotEqualTo(s.get(0).getUrl().toString());
        assertThat(s.get(1).getMetadataFetched()).isFalse();
        assertThat(s.stream().map(x -> x.getUrl().toString()).distinct().count()).isEqualTo(2);
    }

    @Test
    void googleSourcesCarryTopicAndQueryIds() throws Exception {
        var plan = new SearchPlanHolder(sharedGroupPlan(List.of("alpha one", "beta two")), HorizonCode._1Y);
        List<Source> s = sources(plan,
            StubNews.rssItem("Beta two arrives - Reuters", base() + "/articles/q-b", recent(), "Reuters", REUTERS),
            StubNews.rssItem("Alpha one arrives - Reuters", base() + "/articles/q-a", recent(), "Reuters", REUTERS));
        assertThat(s).extracting(Source::getId).containsExactly("S001", "S002");
        assertThat(s.get(0).getQueryIds()).as("response order within the group").isEqualTo(List.of("Q02"));
        assertThat(s.get(1).getQueryIds()).isEqualTo(List.of("Q01"));
        assertThat(s.get(0).getTopic()).isNotNull();
    }
}
