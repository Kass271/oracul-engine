package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-02 news-search.md FR-44 steps 1-3 and 8: the planned queries go out as at most {@code max-requests} OR-group
 * requests, contiguous and balanced (larger groups first), one element per query, exact parameters. Walks the whole
 * range n' = 1..20 of planned queries; the concrete subclasses pin max-requests to each class (default, 1, 0, -1, 10).
 */
// @trace FR-44
@TestPropertySource(properties = {
    "oracul.news.request-spacing=PT0S",
    "oracul.news.rate-limit-wait=PT0S",
    "oracul.news.max-requests=4",
})
class NewsSearchGroupingIT extends AbstractNewsSearchIT {

    /** max-requests of this context as configured (values below 1 count as 1). */
    protected int configuredMaxRequests() {
        return 4;
    }

    private int effectiveMax() {
        return Math.max(1, configuredMaxRequests());
    }

    static Stream<Arguments> queryCounts() {
        return IntStream.rangeClosed(1, 20).mapToObj(Arguments::of);
    }

    /** Expected group sizes: G = min(max, n'), sizes differ by at most 1, the larger groups first. */
    static List<Integer> expectedSizes(int n, int max) {
        int g = Math.min(max, n);
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < g; i++) sizes.add(n / g + (i < n % g ? 1 : 0));
        return sizes;
    }

    @ParameterizedTest(name = "{0} planned queries")
    @MethodSource("queryCounts")
    void thePlannedQueriesAreSplitIntoContiguousBalancedOrGroups(int n) throws Exception {
        var plan = planOfSize(n);
        var outcome = search(plan);
        List<Integer> sizes = expectedSizes(n, effectiveMax());
        assertThat(gdelt.requests).as("requests for " + n + " queries, max-requests " + configuredMaxRequests()).hasSize(sizes.size());
        List<String> sentElements = new ArrayList<>();
        for (int g = 0; g < sizes.size(); g++) {
            StubGdelt.Request req = gdelt.requests.get(g);
            assertThat(req.elements()).as("group " + (g + 1)).hasSize(sizes.get(g));
            sentElements.addAll(req.elements());
            assertThat(req.params().keySet()).containsExactlyInAnyOrder("query", "mode", "format", "maxrecords", "sort", "timespan");
            assertThat(req.params().get("mode")).isEqualTo("ArtList");
            assertThat(req.params().get("format")).isEqualTo("json");
            assertThat(req.params().get("sort")).isEqualTo("HybridRel");
            assertThat(req.params().get("timespan")).isEqualTo("3months");
            assertThat(req.params().get("maxrecords")).as("min(250, 25 x size)")
                .isEqualTo(String.valueOf(Math.min(250, 25 * sizes.get(g))));
            assertThat(req.rawQuery()).as("URL-encoded").doesNotContain(" ").doesNotContain("\"");
            String query = req.params().get("query");
            if (sizes.get(g) == 1) {
                assertThat(query).as("a single element has no parentheses").doesNotStartWith("(").doesNotContain(" OR ")
                    .isEqualTo("\"" + req.elements().get(0) + "\" sourcelang:english");
            } else {
                assertThat(query).startsWith("(\"").endsWith("\") sourcelang:english").contains("\" OR \"");
                assertThat(query.split(" OR ", -1)).hasSize(sizes.get(g));
                String expectedQuery = "(" + req.elements().stream().map(e -> "\"" + e + "\"")
                    .collect(java.util.stream.Collectors.joining(" OR ")) + ") sourcelang:english";
                assertThat(query).isEqualTo(expectedQuery);
            }
        }
        List<String> planTexts = plan.getQueries().stream().map(SearchQuery::getText).toList();
        assertThat(sentElements).as("union = all queries, each in exactly one group, plan order").containsExactlyElementsOf(planTexts);
        // per-query result: nothing was answered, so every query is EMPTY (never FAILED, never lost)
        assertThat(outcome.plan().getQueries()).hasSize(n);
        for (SearchQuery q : outcome.plan().getQueries()) {
            assertThat(q.getStatus()).as(q.getId()).isEqualTo(SearchQueryStatus.EMPTY);
            assertThat(q.getArticlesReturned()).isEqualTo(0);
        }
        assertThat(outcome.searches()).isEqualTo(n);
        assertThat(outcome.articlesRetrieved()).isEqualTo(0);
        assertThat(outcome.allFailed()).isFalse();
    }

    @Test
    void aSingleQueryIsSentWithoutParentheses() throws Exception {
        search(planOf(List.of("mRNA vaccine approval")));
        assertThat(gdelt.requests).hasSize(1);
        assertThat(gdelt.requests.get(0).params().get("query")).isEqualTo("\"mRNA vaccine approval\" sourcelang:english");
        assertThat(gdelt.requests.get(0).params().get("maxrecords")).isEqualTo("25");
    }

    @Test
    void theOrQueryOfAGroupIsParenthesisedAndCarriesTheLanguageFilter() throws Exception {
        search(planOf(List.of("fusion energy", "robot strike")));
        // n' = 2 with max-requests >= 2 gives two groups of one; with max-requests 1 one group of two
        if (effectiveMax() == 1) {
            assertThat(gdelt.requests).hasSize(1);
            assertThat(gdelt.requests.get(0).params().get("query"))
                .isEqualTo("(\"fusion energy\" OR \"robot strike\") sourcelang:english");
            assertThat(gdelt.requests.get(0).params().get("maxrecords")).isEqualTo("50");
        } else {
            assertThat(gdelt.requests).hasSize(2);
            assertThat(gdelt.requests.get(0).params().get("query")).isEqualTo("\"fusion energy\" sourcelang:english");
            assertThat(gdelt.requests.get(1).params().get("query")).isEqualTo("\"robot strike\" sourcelang:english");
        }
    }

    // ---- the element of a query ----------------------------------------------------------------------------------

    static Stream<Arguments> elementClasses() {
        return Stream.of(
            Arguments.of("vaccines", "vaccines"),
            Arguments.of("mRNA vaccine approval", "\"mRNA vaccine approval\""),
            Arguments.of("\"quoted\" (paren) text", "\"quoted paren text\""),
            Arguments.of("fusion OR fission", "\"fusion fission\""),
            Arguments.of("fusion AND fission NOT war", "\"fusion fission war\""),
            Arguments.of("OR vaccines AND", "vaccines"),
            Arguments.of("rock or roll", "\"rock or roll\""),
            Arguments.of("NOTHING ORBIT ANDROID", "\"NOTHING ORBIT ANDROID\""),
            Arguments.of("  many   spaces\there  ", "\"many spaces here\""),
            Arguments.of("(vaccines)", "vaccines"),
            Arguments.of("\"vaccines\"", "vaccines"));
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @MethodSource("elementClasses")
    void theElementOfAQueryIsItsCleanedText(String text, String expectedElement) throws Exception {
        search(planOf(List.of(text)));
        assertThat(gdelt.requests).hasSize(1);
        assertThat(gdelt.requests.get(0).params().get("query")).isEqualTo(expectedElement + " sourcelang:english");
    }

    static Stream<Arguments> emptyElements() {
        return Stream.of(Arguments.of("   "), Arguments.of("\"()\""), Arguments.of("()"), Arguments.of("OR"), Arguments.of("AND NOT OR"),
            Arguments.of(""));
    }

    @ParameterizedTest(name = "empty element \"{0}\"")
    @MethodSource("emptyElements")
    void aQueryWithoutAnElementIsNotSentAndIsEmpty(String text) throws Exception {
        var outcome = search(planOf(List.of(text)));
        assertThat(gdelt.requests).as("n' = 0: no request").isEmpty();
        assertThat(outcome.plan().getQueries()).hasSize(1);
        assertThat(outcome.plan().getQueries().get(0).getStatus()).isEqualTo(SearchQueryStatus.EMPTY);
        assertThat(outcome.plan().getQueries().get(0).getArticlesReturned()).isEqualTo(0);
        assertThat(outcome.allFailed()).as("not sent because empty is no failure").isFalse();
    }

    @Test
    void emptyQueriesAreSkippedWhenGrouping() throws Exception {
        var outcome = search(planOf(List.of("vaccines", "   ", "fusion OR fission", "\"()\"", "robot strike")));
        // n' = 3 (queries 1, 3 and 5): groups of the non-empty ones only
        List<List<String>> groups = gdelt.requests.stream().map(StubGdelt.Request::elements).toList();
        List<String> all = groups.stream().flatMap(List::stream).toList();
        assertThat(all).containsExactly("vaccines", "fusion fission", "robot strike");
        assertThat(groups).hasSize(Math.min(effectiveMax(), 3));
        assertThat(outcome.plan().getQueries().get(1).getStatus()).isEqualTo(SearchQueryStatus.EMPTY);
        assertThat(outcome.plan().getQueries().get(3).getStatus()).isEqualTo(SearchQueryStatus.EMPTY);
        assertThat(outcome.searches()).as("counts.searches stays the number of planned queries").isEqualTo(5);
    }

    // ---- answers per group -------------------------------------------------------------------------------------------

    @Test
    void aFailingGroupFailsEveryQueryOfThatGroupOnlyAndAGroupAnswerIsSharedByItsQueries() throws Exception {
        gdelt.responder = req -> req.number() == 1 ? StubGdelt.status(503)
            : StubGdelt.json(StubGdelt.articles(List.of(article(gdelt.baseUrl() + "/articles/g" + req.number(), "g" + req.number()))));
        var plan = planOfSize(8);
        var outcome = search(plan);
        List<Integer> sizes = expectedSizes(8, effectiveMax());
        int firstGroup = sizes.get(0);
        for (int i = 0; i < 8; i++) {
            SearchQuery q = outcome.plan().getQueries().get(i);
            if (i < firstGroup) {
                assertThat(q.getStatus()).as(q.getId()).isEqualTo(SearchQueryStatus.FAILED);
                assertThat(q.getArticlesReturned()).isEqualTo(0);
            }
        }
        if (sizes.size() == 1) {
            assertThat(outcome.allFailed()).isTrue();
        } else {
            assertThat(outcome.allFailed()).as("only some groups failed").isFalse();
            assertThat(outcome.articlesRetrieved()).isEqualTo(sizes.size() - 1);
        }
        int sum = outcome.plan().getQueries().stream().mapToInt(SearchQuery::getArticlesReturned).sum();
        assertThat(outcome.articles().values().stream().mapToInt(List::size).sum()).as("Σ articlesReturned = Σ attributed entries").isEqualTo(sum);
    }
}
