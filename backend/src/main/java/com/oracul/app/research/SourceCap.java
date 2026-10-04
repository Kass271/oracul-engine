package com.oracul.app.research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** FR-46: at most {@value #MAX} sources per run, spread over the topics by a round robin of the best-ranked candidates. */
public final class SourceCap {

    public static final int MAX = 30;

    private SourceCap() {
    }

    /** The topic and source quality of one usable candidate, in arrival order. */
    public record Ranked(String topic, double quality) {
    }

    /** Indexes (ascending = arrival order) of the kept candidates. */
    public static List<Integer> select(List<Ranked> candidates) {
        int n = candidates.size();
        List<Integer> all = new ArrayList<>();
        if (n <= MAX) {
            for (int i = 0; i < n; i++) {
                all.add(i);
            }
            return all;
        }
        Map<String, List<Integer>> buckets = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String topic = candidates.get(i).topic();
            buckets.computeIfAbsent(topic == null ? "\u0000none" : topic, k -> new ArrayList<>()).add(i);
        }
        Comparator<Integer> rank = Comparator.<Integer>comparingDouble(i -> -candidates.get(i).quality())
            .thenComparingInt(i -> i);
        buckets.values().forEach(b -> b.sort(rank));
        TreeSet<Integer> kept = new TreeSet<>();
        for (int round = 0; kept.size() < MAX; round++) {
            boolean any = false;
            for (List<Integer> bucket : buckets.values()) {
                if (kept.size() >= MAX) {
                    break;
                }
                if (round < bucket.size()) {
                    kept.add(bucket.get(round));
                    any = true;
                }
            }
            if (!any) {
                break;
            }
        }
        return new ArrayList<>(kept);
    }
}
