package com.oracul.app.scenario;

import com.oracul.app.api.model.WildcardSetting;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Semantic validation of the wildcards list (spec: scenario-panel.md, FR-4). */
public final class WildcardRules {

    public static final String INTENSITY_MESSAGE = "wildcard intensity must be between 1 and 10";

    private WildcardRules() {
    }

    /** First failing element wins; per element: id present, id known, not duplicate, intensity in range. */
    public static Optional<String> firstViolation(List<WildcardSetting> wildcards) {
        if (wildcards == null) return Optional.of("wildcards is invalid");
        Set<String> known = ScenarioCatalogueData.wildcardIds();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < wildcards.size(); i++) {
            WildcardSetting w = wildcards.get(i);
            if (w == null || w.getWildcardId() == null) {
                return Optional.of("wildcards[" + i + "].wildcardId is invalid");
            }
            String id = w.getWildcardId();
            if (!known.contains(id)) return Optional.of("unknown wildcard: " + id);
            if (!seen.add(id)) return Optional.of("duplicate wildcard: " + id);
            Integer v = w.getIntensity();
            if (v == null || v < 1 || v > 10) return Optional.of(INTENSITY_MESSAGE);
        }
        return Optional.empty();
    }
}
