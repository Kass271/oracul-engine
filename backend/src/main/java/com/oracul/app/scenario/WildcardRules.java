package com.oracul.app.scenario;

import com.oracul.app.api.model.WildcardSetting;
import com.oracul.app.api.model.CustomWildcard;
import com.oracul.app.api.model.OutputSettings;
import com.oracul.app.api.model.ScenarioConfiguration;
import java.util.HashSet;
import java.util.Locale;
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

    /** Wildcards, then custom wildcards, then output; the first violation wins. */
    public static Optional<String> firstViolation(ScenarioConfiguration cfg) {
        Optional<String> w = firstViolation(cfg.getWildcards());
        if (w.isPresent()) return w;
        Optional<String> c = customViolation(cfg.getCustomWildcards());
        if (c.isPresent()) return c;
        OutputSettings o = cfg.getOutput();
        if (o == null || !Boolean.TRUE.equals(o.getStory())) return Optional.of("Story output is required");
        if (!Boolean.FALSE.equals(o.getIllustration())) return Optional.of("Illustration is not available yet (MVP+1)");
        return Optional.empty();
    }

    static Optional<String> customViolation(List<CustomWildcard> list) {
        if (list == null) return Optional.of("customWildcards is invalid");
        if (list.size() > 3) return Optional.of("At most 3 custom wildcards");
        Set<String> seen = new HashSet<>();
        for (CustomWildcard c : list) {
            String label = c == null ? null : c.getLabel();
            if (label == null || label.isEmpty() || label.length() > 40) {
                return Optional.of("Wildcard name must be 1\u201340 characters");
            }
            if (!seen.add(label.toLowerCase(Locale.ROOT))) return Optional.of("This wildcard already exists");
            Integer v = c.getIntensity();
            if (v == null || v < 1 || v > 10) return Optional.of(INTENSITY_MESSAGE);
        }
        return Optional.empty();
    }
}
