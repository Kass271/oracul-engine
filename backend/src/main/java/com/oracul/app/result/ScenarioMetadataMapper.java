package com.oracul.app.result;

import com.oracul.app.api.model.CustomWildcard;
import com.oracul.app.api.model.ResearchCounts;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.ScenarioMetadata;
import com.oracul.app.api.model.WildcardDisplay;
import com.oracul.app.api.model.WildcardSetting;
import com.oracul.app.scenario.ScenarioCatalogueData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds the metadata panel data from the run snapshot (FR-25). Pure. */
public final class ScenarioMetadataMapper {

    private ScenarioMetadataMapper() {
    }

    public static ScenarioMetadata map(ScenarioConfiguration cfg, ResearchCounts counts) {
        Map<String, String> labels = new HashMap<>();
        ScenarioCatalogueData.catalogue().getCategories()
            .forEach(c -> c.getWildcards().forEach(w -> labels.put(w.getId(), w.getLabel())));
        List<WildcardDisplay> wildcards = new ArrayList<>();
        for (WildcardSetting w : cfg.getWildcards()) {
            wildcards.add(new WildcardDisplay(labels.getOrDefault(w.getWildcardId(), w.getWildcardId()), w.getIntensity(), false));
        }
        for (CustomWildcard c : cfg.getCustomWildcards()) {
            wildcards.add(new WildcardDisplay(c.getLabel(), c.getIntensity(), true));
        }
        return new ScenarioMetadata(cfg, HorizonLabels.label(cfg.getHorizon()), wildcards, counts);
    }
}
