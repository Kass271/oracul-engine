package com.oracul.app.research;

import com.oracul.app.api.model.BucketAllocation;
import com.oracul.app.api.model.HorizonOption;
import com.oracul.app.api.model.QueryBucket;
import com.oracul.app.api.model.QueryExpansionMode;
import com.oracul.app.api.model.ResearchProfile;
import com.oracul.app.api.model.ResearchTopic;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.SearchIntent;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import com.oracul.app.api.model.WildcardCategory;
import com.oracul.app.scenario.ScenarioCatalogueData;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Pure construction of the template search plan (research-pipeline.md FR-12). */
@Component
public class SearchPlanner {

    static final String DARK = " — risks, threats, failures and warnings";
    static final String OPT = " — breakthroughs, recoveries and opportunities";
    static final String BOTH = " — risks, threats, failures and warnings; breakthroughs, recoveries and opportunities";

    private final QueryTemplates templates = new QueryTemplates();
    private final Map<String, String> horizonLabels = new HashMap<>();
    private final Map<String, String> categoryLabels = new HashMap<>();

    public SearchPlanner() {
        var catalogue = ScenarioCatalogueData.catalogue();
        for (HorizonOption h : catalogue.getHorizons()) {
            horizonLabels.put(h.getCode().getValue(), h.getLabel());
        }
        for (WildcardCategory c : catalogue.getCategories()) {
            categoryLabels.put(c.getId(), c.getLabel());
        }
    }

    public SearchPlan plan(ResearchProfile profile, ScenarioConfiguration cfg, int queryBudget) {
        List<ResearchTopic> topics = profile.getTopics();
        boolean hasTopics = !topics.isEmpty();

        // ---- buckets ----
        QueryBucket[] order = QueryBucket.values();
        int[] units = hasTopics ? new int[] {4, 3, 2, 1} : new int[] {0, 3, 2, 1};
        int total = hasTopics ? 10 : 6;
        int[] alloc = new int[4];
        long[] rem = new long[4];
        int assigned = 0;
        for (int i = 0; i < 4; i++) {
            long prod = (long) queryBudget * units[i];
            alloc[i] = (int) (prod / total);
            rem[i] = prod % total;
            assigned += alloc[i];
        }
        for (int left = queryBudget - assigned; left > 0; left--) {
            int best = -1;
            for (int i = 0; i < 4; i++) {
                if (units[i] > 0 && (best < 0 || rem[i] > rem[best])) {
                    best = i;
                }
            }
            alloc[best]++;
            rem[best] = -1;
        }
        for (int i = 0; i < 4; i++) {
            if (units[i] > 0 && alloc[i] == 0) {
                int max = 0;
                for (int j = 1; j < 4; j++) {
                    if (alloc[j] > alloc[max]) {
                        max = j;
                    }
                }
                alloc[max]--;
                alloc[i] = 1;
            }
        }
        double[] shares = hasTopics ? new double[] {0.4, 0.3, 0.2, 0.1} : new double[] {0.0, 0.5, 0.3333, 0.1667};
        List<BucketAllocation> buckets = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            buckets.add(new BucketAllocation(order[i], BigDecimal.valueOf(shares[i]).setScale(4, RoundingMode.HALF_UP)
                .doubleValue(), alloc[i]));
        }

        // ---- intents ----
        String suffix = suffix(cfg);
        List<String> baseDriven = new ArrayList<>();
        if (cfg.getDarkness() >= 6) {
            baseDriven.add("Darkness " + cfg.getDarkness() + "/10");
        }
        if (cfg.getOptimism() >= 6) {
            baseDriven.add("Optimism " + cfg.getOptimism() + "/10");
        }
        String horizon = "Horizon " + horizonLabels.get(cfg.getHorizon().getValue());

        List<SearchIntent> intents = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();

        if (hasTopics) {
            int[] weights = new int[topics.size()];
            for (int i = 0; i < weights.length; i++) {
                weights[i] = (int) Math.round(topics.get(i).getWeight() * 10);
            }
            int[] split = split(alloc[0], weights, true);
            for (int i = 0; i < topics.size(); i++) {
                ResearchTopic t = topics.get(i);
                List<String> driven = new ArrayList<>();
                driven.add(t.getLabel() + " " + weights[i] + "/10");
                driven.addAll(baseDriven);
                driven.add(horizon);
                intents.add(new SearchIntent(null, QueryBucket.WILDCARD,
                    "Current developments related to " + t.getLabel() + suffix, driven).topicKey(t.getKey()));
                counts.add(split[i]);
            }
        }
        List<String> major = new ArrayList<>(baseDriven);
        major.add(horizon);
        intents.add(new SearchIntent(null, QueryBucket.MAJOR, "Major current world events" + suffix, major));
        counts.add(alloc[1]);

        List<String> adjacentCategories = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResearchTopic t : topics) {
            if (!t.getCustom() && seen.add(t.getCategory())) {
                adjacentCategories.add(t.getCategory());
            }
        }
        if (adjacentCategories.isEmpty()) {
            intents.add(new SearchIntent(null, QueryBucket.ADJACENT,
                "Adjacent developments in science, technology and economy" + suffix, new ArrayList<>(major)));
            counts.add(alloc[2]);
        } else {
            int[] ones = new int[adjacentCategories.size()];
            java.util.Arrays.fill(ones, 1);
            int[] split = split(alloc[2], ones, false);
            for (int i = 0; i < adjacentCategories.size(); i++) {
                String cat = adjacentCategories.get(i);
                intents.add(new SearchIntent(null, QueryBucket.ADJACENT,
                    "Adjacent developments in " + categoryLabels.get(cat) + suffix, new ArrayList<>(major))
                    .category(cat));
                counts.add(split[i]);
            }
        }

        List<String> unexpected = new ArrayList<>(baseDriven);
        if (cfg.getRealism() <= 5) {
            unexpected.add("Realism " + cfg.getRealism() + "/10");
        }
        unexpected.add(horizon);
        intents.add(new SearchIntent(null, QueryBucket.UNEXPECTED, "Unusual early signals and research" + suffix,
            unexpected));
        counts.add(alloc[3]);

        // ---- ids and template queries ----
        List<SearchIntent> numbered = new ArrayList<>();
        List<SearchQuery> queries = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (int i = 0; i < intents.size(); i++) {
            SearchIntent in = intents.get(i);
            in.setId(String.format("I%02d", i + 1));
            numbered.add(in);
            List<String> list = templates.forIntent(in);
            int taken = 0;
            for (String text : list) {
                if (taken >= counts.get(i)) {
                    break;
                }
                if (used.add(text.toLowerCase(Locale.ROOT))) {
                    queries.add(new SearchQuery(String.format("Q%02d", queries.size() + 1), in.getId(), in.getBucket(),
                        text, SearchQueryStatus.PENDING, 0));
                    taken++;
                }
            }
        }
        return new SearchPlan(queryBudget, QueryExpansionMode.TEMPLATE_FALLBACK, buckets, numbered, queries);
    }

    private static String suffix(ScenarioConfiguration cfg) {
        boolean dark = cfg.getDarkness() >= 6;
        boolean opt = cfg.getOptimism() >= 6;
        return dark && opt ? BOTH : dark ? DARK : opt ? OPT : "";
    }

    /**
     * Largest-remainder split of {@code total} over weights. More weights than queries: the heaviest (ties earlier)
     * get one each. Zero shares take one from the biggest share (ties later when {@code laterTie}).
     */
    private static int[] split(int total, int[] weights, boolean laterTie) {
        int n = weights.length;
        int[] out = new int[n];
        if (n == 0) {
            return out;
        }
        if (n > total) {
            boolean[] picked = new boolean[n];
            for (int k = 0; k < total; k++) {
                int best = -1;
                for (int i = 0; i < n; i++) {
                    if (!picked[i] && (best < 0 || weights[i] > weights[best])) {
                        best = i;
                    }
                }
                picked[best] = true;
                out[best] = 1;
            }
            return out;
        }
        long sum = 0;
        for (int w : weights) {
            sum += w;
        }
        long[] rem = new long[n];
        int assigned = 0;
        for (int i = 0; i < n; i++) {
            long prod = (long) total * weights[i];
            out[i] = (int) (prod / sum);
            rem[i] = prod % sum;
            assigned += out[i];
        }
        for (int left = total - assigned; left > 0; left--) {
            int best = 0;
            for (int i = 1; i < n; i++) {
                if (rem[i] > rem[best]) {
                    best = i;
                }
            }
            out[best]++;
            rem[best] = -1;
        }
        for (int i = 0; i < n; i++) {
            if (out[i] == 0) {
                int max = 0;
                for (int j = 1; j < n; j++) {
                    if (laterTie ? out[j] >= out[max] : out[j] > out[max]) {
                        max = j;
                    }
                }
                out[max]--;
                out[i] = 1;
            }
        }
        return out;
    }
}
