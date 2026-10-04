package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-02 news-search.md FR-44 step 7: every article of an answered group is attributed to exactly one element of that
 * group by its title: contiguous phrase first (group order), else most distinct shared tokens (ties: earlier), else the
 * first element. One group holds all queries here (max-requests = 1).
 */
// @trace FR-44
@TestPropertySource(properties = {
    "oracul.news.request-spacing=PT0S",
    "oracul.news.rate-limit-wait=PT0S",
    "oracul.news.max-requests=1",
})
class NewsSearchAttributionIT extends AbstractNewsSearchIT {

    private static final List<String> TWO = List.of("alpha beta", "gamma delta");

    static Stream<Arguments> classes() {
        return Stream.of(
            Arguments.of("exact phrase wins although a later element shares more tokens",
                List.of("solar eclipse", "solar wind eclipse storm"), "Total solar eclipse and solar wind storm", 0),
            Arguments.of("phrase of a later element", List.of("apple pie", "banana split"), "Banana split recipe", 1),
            Arguments.of("two phrases occur: the first in group order", List.of("apple pie", "banana split"),
                "banana split and apple pie", 0),
            Arguments.of("no phrase, unequal overlap: the highest overlap", List.of("alpha beta", "gamma delta epsilon"),
                "delta epsilon news", 1),
            Arguments.of("no phrase, equal overlap: the earlier element", TWO, "beta delta report", 0),
            Arguments.of("no phrase, three-way tie of two: the earlier of them", List.of("a1 b1 c1", "b1 c1 d1", "c1 d1 e1"),
                "c1 d1 x", 1),
            Arguments.of("distinct tokens count once", List.of("alpha zzz", "beta gamma delta"),
                "alpha alpha alpha alpha beta delta", 1),
            Arguments.of("no shared token: the first element", TWO, "completely unrelated words", 0),
            Arguments.of("blank title: the first element", TWO, "   ", 0),
            Arguments.of("normalisation is case-insensitive and ignores punctuation", List.of("other thing", "Mars-Rover Launch"),
                "NASA's MARS ROVER LAUNCH, finally!", 1),
            Arguments.of("a later element's phrase beats an earlier element's partial overlap", List.of("solar eclipse totality", "dark moon"),
                "solar eclipse in the dark moon", 1));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("classes")
    void anArticleIsAttributedToExactlyOneElementOfItsGroup(String name, List<String> elements, String title, int expected)
        throws Exception {
        String url = gdelt.baseUrl() + "/articles/attr";
        gdelt.responder = req -> StubGdelt.json(StubGdelt.articles(List.of(article(url, title))));
        var outcome = search(planOf(elements));
        assertThat(gdelt.requests).hasSize(1);
        assertThat(gdelt.requests.get(0).elements()).as("one group with every element").containsExactlyElementsOf(elements);
        for (int i = 0; i < elements.size(); i++) {
            SearchQuery q = outcome.plan().getQueries().get(i);
            assertThat(q.getArticlesReturned()).as(name + ": articlesReturned of " + q.getId()).isEqualTo(i == expected ? 1 : 0);
            assertThat(q.getStatus()).as(q.getId()).isEqualTo(i == expected ? SearchQueryStatus.OK : SearchQueryStatus.EMPTY);
            assertThat(outcome.articles().get(q.getId())).hasSize(i == expected ? 1 : 0);
        }
    }

    @Test
    void anArticleWithoutATitleFieldGoesToTheFirstElement() throws Exception {
        String raw = "{\"articles\":[{\"url\":\"" + gdelt.baseUrl() + "/articles/notitle\",\"domain\":\"reuters.com\","
            + "\"language\":\"English\",\"seendate\":\"" + StubGdelt.seendate(java.time.Instant.now().minusSeconds(3600)) + "\"}]}";
        gdelt.responder = req -> StubGdelt.json(raw);
        var outcome = search(planOf(TWO));
        assertThat(outcome.plan().getQueries().get(0).getArticlesReturned()).isEqualTo(1);
        assertThat(outcome.plan().getQueries().get(1).getArticlesReturned()).isEqualTo(0);
    }

    @Test
    void everyEntryIsAttributedOnceInResponseOrderAndTheTotalsAgree() throws Exception {
        List<String> elements = List.of("solar eclipse", "wind farm", "mars rover", "fusion plant");
        List<String> titles = List.of("Solar eclipse draws crowds", "Wind farm approved", "Rover on Mars wakes", "Unrelated headline",
            "Fusion plant opens", "Wind farm and fusion plant", "The solar eclipse", "Nothing to see");
        List<String> articles = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) articles.add(article(gdelt.baseUrl() + "/articles/e" + i, titles.get(i)));
        gdelt.responder = req -> StubGdelt.json(StubGdelt.articles(articles));
        var outcome = search(planOf(elements));
        int[] expected = {0, 1, 2, 0, 3, 1, 0, 0}; // phrase / overlap / first element
        int[] counts = new int[elements.size()];
        for (int e : expected) counts[e]++;
        int total = 0;
        for (int i = 0; i < elements.size(); i++) {
            SearchQuery q = outcome.plan().getQueries().get(i);
            assertThat(q.getArticlesReturned()).as(q.getId()).isEqualTo(counts[i]);
            assertThat(q.getStatus()).isEqualTo(counts[i] > 0 ? SearchQueryStatus.OK : SearchQueryStatus.EMPTY);
            total += q.getArticlesReturned();
            List<String> urls = outcome.articles().get(q.getId()).stream().map(NewsProvider.Article::url).toList();
            List<String> expectedUrls = new ArrayList<>();
            for (int a = 0; a < expected.length; a++) if (expected[a] == i) expectedUrls.add(gdelt.baseUrl() + "/articles/e" + a);
            assertThat(urls).as("response order within " + q.getId()).containsExactlyElementsOf(expectedUrls);
        }
        assertThat(total).as("Σ articlesReturned = entries of the answered group").isEqualTo(titles.size());
        assertThat(outcome.articlesRetrieved()).isEqualTo(titles.size());
    }
}
