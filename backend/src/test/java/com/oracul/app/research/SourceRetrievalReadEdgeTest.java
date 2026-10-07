package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.QueryBucket;
import com.oracul.app.api.model.QueryExpansionMode;
import com.oracul.app.api.model.SearchIntent;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * FR-53 / FR-54 on {@code SourceRetrieval.read} without Spring or network (a plan without pipelines, the phase-01 shape; the article
 * retriever is a scripted double): the topic of a source from the intent of its query, and the snippet fallback summary cut to 600.
 */
// @trace FR-53
// @trace FR-54
@Timeout(30)
class SourceRetrievalReadEdgeTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC);

    private SourceRetrieval retrieval() throws Exception {
        ArticleRetriever retriever = mock(ArticleRetriever.class);
        when(retriever.retrieveAll(anyList(), any(), any())).thenAnswer(inv -> {
            List<String> links = inv.getArgument(0);
            return links.stream().map(l -> new ArticleRetriever.Outcome(ArticleRetriever.Status.DECODE_FAILED, null, null, null, null,
                null, CLOCK.instant())).toList();
        });
        return new SourceRetrieval(mock(NewsSearchProvider.class), retriever,
            new SourceQualityTable("who.int", "nature.com", "reuters.com", "medium.com"), CLOCK, Duration.ofSeconds(60), Duration.ofSeconds(90));
    }

    private static SourceRetrieval.SearchOutcome outcome(List<SearchIntent> intents, String intentId, QueryBucket bucket, NewsProvider.Article article) {
        SearchQuery q = new SearchQuery("Q01", intentId, bucket, "grid strain", SearchQueryStatus.OK, 1);
        SearchPlan plan = new SearchPlan(1, QueryExpansionMode.TEMPLATE_FALLBACK, new ArrayList<>(), intents, List.of(q));
        Map<String, List<NewsProvider.Article>> byQuery = new LinkedHashMap<>();
        byQuery.put("Q01", List.of(article));
        return new SourceRetrieval.SearchOutcome(plan, byQuery, List.of(new SourceRetrieval.Attributed("Q01", article)));
    }

    private static NewsProvider.Article article(String snippet) {
        return new NewsProvider.Article("https://publisher.example/articles/one", "Grid strain", null, "Wire", null, snippet);
    }

    static Stream<Arguments> topics() {
        return Stream.of(
            Arguments.of("WILDCARD with a topic key", QueryBucket.WILDCARD, "robotics-boom", null, "robotics-boom"),
            Arguments.of("WILDCARD without a topic key", QueryBucket.WILDCARD, null, null, null),
            Arguments.of("ADJACENT with a category", QueryBucket.ADJACENT, null, "health", "health"),
            Arguments.of("ADJACENT without a category", QueryBucket.ADJACENT, null, null, "general"),
            Arguments.of("MAJOR", QueryBucket.MAJOR, null, null, "major"),
            Arguments.of("UNEXPECTED", QueryBucket.UNEXPECTED, null, null, "unexpected"));
    }

    @ParameterizedTest(name = "{0} -> topic {4}")
    @MethodSource("topics")
    void theTopicOfASourceOfAPlanWithoutPipelinesComesFromTheIntentOfItsQuery(String name, QueryBucket bucket, String topicKey,
                                                                              String category, String expected) throws Exception {
        SearchIntent intent = new SearchIntent("I01", bucket, "an intent", List.of("driver")).topicKey(topicKey).category(category);
        SourceRetrieval.Read read = retrieval().read(outcome(List.of(intent), "I01", bucket, article(null)), HorizonCode._1Y, () -> true);
        assertThat(read.sources()).hasSize(1);
        assertThat(read.sources().get(0).source().getTopic()).as(name).isEqualTo(expected);
    }

    @ParameterizedTest(name = "every bucket of the enum is mapped: {0}")
    @MethodSource("everyBucket")
    void everyBucketGivesASourceTopic(QueryBucket bucket) throws Exception {
        SearchIntent intent = new SearchIntent("I01", bucket, "an intent", List.of("driver")).topicKey("tk").category("cat");
        SourceRetrieval.Read read = retrieval().read(outcome(List.of(intent), "I01", bucket, article(null)), HorizonCode._1Y, () -> true);
        String expected = switch (bucket) {
            case WILDCARD -> "tk";
            case ADJACENT -> "cat";
            case MAJOR -> "major";
            case UNEXPECTED -> "unexpected";
        };
        assertThat(read.sources().get(0).source().getTopic()).isEqualTo(expected);
    }

    static Stream<QueryBucket> everyBucket() {
        return Stream.of(QueryBucket.values());
    }

    @org.junit.jupiter.api.Test
    void aQueryWhoseIntentIsNotInThePlanHasNoTopic() throws Exception {
        SearchIntent other = new SearchIntent("I99", QueryBucket.MAJOR, "another intent", List.of("driver"));
        SourceRetrieval.Read read = retrieval().read(outcome(List.of(other), "I01", QueryBucket.MAJOR, article(null)), HorizonCode._1Y, () -> true);
        assertThat(read.sources()).hasSize(1);
        assertThat(read.sources().get(0).source().getTopic()).isNull();
    }

    @ParameterizedTest(name = "snippet of {0} characters")
    @ValueSource(ints = {1, 599, 600, 601, 700, 5000})
    void theFallbackSummaryIsTheSnippetCutTo600Characters(int length) throws Exception {
        String snippet = IntStream.range(0, length).mapToObj(i -> String.valueOf((char) ('a' + i % 26))).reduce("", String::concat);
        SearchIntent intent = new SearchIntent("I01", QueryBucket.MAJOR, "an intent", List.of("driver"));
        SourceRetrieval.Read read = retrieval().read(outcome(List.of(intent), "I01", QueryBucket.MAJOR, article(snippet)), HorizonCode._1Y, () -> true);
        String summary = read.sources().get(0).source().getSummary();
        assertThat(summary).hasSize(Math.min(length, 600)).isEqualTo(snippet.substring(0, Math.min(length, 600)));
    }

    @org.junit.jupiter.api.Test
    void aBlankSnippetFallsBackToTheTitle() throws Exception {
        SearchIntent intent = new SearchIntent("I01", QueryBucket.MAJOR, "an intent", List.of("driver"));
        SourceRetrieval.Read read = retrieval().read(outcome(List.of(intent), "I01", QueryBucket.MAJOR, article("   ")), HorizonCode._1Y, () -> true);
        assertThat(read.sources().get(0).source().getSummary()).isEqualTo("Grid strain");
    }
}
