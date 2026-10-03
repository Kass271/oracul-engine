package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.BucketAllocation;
import com.oracul.app.api.model.CustomWildcard;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.QueryBucket;
import com.oracul.app.api.model.QueryExpansionMode;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.SearchIntent;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import com.oracul.app.api.model.WildcardSetting;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

import static com.oracul.app.research.PlanSupport.cfg;
import static com.oracul.app.research.PlanSupport.cfgA;
import static com.oracul.app.research.PlanSupport.plan;

/** SearchPlanner pure unit tables of research-pipeline.md "Slice 05_search-sources - FR-12". */
// @trace FR-12
class SearchPlannerTest {

    private static final QueryBucket W = QueryBucket.WILDCARD;
    private static final QueryBucket M = QueryBucket.MAJOR;
    private static final QueryBucket AD = QueryBucket.ADJACENT;
    private static final QueryBucket U = QueryBucket.UNEXPECTED;

    private static final String DARK = " — risks, threats, failures and warnings";
    private static final String OPT = " — breakthroughs, recoveries and opportunities";
    private static final String BOTH = " — risks, threats, failures and warnings; breakthroughs, recoveries and opportunities";

    /** Expected intent: id, bucket, topicKey, category, query count, description, drivenBy. */
    record Exp(String id, QueryBucket bucket, String topicKey, String category, int queries, String description,
               List<String> drivenBy) {}

    private static void assertBuckets(SearchPlan p, double... shares) {
        List<BucketAllocation> b = p.getBuckets();
        assertThat(b).extracting(BucketAllocation::getBucket).containsExactly(W, M, AD, U);
        assertThat(b).extracting(BucketAllocation::getShare).containsExactly(shares[0], shares[1], shares[2], shares[3]);
    }

    private static void assertBucketQueries(SearchPlan p, int... counts) {
        assertThat(p.getBuckets()).extracting(BucketAllocation::getQueries)
            .containsExactly(counts[0], counts[1], counts[2], counts[3]);
    }

    private static void assertIntents(SearchPlan p, Exp... expected) {
        assertThat(p.getIntents()).hasSize(expected.length);
        for (int i = 0; i < expected.length; i++) {
            SearchIntent in = p.getIntents().get(i);
            Exp e = expected[i];
            assertThat(in.getId()).as("intent %d id", i).isEqualTo(e.id());
            assertThat(in.getBucket()).as(e.id() + " bucket").isEqualTo(e.bucket());
            assertThat(in.getTopicKey()).as(e.id() + " topicKey").isEqualTo(e.topicKey());
            assertThat(in.getCategory()).as(e.id() + " category").isEqualTo(e.category());
            assertThat(in.getDescription()).as(e.id() + " description").isEqualTo(e.description());
            assertThat(in.getDrivenBy()).as(e.id() + " drivenBy").containsExactlyElementsOf(e.drivenBy());
            long n = p.getQueries().stream().filter(q -> q.getIntentId().equals(e.id())).count();
            assertThat(n).as(e.id() + " query count").isEqualTo(e.queries());
        }
    }

    private static void assertQueryShape(SearchPlan p, int budget) {
        assertThat(p.getQueryBudget()).isEqualTo(budget);
        assertThat(p.getExpansionMode()).isEqualTo(QueryExpansionMode.TEMPLATE_FALLBACK);
        assertThat(p.getQueries()).hasSize(budget);
        List<String> seenIntents = new ArrayList<>();
        for (int i = 0; i < p.getQueries().size(); i++) {
            SearchQuery q = p.getQueries().get(i);
            assertThat(q.getId()).isEqualTo(String.format("Q%02d", i + 1));
            assertThat(q.getStatus()).isEqualTo(SearchQueryStatus.PENDING);
            assertThat(q.getArticlesReturned()).isEqualTo(0);
            assertThat(q.getText()).isNotBlank();
            Map<String, QueryBucket> byIntent = p.getIntents().stream()
                .collect(Collectors.toMap(SearchIntent::getId, SearchIntent::getBucket));
            assertThat(q.getBucket()).isEqualTo(byIntent.get(q.getIntentId()));
            if (seenIntents.isEmpty() || !seenIntents.get(seenIntents.size() - 1).equals(q.getIntentId())) {
                seenIntents.add(q.getIntentId());
            }
        }
        assertThat(seenIntents).as("queries are grouped in intent order").doesNotHaveDuplicates();
        Set<String> lower = p.getQueries().stream().map(q -> q.getText().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        assertThat(lower).as("template queries are distinct, case-insensitive").hasSize(budget);
    }

    private static void assertQueryRanges(SearchPlan p, Object... intentIdAndLastQueryId) {
        for (int i = 0; i < intentIdAndLastQueryId.length; i += 2) {
            String intent = (String) intentIdAndLastQueryId[i];
            String last = (String) intentIdAndLastQueryId[i + 1];
            List<SearchQuery> qs = p.getQueries().stream().filter(q -> q.getIntentId().equals(intent)).toList();
            assertThat(qs.get(qs.size() - 1).getId()).as("last query of " + intent).isEqualTo(last);
        }
    }

    @Test
    void configurationAHasTheSixIntentsOfTheSpecTable() {
        SearchPlan p = plan(cfgA(), 20);
        assertBuckets(p, 0.4, 0.3, 0.2, 0.1);
        assertBucketQueries(p, 8, 6, 4, 2);
        List<String> drivenA = List.of("Darkness 9/10", "Horizon 5 years");
        assertIntents(p,
            new Exp("I01", W, "biology-new-pandemic", null, 5, "Current developments related to New pandemic" + DARK,
                List.of("New pandemic 8/10", "Darkness 9/10", "Horizon 5 years")),
            new Exp("I02", W, "robotics-humanoid-boom", null, 3, "Current developments related to Humanoid robot boom" + DARK,
                List.of("Humanoid robot boom 6/10", "Darkness 9/10", "Horizon 5 years")),
            new Exp("I03", M, null, null, 6, "Major current world events" + DARK, drivenA),
            new Exp("I04", AD, null, "biology", 2, "Adjacent developments in Biology" + DARK, drivenA),
            new Exp("I05", AD, null, "robotics", 2, "Adjacent developments in Robotics" + DARK, drivenA),
            new Exp("I06", U, null, null, 2, "Unusual early signals and research" + DARK, drivenA));
        assertQueryRanges(p, "I01", "Q05", "I02", "Q08", "I03", "Q14", "I04", "Q16", "I05", "Q18", "I06", "Q20");
        assertQueryShape(p, 20);
    }

    @Test
    void configurationBWithoutWildcardsRedistributesTheWildcardShare() {
        SearchPlan p = plan(cfg(8, 5, 5, HorizonCode._1Y, List.of(), List.of()), 20);
        assertBuckets(p, 0.0, 0.5, 0.3333, 0.1667);
        assertBucketQueries(p, 0, 10, 7, 3);
        List<String> driven = List.of("Horizon 1 year");
        assertIntents(p,
            new Exp("I01", M, null, null, 10, "Major current world events", driven),
            new Exp("I02", AD, null, null, 7, "Adjacent developments in science, technology and economy", driven),
            new Exp("I03", U, null, null, 3, "Unusual early signals and research", driven));
        assertQueryShape(p, 20);
    }

    @Test
    void optimisticConfigurationAddsOpportunityAspectsAndRealismDriver() {
        SearchPlan p = plan(cfg(4, 3, 7, HorizonCode._1M, List.of(new WildcardSetting("energy-fusion-breakthrough", 10)), List.of()), 20);
        assertBucketQueries(p, 8, 6, 4, 2);
        assertIntents(p,
            new Exp("I01", W, "energy-fusion-breakthrough", null, 8, "Current developments related to Fusion breakthrough" + OPT,
                List.of("Fusion breakthrough 10/10", "Optimism 7/10", "Horizon 1 month")),
            new Exp("I02", M, null, null, 6, "Major current world events" + OPT, List.of("Optimism 7/10", "Horizon 1 month")),
            new Exp("I03", AD, null, "energy", 4, "Adjacent developments in Energy" + OPT, List.of("Optimism 7/10", "Horizon 1 month")),
            new Exp("I04", U, null, null, 2, "Unusual early signals and research" + OPT,
                List.of("Optimism 7/10", "Realism 4/10", "Horizon 1 month")));
        assertQueryShape(p, 20);
    }

    @Test
    void darknessAndOptimismBothAtSixCombineTheSuffixes() {
        SearchPlan p = plan(cfg(5, 6, 6, HorizonCode._1W, List.of(), List.of()), 20);
        assertThat(p.getIntents()).isNotEmpty();
        for (SearchIntent in : p.getIntents()) {
            assertThat(in.getDescription()).endsWith(BOTH);
            int d = in.getDrivenBy().indexOf("Darkness 6/10");
            int o = in.getDrivenBy().indexOf("Optimism 6/10");
            assertThat(d).as(in.getId() + " has Darkness").isGreaterThanOrEqualTo(0);
            assertThat(o).as(in.getId() + " Optimism after Darkness").isGreaterThan(d);
            assertThat(in.getDrivenBy().get(in.getDrivenBy().size() - 1)).isEqualTo("Horizon 1 week");
        }
        SearchIntent unexpected = p.getIntents().get(p.getIntents().size() - 1);
        assertThat(unexpected.getBucket()).isEqualTo(U);
        assertThat(unexpected.getDrivenBy()).containsExactly("Darkness 6/10", "Optimism 6/10", "Realism 5/10", "Horizon 1 week");
    }

    @Test
    void valuesBelowTheThresholdsAddNoSuffixAndNoDriver() {
        SearchPlan p = plan(cfg(6, 5, 5, HorizonCode._1D, List.of(), List.of()), 20);
        for (SearchIntent in : p.getIntents()) {
            assertThat(in.getDescription()).doesNotContain("—");
            assertThat(in.getDrivenBy()).containsExactly("Horizon Tomorrow");
        }
    }

    @Test
    void tenWildcardsGiveOneQueryToTheFirstEightAndNoneToTheLastTwo() {
        List<WildcardSetting> ws = PlanSupport.TEN_WILDCARDS.stream().map(id -> new WildcardSetting(id, 5)).toList();
        SearchPlan p = plan(cfg(8, 5, 5, HorizonCode._5Y, ws, List.of()), 20);
        assertBucketQueries(p, 8, 6, 4, 2);
        List<SearchIntent> wildcardIntents = p.getIntents().stream().filter(i -> i.getBucket() == W).toList();
        assertThat(wildcardIntents).hasSize(10);
        for (int i = 0; i < 10; i++) {
            String id = wildcardIntents.get(i).getId();
            long n = p.getQueries().stream().filter(q -> q.getIntentId().equals(id)).count();
            assertThat(n).as("wildcard intent #" + (i + 1)).isEqualTo(i < 8 ? 1 : 0);
            assertThat(wildcardIntents.get(i).getTopicKey()).isEqualTo(PlanSupport.TEN_WILDCARDS.get(i));
        }
        List<SearchIntent> adjacent = p.getIntents().stream().filter(i -> i.getBucket() == AD).toList();
        assertThat(adjacent).extracting(SearchIntent::getCategory)
            .containsExactly("ai", "robotics", "biology", "political", "economy", "energy", "environment", "space", "extreme");
        List<Long> perAdjacent = adjacent.stream()
            .map(a -> p.getQueries().stream().filter(q -> q.getIntentId().equals(a.getId())).count()).toList();
        assertThat(perAdjacent).containsExactly(1L, 1L, 1L, 1L, 0L, 0L, 0L, 0L, 0L);
        assertQueryShape(p, 20);
    }

    @Test
    void customWildcardGetsItsOwnIntentAndAddsNoAdjacentCategory() {
        SearchPlan p = plan(cfg(8, 5, 5, HorizonCode._1Y, List.of(), List.of(new CustomWildcard(" Solar sails ", 4))), 20);
        assertBucketQueries(p, 8, 6, 4, 2);
        List<String> driven = List.of("Horizon 1 year");
        assertIntents(p,
            new Exp("I01", W, "custom-1", null, 8, "Current developments related to Solar sails", List.of("Solar sails 4/10", "Horizon 1 year")),
            new Exp("I02", M, null, null, 6, "Major current world events", driven),
            new Exp("I03", AD, null, null, 4, "Adjacent developments in science, technology and economy", driven),
            new Exp("I04", U, null, null, 2, "Unusual early signals and research", driven));
        assertQueryShape(p, 20);
    }

    @ParameterizedTest(name = "budget {0} with topics -> {1}/{2}/{3}/{4}")
    @CsvSource({"18,7,5,4,2", "10,4,3,2,1", "4,1,1,1,1"})
    void bucketTableWithTopics(int budget, int w, int m, int a, int u) {
        SearchPlan p = plan(cfgA(), budget);
        assertBucketQueries(p, w, m, a, u);
        assertQueryShape(p, budget);
    }

    @ParameterizedTest(name = "budget {0} without topics -> {1}/{2}/{3}/{4}")
    @CsvSource({"20,0,10,7,3", "10,0,5,3,2"})
    void bucketTableWithoutTopics(int budget, int w, int m, int a, int u) {
        SearchPlan p = plan(cfg(8, 5, 5, HorizonCode._1Y, List.of(), List.of()), budget);
        assertBucketQueries(p, w, m, a, u);
        assertQueryShape(p, budget);
    }

    // @trace FR-12
    @Test
    void invariantsHoldForEveryBudgetFourToOneHundredWithZeroOneThreeAndTenTopics() {
        for (int topics : new int[] {0, 1, 3, 10}) {
            List<WildcardSetting> ws = PlanSupport.TEN_WILDCARDS.subList(0, topics).stream()
                .map(id -> new WildcardSetting(id, 5)).toList();
            for (int budget = 4; budget <= 100; budget++) {
                String ctx = "budget " + budget + ", topics " + topics;
                SearchPlan p = plan(cfg(8, 5, 5, HorizonCode._1Y, ws, List.of()), budget);
                assertThat(p.getQueryBudget()).as(ctx).isEqualTo(budget);
                assertThat(p.getBuckets()).as(ctx).hasSize(4);
                int sum = p.getBuckets().stream().mapToInt(BucketAllocation::getQueries).sum();
                assertThat(sum).as(ctx + ": sum of bucket queries").isEqualTo(budget);
                for (BucketAllocation b : p.getBuckets()) {
                    boolean active = b.getBucket() != W || topics > 0;
                    if (active) assertThat(b.getQueries()).as(ctx + " " + b.getBucket() + " >= 1").isGreaterThanOrEqualTo(1);
                    else assertThat(b.getQueries()).as(ctx + " WILDCARD inactive").isEqualTo(0);
                    List<String> texts = p.getQueries().stream().filter(q -> q.getBucket() == b.getBucket())
                        .map(q -> q.getText().toLowerCase(Locale.ROOT)).toList();
                    assertThat(texts).as(ctx + " queries in " + b.getBucket()).hasSize(b.getQueries());
                    assertThat(new HashSet<>(texts)).as(ctx + " distinct texts in " + b.getBucket()).hasSize(texts.size());
                }
                assertThat(p.getQueries()).as(ctx).hasSize(budget);
                assertThat(p.getQueries().stream().map(q -> q.getText().toLowerCase(Locale.ROOT)).collect(Collectors.toSet()))
                    .as(ctx + " distinct texts in plan").hasSize(budget);
                for (int i = 0; i < p.getIntents().size(); i++) {
                    assertThat(p.getIntents().get(i).getId()).as(ctx).isEqualTo(String.format("I%02d", i + 1));
                }
                for (int i = 0; i < p.getQueries().size(); i++) {
                    assertThat(p.getQueries().get(i).getId()).as(ctx).isEqualTo(String.format("Q%02d", i + 1));
                }
                Set<String> intentIds = new HashSet<>();
                p.getIntents().forEach(i -> intentIds.add(i.getId()));
                assertThat(p.getQueries()).as(ctx).allMatch(q -> intentIds.contains(q.getIntentId()));
                assertThat(p.getExpansionMode()).as(ctx).isEqualTo(QueryExpansionMode.TEMPLATE_FALLBACK);
            }
        }
    }

    // R11
    // @trace FR-12
    @Test
    void invariantsHoldForEveryBudgetWithOneAndThreeTenWordCustomTopics() {
        List<String> labels = List.of("Fall of the big US tech firms in the EU", "Rise of the new AI chip makers in the US",
            "Rise of the new US gas plants in the EU");
        for (int topics : new int[] {1, 3}) {
            List<CustomWildcard> cws = labels.subList(0, topics).stream().map(l -> new CustomWildcard(l, 5)).toList();
            for (int budget = 4; budget <= 100; budget++) {
                String ctx = "budget " + budget + ", custom topics " + topics;
                SearchPlan p = plan(cfg(8, 5, 5, HorizonCode._1Y, List.of(), cws), budget);
                assertThat(p.getQueries()).as(ctx).hasSize(budget);
                assertThat(p.getBuckets().stream().mapToInt(BucketAllocation::getQueries).sum()).as(ctx).isEqualTo(budget);
                for (BucketAllocation b : p.getBuckets()) {
                    List<String> texts = p.getQueries().stream().filter(q -> q.getBucket() == b.getBucket())
                        .map(q -> q.getText().toLowerCase(Locale.ROOT)).toList();
                    assertThat(texts).as(ctx + " queries in " + b.getBucket()).hasSize(b.getQueries());
                    assertThat(new HashSet<>(texts)).as(ctx + " distinct in " + b.getBucket()).hasSize(texts.size());
                }
                assertThat(p.getQueries().stream().map(q -> q.getText().toLowerCase(Locale.ROOT)).collect(Collectors.toSet()))
                    .as(ctx + " distinct in plan").hasSize(budget);
            }
        }
    }

    // R12 - closes the class of label-length edge cases: every word count 1..10, short and exactly-40-char labels.
    private static final List<String> LABELS = List.of(
        "Cybersecurity",                                   // 1
        "Solar sails",                                     // 2
        "Semiconductor shortages",                         // 2
        "Deep sea mining",                                 // 3
        "Unexpected telecommunications regulation",        // 3 (40 chars)
        "Next generation battery storage",                 // 4
        "Autonomous vehicle liability regulations",        // 4 (40 chars)
        "Collapse of the global internet",                 // 5
        "Collapse of the worldwide pharmaceutical",        // 5 (40 chars)
        "Fall of the big tech firms",                      // 6
        "Collapse of the global shipping networks",        // 6 (40 chars)
        "Growth of AI in the European Union",              // 7
        "Rise of quantum battery startups in Asia",        // 7 (40 chars)
        "Collapse of the old oil economy in Gulf",         // 8
        "Rise of the rapid housing sector in Asia",        // 8 (40 chars)
        "Rise of the new AI chip makers in Asia",          // 9
        "Growth of the new AI chip makers in Asia",        // 9 (40 chars)
        "Fall of the big US tech firms in the EU",         // 10
        "Rise of the new AI chip makers in the EU");       // 10 (40 chars)

    private static final int[] LABEL_WORDS = {1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10};

    static Stream<Arguments> labelShapes() {
        List<Arguments> out = new ArrayList<>();
        for (int i = 0; i < LABELS.size(); i++) {
            for (String shape : new String[] {"one custom", "three custom", "one custom + two catalogue"}) {
                for (boolean aspects : new boolean[] {false, true}) {
                    out.add(Arguments.of(LABELS.get(i), LABEL_WORDS[i], shape, aspects));
                }
            }
        }
        return out.stream();
    }

    // @trace FR-12
    @ParameterizedTest(name = "[{1} words] \"{0}\" / {2} / aspects={3}")
    @MethodSource("labelShapes")
    void everyLabelWordCountKeepsThePlanInvariantsForEveryBudget(String label, int words, String shape, boolean aspects) {
        assertThat(label.trim().split("\\s+")).as("test label word count").hasSize(words);
        assertThat(label.length()).as("test label length").isLessThanOrEqualTo(40);
        int idx = LABELS.indexOf(label);
        List<CustomWildcard> cws = new ArrayList<>();
        cws.add(new CustomWildcard(label, 5));
        if (shape.equals("three custom")) {
            cws.add(new CustomWildcard(LABELS.get((idx + 7) % LABELS.size()), 5));
            cws.add(new CustomWildcard(LABELS.get((idx + 12) % LABELS.size()), 5));
        }
        List<WildcardSetting> ws = shape.endsWith("two catalogue")
            ? PlanSupport.TEN_WILDCARDS.subList(0, 2).stream().map(id -> new WildcardSetting(id, 5)).toList()
            : List.of();
        int level = aspects ? 9 : 5;
        for (int budget = 4; budget <= 100; budget++) {
            String ctx = "label '" + label + "', " + shape + ", aspects " + aspects + ", budget " + budget;
            SearchPlan p = plan(cfg(8, level, level, HorizonCode._1Y, ws, cws), budget);
            assertThat(p.getQueries()).as(ctx + ": total queries").hasSize(budget);
            assertThat(p.getBuckets().stream().mapToInt(BucketAllocation::getQueries).sum()).as(ctx + ": allocation sum").isEqualTo(budget);
            for (BucketAllocation b : p.getBuckets()) {
                List<String> texts = p.getQueries().stream().filter(q -> q.getBucket() == b.getBucket())
                    .map(q -> q.getText().toLowerCase(Locale.ROOT)).toList();
                assertThat(texts).as(ctx + ": queries in " + b.getBucket()).hasSize(b.getQueries());
                assertThat(new HashSet<>(texts)).as(ctx + ": distinct in " + b.getBucket()).hasSize(texts.size());
            }
            assertThat(p.getQueries().stream().map(q -> q.getText().toLowerCase(Locale.ROOT)).collect(Collectors.toSet()))
                .as(ctx + ": distinct in plan").hasSize(budget);
            for (SearchQuery q : p.getQueries()) {
                String text = q.getText();
                String[] tokens = text.trim().split("\\s+");
                String qctx = ctx + ": '" + text + "'";
                assertThat(text).as(qctx + " no slash").doesNotContain("/");
                assertThat(text).as(qctx + " no quotes").doesNotContain("\"").doesNotContain("\u201c").doesNotContain("\u201d");
                assertThat(text).as(qctx + " no parentheses").doesNotContain("(").doesNotContain(")");
                assertThat(text.length()).as(qctx + " <=120").isLessThanOrEqualTo(120);
                assertThat(tokens.length).as(qctx + " 2..8 keywords").isBetween(2, 8);
                for (String t : tokens) {
                    assertThat(t).as(qctx + " no leading dash").doesNotStartWith("-");
                    assertThat(t).as(qctx + " no operator word").isNotIn("OR", "AND", "NOT", "or", "and", "not");
                }
            }
        }
    }
}
