package com.oracul.app.research;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Test-side oracle of news-search.md FR-46 "At most 30 sources per run": a plain re-statement of the specified selection,
 * independent of the production code. Candidates are given in arrival order; the result is the indexes of the kept
 * candidates in arrival order.
 */
final class CapOracle {

    static final int MAX = 30;

    /** A usable candidate: topic (may be null), source quality of its publisher. Arrival order = list order. */
    record Cand(String name, String topic, double quality) {}

    private CapOracle() {
    }

    static List<Integer> select(List<Cand> candidates) {
        int n = candidates.size();
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < n; i++) all.add(i);
        if (n <= MAX) return all;
        // a. buckets by topic, ordered by the arrival index of their first candidate (null topic = one bucket of its own)
        Map<String, List<Integer>> buckets = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String key = candidates.get(i).topic() == null ? "\u0000none" : candidates.get(i).topic();
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        // b. rank inside a bucket: quality descending, ties by arrival ascending
        for (List<Integer> bucket : buckets.values()) {
            bucket.sort((a, b) -> {
                int c = Double.compare(candidates.get(b).quality(), candidates.get(a).quality());
                return c != 0 ? c : Integer.compare(a, b);
            });
        }
        // c. round robin
        TreeSet<Integer> kept = new TreeSet<>();
        int round = 0;
        while (kept.size() < MAX) {
            boolean any = false;
            for (List<Integer> bucket : buckets.values()) {
                if (kept.size() >= MAX) break;
                if (round < bucket.size()) {
                    kept.add(bucket.get(round));
                    any = true;
                }
            }
            if (!any) break;
            round++;
        }
        return new ArrayList<>(kept);
    }
}
