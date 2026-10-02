package com.oracul.app.research;

import com.oracul.app.api.model.CustomWildcard;
import com.oracul.app.api.model.ResearchProfile;
import com.oracul.app.api.model.ResearchTopic;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.WildcardCategory;
import com.oracul.app.api.model.WildcardDefinition;
import com.oracul.app.api.model.WildcardSetting;
import com.oracul.app.scenario.ScenarioCatalogueData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Pure mapping of a scenario configuration to the Research Profile (research-pipeline.md FR-11). */
@Component
public class ResearchProfileFactory {

    private final Map<String, WildcardDefinition> catalogue = new HashMap<>();

    public ResearchProfileFactory() {
        for (WildcardCategory c : ScenarioCatalogueData.catalogue().getCategories()) {
            for (WildcardDefinition d : c.getWildcards()) {
                catalogue.put(d.getId(), d);
            }
        }
    }

    public ResearchProfile from(ScenarioConfiguration cfg) {
        List<ResearchTopic> topics = new ArrayList<>();
        for (WildcardSetting w : cfg.getWildcards()) {
            WildcardDefinition d = catalogue.get(w.getWildcardId());
            topics.add(new ResearchTopic(w.getWildcardId(), d.getLabel(), d.getCategoryId(),
                tenth(w.getIntensity()), false));
        }
        int n = 1;
        for (CustomWildcard c : cfg.getCustomWildcards()) {
            topics.add(new ResearchTopic("custom-" + n++, c.getLabel().trim(), "custom", tenth(c.getIntensity()), true));
        }
        return new ResearchProfile(tenth(cfg.getDarkness()), tenth(cfg.getOptimism()), tenth(cfg.getRealism()),
            cfg.getHorizon(), topics);
    }

    private static double tenth(int value) {
        return BigDecimal.valueOf(value).movePointLeft(1).doubleValue();
    }
}
