package com.oracul.app.research;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shared expectations of fixture F240 (budget 18, one Google request per query): its usable candidates in arrival order and the topic of an intent. */
final class F240Support {

    private F240Support() {
    }

    /** Topic of a query's intent (phase-01 mapping: WILDCARD topicKey, ADJACENT category or "general", MAJOR, UNEXPECTED). */
    static String topicOf(Map<String, Object> intent) {
        return switch ((String) intent.get("bucket")) {
            case "WILDCARD" -> (String) intent.get("topicKey");
            case "ADJACENT" -> intent.get("category") == null ? "general" : (String) intent.get("category");
            case "MAJOR" -> "major";
            default -> "unexpected";
        };
    }

    /**
     * The 205 usable candidates of F240 in arrival order: name of the article page (also the last path segment of the resolved
     * URL), topic of its first query, source quality 0.85.
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
