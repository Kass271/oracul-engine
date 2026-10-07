package com.oracul.app.research;

import static com.oracul.app.research.PlanSupport.cfg;
import static com.oracul.app.research.PlanSupport.cfgA;
import static com.oracul.app.research.PlanSupport.cfgOfSize;
import static com.oracul.app.research.PlanSupport.plan;
import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.CustomWildcard;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.PipelineQuery;
import com.oracul.app.api.model.QueryExpansionMode;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.SearchQueryStatus;
import com.oracul.app.api.model.WildcardPipeline;
import com.oracul.app.api.model.WildcardPipelineKind;
import com.oracul.app.api.model.WildcardSetting;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * SearchPlanner (pure) - wildcard-search.md FR-50 and its "Slice 04 delta": one pipeline per profile topic, q = 3 / 2
 * queries per pipeline, ids, kinds, levels, headings, the template texts, and the empty buckets / intents / queries.
 */
// @trace FR-50
class SearchPlannerTest {

    private static List<PipelineQuery> allQueries(SearchPlan p) {
        List<PipelineQuery> out = new ArrayList<>();
        for (WildcardPipeline w : p.getPipelines()) out.addAll(w.getQueries());
        return out;
    }

    private static int expectedPerPipeline(int n) {
        return n >= 9 ? 2 : 3;
    }

    static Stream<Arguments> everyWildcardCount() {
        return IntStream.rangeClosed(0, 33).mapToObj(n -> Arguments.of(n));
    }

    // Ranges & invariants: pipeline count, queries per pipeline, contiguous ids, budget, empty legacy arrays - for n = 0 ... 33
    // (the spec's n = 0, 1, 2, 3, 8, 9, 10, 33 are all inside the loop, and so is every boundary 8 / 9)
    @ParameterizedTest(name = "{0} wildcards")
    @MethodSource("everyWildcardCount")
    void everyWildcardCountGivesTheSpecifiedPipelinesQueriesAndIds(int n) {
        int catalogue = Math.min(n, 30);
        ScenarioConfiguration cfg = cfgOfSize(catalogue, n - catalogue);
        SearchPlan p = plan(cfg);

        int pipelines = Math.max(n, 1);
        int perPipeline = n == 0 ? 3 : expectedPerPipeline(n);
        assertThat(p.getPipelines()).as("pipeline count for n = " + n).hasSize(pipelines);
        for (int i = 0; i < pipelines; i++) {
            WildcardPipeline w = p.getPipelines().get(i);
            assertThat(w.getId()).as("pipeline id " + i).isEqualTo(String.format("W%02d", i + 1));
            assertThat(w.getQueries()).as(w.getId() + " query count (n = " + n + ")").hasSize(perPipeline);
            assertThat(w.getQueryMode()).as(w.getId() + " queryMode of a template plan").isEqualTo(QueryExpansionMode.TEMPLATE_FALLBACK);
            assertThat(w.getCandidatesConsidered()).as(w.getId() + " candidatesConsidered").isNull();
            assertThat(w.getSourceIds()).as(w.getId() + " sourceIds").isEmpty();
        }
        List<PipelineQuery> queries = allQueries(p);
        assertThat(queries).hasSize(pipelines * perPipeline);
        assertThat(p.getQueryBudget()).as("queryBudget = sum of the pipelines' queries").isEqualTo(queries.size());
        for (int i = 0; i < queries.size(); i++) {
            PipelineQuery q = queries.get(i);
            assertThat(q.getId()).as("query ids are contiguous over the whole plan").isEqualTo(String.format("Q%02d", i + 1));
            assertThat(q.getStatus()).isEqualTo(SearchQueryStatus.PENDING);
            assertThat(q.getArticlesReturned()).isEqualTo(0);
            assertThat(RuleQOracle.holds(q.getText())).as("every query of a plan obeys rule Q: " + RuleQOracle.why(q.getText())).isTrue();
        }
        assertThat(new HashSet<>(queries.stream().map(PipelineQuery::getId).toList())).as("every query belongs to exactly one pipeline")
            .hasSize(queries.size());
        assertThat(p.getBuckets()).as("buckets are [] for a new plan").isEmpty();
        assertThat(p.getIntents()).as("intents are [] for a new plan").isEmpty();
        assertThat(p.getQueries()).as("queries are [] for a new plan").isEmpty();
        assertThat(p.getExpansionMode()).isEqualTo(QueryExpansionMode.TEMPLATE_FALLBACK);
    }

    @Test
    void noWildcardGivesOneGeneralPipeline() {
        SearchPlan p = plan(cfg(8, 5, 5, HorizonCode._1Y, List.of(), List.of()));
        assertThat(p.getPipelines()).hasSize(1);
        WildcardPipeline w = p.getPipelines().get(0);
        assertThat(w.getId()).isEqualTo("W01");
        assertThat(w.getKind()).isEqualTo(WildcardPipelineKind.GENERAL);
        assertThat(w.getLabel()).isEqualTo("General");
        assertThat(w.getHeading()).isEqualTo("General");
        assertThat(w.getLevel()).as("a GENERAL pipeline has no level").isNull();
        assertThat(w.getTopicKey()).as("a GENERAL pipeline has no topicKey").isNull();
        assertThat(w.getQueries()).extracting(PipelineQuery::getId).containsExactly("Q01", "Q02", "Q03");
        // GENERAL with Darkness 5 / Optimism 5: no direction word (spec step 6, example b)
        assertThat(w.getQueries()).extracting(PipelineQuery::getText)
            .containsExactly("major world events today", "global economy politics developments",
                "international science technology developments");
        assertThat(p.getQueryBudget()).isEqualTo(3);
    }

    @Test
    void acceptanceBodyAGivesTwoCataloguePipelinesWithTheirTemplateTexts() {
        SearchPlan p = plan(cfgA());
        assertThat(p.getPipelines()).hasSize(2);
        WildcardPipeline w1 = p.getPipelines().get(0);
        assertThat(w1.getId()).isEqualTo("W01");
        assertThat(w1.getKind()).isEqualTo(WildcardPipelineKind.CATALOGUE);
        assertThat(w1.getLabel()).isEqualTo("New pandemic");
        assertThat(w1.getLevel()).isEqualTo(8);
        assertThat(w1.getTopicKey()).isEqualTo("biology-new-pandemic");
        assertThat(w1.getHeading()).isEqualTo("New pandemic 8/10");
        assertThat(w1.getQueries()).extracting(PipelineQuery::getId).containsExactly("Q01", "Q02", "Q03");
        assertThat(w1.getQueries()).extracting(PipelineQuery::getText).containsExactly(
            "New pandemic extreme scenario disaster", "New pandemic unprecedented scale catastrophe",
            "New pandemic radical upheaval collapse");

        WildcardPipeline w2 = p.getPipelines().get(1);
        assertThat(w2.getId()).isEqualTo("W02");
        assertThat(w2.getKind()).isEqualTo(WildcardPipelineKind.CATALOGUE);
        assertThat(w2.getLabel()).isEqualTo("Humanoid robot boom");
        assertThat(w2.getLevel()).isEqualTo(6);
        assertThat(w2.getTopicKey()).isEqualTo("robotics-humanoid-boom");
        assertThat(w2.getHeading()).isEqualTo("Humanoid robot boom 6/10");
        assertThat(w2.getQueries()).extracting(PipelineQuery::getId).containsExactly("Q04", "Q05", "Q06");
        assertThat(w2.getQueries()).extracting(PipelineQuery::getText).containsExactly(
            "Humanoid robot boom serious disruption crisis", "Humanoid robot boom major escalation conflict",
            "Humanoid robot boom growing concerns threat");
        assertThat(p.getQueryBudget()).isEqualTo(6);
        assertThat(p.getExpansionMode()).isEqualTo(QueryExpansionMode.TEMPLATE_FALLBACK);
    }

    @Test
    void theAcceptanceTripleGivesThreePipelinesOfThreeQueriesEach() {
        // FR-50 acceptance 1 / 2: New pandemic 8, AI takeover 5 (a custom wildcard, contract-notes 18) and Energy crisis 3
        ScenarioConfiguration cfg = cfg(8, 9, 2, HorizonCode._5Y,
            List.of(new WildcardSetting("biology-new-pandemic", 8), new WildcardSetting("energy-energy-crisis", 3)),
            List.of(new CustomWildcard("AI takeover", 5)));
        SearchPlan p = plan(cfg);
        assertThat(p.getPipelines()).hasSize(3);
        assertThat(p.getPipelines()).extracting(WildcardPipeline::getHeading)
            .containsExactly("New pandemic 8/10", "Energy crisis 3/10", "AI takeover 5/10");
        assertThat(p.getPipelines()).allSatisfy(w -> assertThat(w.getQueries()).hasSize(3));
        assertThat(p.getBuckets()).isEmpty();
        assertThat(p.getIntents()).isEmpty();
        assertThat(p.getQueries()).isEmpty();
        assertThat(p.getQueryBudget()).isEqualTo(9);
        // each pipeline's queries are about its own label
        assertThat(p.getPipelines().get(0).getQueries()).allSatisfy(q -> assertThat(q.getText()).contains("New pandemic"));
        assertThat(p.getPipelines().get(1).getQueries()).allSatisfy(q -> assertThat(q.getText()).contains("Energy crisis"));
        assertThat(p.getPipelines().get(2).getQueries()).allSatisfy(q -> assertThat(q.getText()).contains("AI takeover"));
    }

    @Test
    void aCustomWildcardGetsItsOwnPipelineWithQueriesThatNameIt() {
        ScenarioConfiguration cfg = cfg(8, 9, 2, HorizonCode._5Y, List.of(),
            List.of(new CustomWildcard("  Ocean desalination boom ", 7)));
        SearchPlan p = plan(cfg);
        assertThat(p.getPipelines()).hasSize(1);
        WildcardPipeline w = p.getPipelines().get(0);
        assertThat(w.getKind()).isEqualTo(WildcardPipelineKind.CUSTOM);
        assertThat(w.getLabel()).as("the custom label is trimmed").isEqualTo("Ocean desalination boom");
        assertThat(w.getTopicKey()).isEqualTo("custom-1");
        assertThat(w.getLevel()).isEqualTo(7);
        assertThat(w.getHeading()).isEqualTo("Ocean desalination boom 7/10");
        assertThat(w.getQueries()).hasSize(3);
        assertThat(w.getQueries()).allSatisfy(q -> assertThat(q.getText()).contains("Ocean desalination boom"));
    }

    @Test
    void thePipelineOrderIsTheProfileTopicOrderNotTheCatalogueOrder() {
        // configuration order: space (category 8) before ai (category 1) before biology (3); then the custom wildcards in order
        ScenarioConfiguration cfg = cfg(8, 9, 2, HorizonCode._5Y,
            List.of(new WildcardSetting("space-major-discovery", 4), new WildcardSetting("ai-agi-breakthrough", 5),
                new WildcardSetting("biology-new-pandemic", 6)),
            List.of(new CustomWildcard("Zeta topic", 2), new CustomWildcard("  Alpha topic  ", 9)));
        SearchPlan p = plan(cfg);
        assertThat(p.getPipelines()).extracting(WildcardPipeline::getId).containsExactly("W01", "W02", "W03", "W04", "W05");
        assertThat(p.getPipelines()).extracting(WildcardPipeline::getTopicKey)
            .containsExactly("space-major-discovery", "ai-agi-breakthrough", "biology-new-pandemic", "custom-1", "custom-2");
        assertThat(p.getPipelines()).extracting(WildcardPipeline::getKind).containsExactly(WildcardPipelineKind.CATALOGUE,
            WildcardPipelineKind.CATALOGUE, WildcardPipelineKind.CATALOGUE, WildcardPipelineKind.CUSTOM, WildcardPipelineKind.CUSTOM);
        assertThat(p.getPipelines()).extracting(WildcardPipeline::getLabel)
            .containsExactly("Major space discovery", "AGI breakthrough", "New pandemic", "Zeta topic", "Alpha topic");
        assertThat(p.getPipelines()).extracting(WildcardPipeline::getHeading).containsExactly("Major space discovery 4/10",
            "AGI breakthrough 5/10", "New pandemic 6/10", "Zeta topic 2/10", "Alpha topic 9/10");
    }

    // Slice 04 (a): level = round(topic weight x 10) = the request intensity, for every level 1 ... 10
    @ParameterizedTest(name = "intensity {0}")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void theLevelEqualsTheRequestIntensityAndTheHeadingNamesIt(int intensity) {
        ScenarioConfiguration cfg = cfg(8, 5, 5, HorizonCode._1Y,
            List.of(new WildcardSetting("biology-new-pandemic", intensity)),
            List.of(new CustomWildcard("Mars colony", intensity)));
        SearchPlan p = plan(cfg);
        assertThat(p.getPipelines()).hasSize(2);
        WildcardPipeline catalogue = p.getPipelines().get(0);
        WildcardPipeline custom = p.getPipelines().get(1);
        assertThat(catalogue.getLevel()).isEqualTo(intensity);
        assertThat(catalogue.getHeading()).isEqualTo("New pandemic " + intensity + "/10");
        assertThat(custom.getLevel()).isEqualTo(intensity);
        assertThat(custom.getHeading()).isEqualTo("Mars colony " + intensity + "/10");
    }

    // the planner takes the first q of QueryTemplates.forPipeline, for every wildcard count
    @ParameterizedTest(name = "{0} wildcards")
    @ValueSource(ints = {1, 8, 9, 33})
    void everyQueryTextIsTheCorrespondingTemplateText(int n) {
        int catalogue = Math.min(n, 30);
        ScenarioConfiguration cfg = cfgOfSize(catalogue, n - catalogue);
        SearchPlan p = plan(cfg);
        for (WildcardPipeline w : p.getPipelines()) {
            List<String> templates = PlanSupport.templates(w, cfg);
            assertThat(templates).as("forPipeline always returns exactly 3 texts").hasSize(3);
            assertThat(w.getQueries()).extracting(PipelineQuery::getText)
                .as(w.getId() + " takes the first " + w.getQueries().size() + " templates")
                .containsExactlyElementsOf(templates.subList(0, w.getQueries().size()));
        }
    }

    @Test
    void theTemplatePlanHasNoDuplicateQueryTextsWithinAPipeline() {
        for (int n : new int[] {0, 1, 8, 9, 33}) {
            int catalogue = Math.min(n, 30);
            SearchPlan p = plan(cfgOfSize(catalogue, n - catalogue));
            for (WildcardPipeline w : p.getPipelines()) {
                Set<String> lower = new HashSet<>();
                for (PipelineQuery q : w.getQueries()) {
                    assertThat(lower.add(q.getText().toLowerCase(java.util.Locale.ROOT))).as(w.getId() + " " + q.getText()).isTrue();
                }
            }
        }
    }
}
