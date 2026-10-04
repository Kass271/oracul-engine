package com.oracul.app.reasoning;

import com.oracul.app.api.model.CausalStep;
import com.oracul.app.api.model.StructuredScenario;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Distinctness of an alternative scenario from the futures to avoid (FR-30). Pure. */
public final class AlternativeDistinctness {

    private AlternativeDistinctness() {
    }

    static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Empty when the candidate is distinct; {@code avoided.get(0)} is the parent. */
    public static List<String> findings(StructuredScenario candidate, List<AvoidedFutures.AvoidedFuture> avoided) {
        List<String> out = new ArrayList<>();
        String title = normalize(candidate.getFutureEvent().getTitle());
        for (int i = 0; i < avoided.size(); i++) {
            if (normalize(avoided.get(i).title()).equals(title)) {
                out.add("same future event title as future " + (i + 1));
            }
        }
        if (!avoided.isEmpty()) {
            Set<String> parent = new HashSet<>();
            for (String st : avoided.get(0).steps()) {
                parent.add(normalize(st));
            }
            boolean allKnown = true;
            for (CausalStep step : candidate.getCausalChain()) {
                if (!parent.contains(normalize(step.getStatement()))) {
                    allKnown = false;
                    break;
                }
            }
            if (allKnown) {
                out.add("same causal steps as future 1");
            }
        }
        return out;
    }
}
