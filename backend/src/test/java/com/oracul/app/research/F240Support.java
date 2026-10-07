package com.oracul.app.research;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared expectations of fixture F240 under the FR-53 selection (article-retrieval.md "Worked fixtures"): F240_BODY = 9 pipelines
 * x 2 queries (Wk = Q(2k-1), Q(2k)), F240_BODY_TEN = 10 pipelines x 2. Every item of Wk scores 3, the link "shared" 3 + 2 = 5;
 * the per-pipeline selection is {@code shared} plus the next three by feed position, then query id. Stated here as plain data
 * plus the round-robin cap, independent of the production selector.
 */
final class F240Support {

    private F240Support() {
    }

    /** Topic of a query's pipeline: its topicKey (catalogue id / custom-n), or "major" for the GENERAL pipeline. */
    static String topicOf(Map<String, Object> pipeline) {
        Object key = pipeline.get("topicKey");
        return key == null ? "major" : (String) key;
    }

    /**
     * Names (last path segment of the article page) the pipeline Wk selects, in its relevance order. Queries of Wk are
     * Q(2k-1), Q(2k); r = 2k-1, s = 2k. With {@code q02Failed} (Q02 answered 503) W01 only has Q01's items.
     */
    static List<String> selected(int k, boolean q02Failed) {
        int r = 2 * k - 1;
        int s = 2 * k;
        if (k == 1 && q02Failed) return List.of("shared", "r1-a3", "r1-a4", "r1-a5");
        if (k == 8) return List.of("shared", "r16-a3", "r15-a3", "r15-a4"); // Q16's a2 entry links to r16-a3 (position 2)
        if (k >= 9) return List.of("shared", "r" + r + "-a3", "r" + s + "-a3", "r" + r + "-a4"); // W09, W10: both a2 entries link to a3
        return List.of("shared", "r" + r + "-a3", "r" + s + "-a3", "r" + r + "-a4"); // W01-W07: a2 dropped, a3 at position 3
    }

    /** Result of the FR-53 cap over {@code pipelines} F240 pipelines: names of the kept sources in Evidence order (S001...). */
    static List<String> keptNames(int pipelines, boolean q02Failed) {
        Set<String> kept = new LinkedHashSet<>();
        for (int round = 0; round < 4; round++) {
            for (int k = 1; k <= pipelines; k++) {
                String name = selected(k, q02Failed).get(round);
                if (kept.contains(name)) continue;
                if (kept.size() < WildcardLimits.MAX_SOURCES) kept.add(name);
            }
        }
        // group of Wk = kept sources among its selections, in its relevance order; Evidence order = groups concatenated, first appearance kept
        Set<String> evidence = new LinkedHashSet<>();
        for (int k = 1; k <= pipelines; k++) {
            for (String name : selected(k, q02Failed)) if (kept.contains(name)) evidence.add(name);
        }
        return new ArrayList<>(evidence);
    }

    /** The names of the group of pipeline k (kept sources among its selections, relevance order). */
    static List<String> group(int k, int pipelines, boolean q02Failed) {
        List<String> kept = keptNames(pipelines, q02Failed);
        List<String> out = new ArrayList<>();
        for (String name : selected(k, q02Failed)) if (kept.contains(name)) out.add(name);
        return out;
    }

    /** Spec constants (article-retrieval.md "Constants"). */
    static final class WildcardLimits {
        static final int MAX_SOURCES = 30;

        private WildcardLimits() {
        }
    }
}
