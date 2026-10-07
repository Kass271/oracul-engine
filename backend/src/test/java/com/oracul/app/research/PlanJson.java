package com.oracul.app.research;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads the wire form of a run's search plan (GET /api/runs/{id}/research): from this phase on (wildcard-search.md FR-50)
 * a new run keeps its queries and their statuses in {@code searchPlan.pipelines[].queries[]}; {@code searchPlan.queries},
 * {@code intents} and {@code buckets} are {@code []}.
 */
public final class PlanJson {

    private PlanJson() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> plan(Map<String, Object> research) {
        return (Map<String, Object>) research.get("searchPlan");
    }

    /** {@code searchPlan.pipelines} (empty when the key is absent). */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> pipelines(Map<String, Object> research) {
        Object p = plan(research).get("pipelines");
        return p == null ? List.of() : (List<Map<String, Object>>) p;
    }

    /** The queries of {@code pipeline}. */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> queriesOf(Map<String, Object> pipeline) {
        return (List<Map<String, Object>>) pipeline.get("queries");
    }

    /** Every query of every pipeline in plan order (pipeline order, then query order). */
    public static List<Map<String, Object>> queries(Map<String, Object> research) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> p : pipelines(research)) out.addAll(queriesOf(p));
        return out;
    }

    /** The texts of {@link #queries}. */
    public static List<String> queryTexts(Map<String, Object> research) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> q : queries(research)) out.add((String) q.get("text"));
        return out;
    }

    /** The pipeline with this id (W01...); fails when there is none. */
    public static Map<String, Object> pipeline(Map<String, Object> research, String id) {
        return pipelines(research).stream().filter(p -> id.equals(p.get("id"))).findFirst()
            .orElseThrow(() -> new AssertionError("no pipeline " + id + " in " + plan(research)));
    }
}
