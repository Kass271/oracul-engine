package com.oracul.app.research;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shared expectations of fixture F240 (F240_BODY: 9 pipelines x 2 queries = 18, one Google request per query): its usable candidates in arrival order and the topic of a pipeline. */
final class F240Support {

    private F240Support() {
    }

    /** Topic of a query's pipeline: its topicKey (catalogue id / custom-n), or "major" for the GENERAL pipeline. */
    static String topicOf(Map<String, Object> pipeline) {
        Object key = pipeline.get("topicKey");
        return key == null ? "major" : (String) key;
    }

    /**
     * The 205 usable candidates of F240 in arrival order: name of the article page (also the last path segment of the resolved
     * URL), topic of its first query (= the topicKey of that query's pipeline W(ceil(r/2))), source quality 0.85.
     */
    static List<CapOracle.Cand> usableCandidates(List<String> topicOfQuery) {
        List<CapOracle.Cand> out = new ArrayList<>();
        for (int r = 1; r <= 18; r++) {
            int n = r <= 6 ? 14 : 13;
            for (int a = 1; a <= n; a++) {
                if (a == 1 && r > 1) continue; // "shared" seen before: only adds a query id
                if (a == 2 && r <= 5) continue; // blank title
                if (a == 2 && r >= 6 && r <= 15) continue; // unusable link / 200 days old
                if (a == 3 && r >= 16) continue; // the a2 entry of these queries is the same URL as a3 and came first
                String name = a == 1 ? "shared" : "r" + r + "-a" + (a == 2 ? 3 : a);
                out.add(new CapOracle.Cand(name, topicOfQuery.get(r - 1), 0.85));
            }
        }
        return out;
    }
}
