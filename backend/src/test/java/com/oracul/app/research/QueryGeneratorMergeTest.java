package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.PipelineQuery;
import com.oracul.app.api.model.QueryExpansionMode;
import com.oracul.app.api.model.SearchQueryStatus;
import com.oracul.app.api.model.WildcardPipeline;
import com.oracul.app.api.model.WildcardPipelineKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * QueryGenerator.merge / parse (pure, package-private statics) - wildcard-search.md FR-51 step 5 and the post-processing classes
 * (c): trim and collapse, drop rule-Q breakers and case-insensitive duplicates, keep the first q, fill from the templates.
 */
// @trace FR-51
class QueryGeneratorMergeTest {

    static final List<String> TEMPLATES = List.of("New pandemic extreme scenario disaster",
        "New pandemic unprecedented scale catastrophe", "New pandemic radical upheaval collapse");

    private static WildcardPipeline template(int q) {
        List<PipelineQuery> queries = new ArrayList<>();
        for (int i = 0; i < q; i++) {
            queries.add(new PipelineQuery(String.format("Q%02d", i + 4), TEMPLATES.get(i), SearchQueryStatus.PENDING, 0));
        }
        return new WildcardPipeline("W02", WildcardPipelineKind.CATALOGUE, "New pandemic", "New pandemic 8/10",
            QueryExpansionMode.TEMPLATE_FALLBACK, queries).level(8).topicKey("biology-new-pandemic");
    }

    private static List<String> texts(WildcardPipeline w) {
        return w.getQueries().stream().map(PipelineQuery::getText).toList();
    }

    private static WildcardPipeline merge(int q, String... model) {
        return QuerySupport.merge(template(q), List.of(model), TEMPLATES);
    }

    // ---- post-processing classes (c), q = 3 --------------------------------------------------------------------

    @Test
    void threeValidDistinctTextsAreAllKeptAndTheModeIsModel() {
        WildcardPipeline w = merge(3, "alpha beta gamma", "delta epsilon zeta", "eta theta iota");
        assertThat(texts(w)).containsExactly("alpha beta gamma", "delta epsilon zeta", "eta theta iota");
        assertThat(w.getQueryMode()).isEqualTo(QueryExpansionMode.MODEL);
    }

    @Test
    void fourValidTextsKeepTheFirstThree() {
        WildcardPipeline w = merge(3, "alpha beta gamma", "delta epsilon zeta", "eta theta iota", "kappa lambda mu");
        assertThat(texts(w)).containsExactly("alpha beta gamma", "delta epsilon zeta", "eta theta iota");
        assertThat(w.getQueryMode()).isEqualTo(QueryExpansionMode.MODEL);
    }

    @Test
    void twoValidAndOneRuleQBreakerKeepTwoAndFillTheFirstTemplate() {
        WildcardPipeline w = merge(3, "alpha beta gamma", "two words", "eta theta iota");
        assertThat(texts(w)).containsExactly("alpha beta gamma", "eta theta iota", TEMPLATES.get(0));
        assertThat(w.getQueryMode()).isEqualTo(QueryExpansionMode.MODEL);
    }

    @Test
    void threeCaseInsensitiveDuplicatesKeepOneAndFillTwoTemplates() {
        WildcardPipeline w = merge(3, "alpha beta gamma", "ALPHA beta GAMMA", "Alpha Beta Gamma");
        assertThat(texts(w)).containsExactly("alpha beta gamma", TEMPLATES.get(0), TEMPLATES.get(1));
        assertThat(w.getQueryMode()).isEqualTo(QueryExpansionMode.MODEL);
    }

    @Test
    void aModelTextEqualToTheFirstTemplateIsKeptAndThatTemplateIsSkippedInTheFill() {
        WildcardPipeline w = merge(3, "new pandemic EXTREME scenario disaster", "alpha beta gamma");
        assertThat(texts(w)).containsExactly("new pandemic EXTREME scenario disaster", "alpha beta gamma", TEMPLATES.get(1));
        assertThat(w.getQueryMode()).isEqualTo(QueryExpansionMode.MODEL);
    }

    @Test
    void textsAreTrimmedAndTheirInnerWhitespaceCollapsed() {
        WildcardPipeline w = merge(3, "  a   b  c ", "d e f", "g h i");
        assertThat(texts(w)).containsExactly("a b c", "d e f", "g h i");
    }

    @Test
    void anEmptyAnswerGivesThreeTemplatesAndTheFallbackMode() {
        WildcardPipeline w = merge(3);
        assertThat(texts(w)).containsExactlyElementsOf(TEMPLATES);
        assertThat(w.getQueryMode()).isEqualTo(QueryExpansionMode.TEMPLATE_FALLBACK);
    }

    @Test
    void anAnswerWhereEveryTextIsDroppedGivesThreeTemplatesAndTheFallbackMode() {
        WildcardPipeline w = merge(3, "two words", "\"quoted text\" here now", "fusion OR fission plants", "when:7d vaccine news",
            "(a b c)", "x");
        assertThat(texts(w)).containsExactlyElementsOf(TEMPLATES);
        assertThat(w.getQueryMode()).isEqualTo(QueryExpansionMode.TEMPLATE_FALLBACK);
    }

    @Test
    void theHostileSetOfTheSpecKeepsOnlyTheLastTextAndFillsTwoTemplates() {
        WildcardPipeline w = merge(3, "fusion OR fission plants", "\"mRNA\" vaccine news", "vaccine news when:7d", "ok query text here");
        assertThat(texts(w)).containsExactly("ok query text here", TEMPLATES.get(0), TEMPLATES.get(1));
        assertThat(w.getQueryMode()).isEqualTo(QueryExpansionMode.MODEL);
    }

    // ---- q = 2 ----------------------------------------------------------------------------------------------------

    @Test
    void aTwoQueryPipelineKeepsAtMostTwoTexts() {
        WildcardPipeline w = merge(2, "alpha beta gamma", "delta epsilon zeta", "eta theta iota");
        assertThat(texts(w)).containsExactly("alpha beta gamma", "delta epsilon zeta");
        assertThat(w.getQueryMode()).isEqualTo(QueryExpansionMode.MODEL);
    }

    @Test
    void aTwoQueryPipelineFillsFromTheFirstTemplates() {
        assertThat(texts(merge(2))).containsExactly(TEMPLATES.get(0), TEMPLATES.get(1));
        assertThat(texts(merge(2, "alpha beta gamma"))).containsExactly("alpha beta gamma", TEMPLATES.get(0));
    }

    // ---- ids, statuses and the other fields are copied from the template -------------------------------------------

    @Test
    void idsStatusesAndPipelineFieldsComeFromTheTemplate() {
        WildcardPipeline w = merge(3, "alpha beta gamma");
        assertThat(w.getId()).isEqualTo("W02");
        assertThat(w.getKind()).isEqualTo(WildcardPipelineKind.CATALOGUE);
        assertThat(w.getLabel()).isEqualTo("New pandemic");
        assertThat(w.getHeading()).isEqualTo("New pandemic 8/10");
        assertThat(w.getLevel()).isEqualTo(8);
        assertThat(w.getTopicKey()).isEqualTo("biology-new-pandemic");
        assertThat(w.getQueries()).extracting(PipelineQuery::getId).containsExactly("Q04", "Q05", "Q06");
        assertThat(w.getQueries()).extracting(PipelineQuery::getStatus)
            .containsOnly(SearchQueryStatus.PENDING);
        assertThat(w.getQueries()).extracting(PipelineQuery::getArticlesReturned).containsOnly(0);
    }

    // ---- invariants for every input of a corpus x q ------------------------------------------------------------------

    static Stream<Arguments> corpus() {
        List<List<String>> inputs = List.of(
            List.of(),
            List.of("x"),
            List.of("alpha beta gamma"),
            List.of("alpha beta gamma", "delta epsilon zeta"),
            List.of("alpha beta gamma", "delta epsilon zeta", "eta theta iota"),
            List.of("alpha beta gamma", "delta epsilon zeta", "eta theta iota", "kappa lambda mu", "nu xi omicron"),
            List.of("alpha beta gamma", "ALPHA BETA GAMMA", "alpha  beta   gamma"),
            List.of("  spaced   out   query  ", "spaced out query"),
            List.of("two words", "fusion OR fission plants", "\"quoted\" text here now", "(paren) text here now", "text when:7d here"),
            List.of("new pandemic extreme scenario disaster", "New Pandemic Unprecedented Scale Catastrophe"),
            List.of("new pandemic radical upheaval collapse", "alpha beta gamma", "NEW PANDEMIC RADICAL UPHEAVAL COLLAPSE"),
            List.of("a b c d e f g h i j k l", "a b c d e f g h i j k l m", "x ".repeat(70) + "y z"),
            List.of("ok query text here", "ok query text here", "ok query text here", "ok query text here"),
            List.of("fusion or fission plants", "ORganic farming growth trends"));
        List<Arguments> out = new ArrayList<>();
        for (List<String> in : inputs) {
            for (int q : new int[] {2, 3}) {
                out.add(Arguments.of(in, q));
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} / q = {1}")
    @MethodSource("corpus")
    void everyResultHasExactlyQDistinctRuleQTextsAndTheTemplateIds(List<String> model, int q) {
        WildcardPipeline w = QuerySupport.merge(template(q), model, TEMPLATES);
        List<String> out = texts(w);
        assertThat(out).as("exactly q texts").hasSize(q);
        for (String t : out) {
            assertThat(RuleQOracle.holds(t)).as(RuleQOracle.why(t)).isTrue();
        }
        assertThat(new HashSet<>(out.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList())).as("pairwise distinct, case-insensitive")
            .hasSize(q);
        assertThat(w.getQueries()).extracting(PipelineQuery::getId).isEqualTo(template(q).getQueries().stream().map(PipelineQuery::getId).toList());
        boolean anyModelKept = model.stream().map(QuerySupport::clean).filter(t -> t != null && QuerySupport.valid(t))
            .anyMatch(t -> out.stream().anyMatch(o -> o.equalsIgnoreCase(t)));
        assertThat(w.getQueryMode()).as("MODEL iff at least one model text was kept")
            .isEqualTo(anyModelKept ? QueryExpansionMode.MODEL : QueryExpansionMode.TEMPLATE_FALLBACK);
    }

    // ---- parse: usable or null -------------------------------------------------------------------------------------

    @Test
    void parseReturnsTheTextsOfAWellFormedAnswer() {
        assertThat(QuerySupport.parse("{\"queries\":[\"alpha beta gamma\",\"delta epsilon zeta\"]}"))
            .containsExactly("alpha beta gamma", "delta epsilon zeta");
        assertThat(QuerySupport.parse("{\"queries\":[]}")).isNotNull().isEmpty();
    }

    @ParameterizedTest(name = "unusable: {0}")
    @MethodSource("unusableAnswers")
    void parseReturnsNullForAnUnusableAnswer(String answer) {
        assertThat(QuerySupport.parse(answer)).isNull();
    }

    static Stream<Arguments> unusableAnswers() {
        return Stream.of("not json", "{}", "{\"queries\":\"x\"}", "{\"queries\":[1,2,3]}", "[]", "{\"queries\":[\"a b c\",2]}",
            "{\"queries\":null}", "{\"queries\":{}}", "", "   ", "{\"queries\":[\"a b c\"", "\"text\"", "42").map(Arguments::of);
    }
}
