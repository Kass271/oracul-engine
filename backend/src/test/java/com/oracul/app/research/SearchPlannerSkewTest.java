package com.oracul.app.research;

import static com.oracul.app.research.PlanSupport.cfg;
import static com.oracul.app.research.PlanSupport.plan;
import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.BucketAllocation;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.QueryBucket;
import com.oracul.app.api.model.SearchIntent;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.WildcardSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Skewed topic weights and tiny budgets: every share is kept at least one query, sums stay exact (FR-12). */
// @trace FR-12
@Timeout(60)
class SearchPlannerSkewTest {

    private static List<WildcardSetting> weights(int... w) {
        List<WildcardSetting> out = new ArrayList<>();
        for (int i = 0; i < w.length; i++) {
            out.add(new WildcardSetting(PlanSupport.TEN_WILDCARDS.get(i), w[i]));
        }
        return out;
    }

    private static Map<String, Long> perIntent(SearchPlan p) {
        return p.getQueries().stream().collect(Collectors.groupingBy(q -> q.getIntentId(), Collectors.counting()));
    }

    /** Range: 4..100 budgets x one heavy topic among 2, 3, 5 and 10 light ones (weights 10 against 1). */
    @Test
    void aHeavyTopicNeverLeavesALightTopicWithoutQueriesWhenTheWildcardShareCoversAllTopics() {
        int[][] shapes = {{10, 1}, {1, 10, 1}, {1, 1, 1, 10, 1}, {10, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 10}};
        for (int[] w : shapes) {
            for (int budget = 4; budget <= 100; budget++) {
                String ctx = "weights " + java.util.Arrays.toString(w) + ", budget " + budget;
                SearchPlan p = plan(cfg(8, 5, 5, HorizonCode._1Y, weights(w), List.of()), budget);
                int wildcardQueries = p.getBuckets().stream().filter(b -> b.getBucket() == QueryBucket.WILDCARD)
                    .mapToInt(BucketAllocation::getQueries).sum();
                Map<String, Long> counts = perIntent(p);
                List<SearchIntent> topics = p.getIntents().stream().filter(i -> i.getBucket() == QueryBucket.WILDCARD).toList();
                assertThat(topics).as(ctx).hasSize(w.length);
                long sum = topics.stream().mapToLong(i -> counts.getOrDefault(i.getId(), 0L)).sum();
                assertThat(sum).as(ctx + ": topic queries add up to the wildcard bucket").isEqualTo(wildcardQueries);
                if (wildcardQueries >= w.length) {
                    for (int i = 0; i < w.length; i++) {
                        long own = counts.getOrDefault(topics.get(i).getId(), 0L);
                        assertThat(own).as(ctx + ": topic " + i + " keeps a query").isGreaterThanOrEqualTo(1);
                        if (w[i] == 10) {
                            final int heavy = i;
                            long maxLight = java.util.stream.IntStream.range(0, w.length).filter(j -> j != heavy)
                                .mapToLong(j -> counts.getOrDefault(topics.get(j).getId(), 0L)).max().orElse(0);
                            assertThat(own).as(ctx + ": heavy topic is not below any light one").isGreaterThanOrEqualTo(maxLight);
                        }
                    }
                } else {
                    assertThat(topics.stream().filter(t -> counts.getOrDefault(t.getId(), 0L) > 0).count())
                        .as(ctx + ": one query each for the heaviest topics").isEqualTo(wildcardQueries);
                    // more topics than queries: the highest weight gets one, ties (all light ones) go to earlier topics
                    List<Boolean> served = new ArrayList<>();
                    for (int i = 0; i < w.length; i++) {
                        long own = counts.getOrDefault(topics.get(i).getId(), 0L);
                        assertThat(own).as(ctx + ": topic " + i + " has at most one query").isLessThanOrEqualTo(1);
                        served.add(own == 1);
                        if (w[i] == 10) {
                            assertThat(own).as(ctx + ": the heavy topic is among those that got a query").isEqualTo(1);
                        }
                    }
                    int firstUnservedLight = -1;
                    for (int i = 0; i < w.length; i++) {
                        if (w[i] == 10) {
                            continue;
                        }
                        if (!served.get(i) && firstUnservedLight < 0) {
                            firstUnservedLight = i;
                        } else if (served.get(i)) {
                            assertThat(firstUnservedLight).as(ctx + ": a later light topic " + i
                                + " got a query while an earlier light topic got none").isLessThan(0);
                        }
                    }
                }
            }
        }
    }

    /** Budget 3 (below the usual minimum of 4) without topics: the sum stays exact, no active bucket is empty. */
    @Test
    void aBudgetBelowTheUsualMinimumStillGivesEveryActiveBucketAQuery() {
        SearchPlan p = plan(cfg(8, 5, 5, HorizonCode._1Y, List.of(), List.of()), 3);
        List<Integer> alloc = p.getBuckets().stream().map(BucketAllocation::getQueries).toList();
        assertThat(alloc.stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);
        assertThat(alloc.get(0)).as("no wildcard queries without topics").isZero();
        assertThat(alloc.subList(1, 4)).containsOnly(1);
    }
}
