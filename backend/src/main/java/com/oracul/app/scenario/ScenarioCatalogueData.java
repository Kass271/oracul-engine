package com.oracul.app.scenario;

import com.oracul.app.api.model.CustomWildcard;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.HorizonOption;
import com.oracul.app.api.model.OutputSettings;
import com.oracul.app.api.model.ScenarioCatalogue;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.ScenarioLimits;
import com.oracul.app.api.model.WildcardCategory;
import com.oracul.app.api.model.WildcardDefinition;
import java.util.ArrayList;
import java.util.List;

/** Static scenario catalogue (spec: scenario-panel.md). */
public final class ScenarioCatalogueData {

    private static final String[][] HORIZONS = {
        {"1d", "Tomorrow"}, {"1w", "1 week"}, {"1m", "1 month"}, {"1y", "1 year"},
        {"5y", "5 years"}, {"10y", "10 years"}, {"20y", "20 years"},
    };

    /** category id, label, then pairs of wildcard id and label. */
    private static final String[][] CATEGORIES = {
        {"ai", "AI", "ai-agi-breakthrough", "AGI breakthrough", "ai-stagnation", "AI stagnation",
            "ai-loss-of-control", "AI loss of control"},
        {"robotics", "Robotics", "robotics-massive-automation", "Massive automation",
            "robotics-humanoid-boom", "Humanoid robot boom", "robotics-robot-uprising", "Robot uprising"},
        {"biology", "Biology", "biology-new-pandemic", "New pandemic", "biology-dangerous-mutation",
            "Dangerous mutation", "biology-medical-breakthrough", "Major medical breakthrough",
            "biology-synthetic-biology", "Synthetic biology breakthrough"},
        {"political", "Political / institutional", "political-democracy-strengthens",
            "Democratic institutions strengthen", "political-authoritarian-expansion",
            "Authoritarian systems expand", "political-international-institutions",
            "International institutions strengthen", "political-global-fragmentation",
            "Global fragmentation increases"},
        {"economy", "Economy", "economy-global-boom", "Global economic boom", "economy-global-recession",
            "Global recession", "economy-financial-crisis", "Financial crisis"},
        {"energy", "Energy", "energy-fusion-breakthrough", "Fusion breakthrough", "energy-cheap-energy",
            "Cheap energy", "energy-energy-crisis", "Energy crisis"},
        {"environment", "Environment", "environment-extreme-climate-event", "Extreme climate event",
            "environment-climate-stabilization", "Climate stabilization", "environment-ecosystem-collapse",
            "Ecosystem collapse"},
        {"space", "Space", "space-major-discovery", "Major space discovery", "space-asteroid-threat",
            "Asteroid threat", "space-moon-settlement", "Moon settlement", "space-mars-breakthrough",
            "Mars breakthrough"},
        {"extreme", "Extreme speculation", "extreme-alien-contact", "Alien contact",
            "extreme-unknown-intelligence", "Unknown intelligence", "extreme-unexplained-phenomenon",
            "Unexplained global phenomenon"},
    };

    public static ScenarioCatalogue catalogue() {
        List<WildcardCategory> categories = new ArrayList<>();
        for (String[] c : CATEGORIES) {
            List<WildcardDefinition> defs = new ArrayList<>();
            for (int i = 2; i < c.length; i += 2) {
                defs.add(new WildcardDefinition(c[i], c[i + 1], c[0]));
            }
            categories.add(new WildcardCategory(c[0], c[1], defs));
        }
        List<HorizonOption> horizons = new ArrayList<>();
        for (String[] h : HORIZONS) {
            horizons.add(new HorizonOption(HorizonCode.fromValue(h[0]), h[1]));
        }
        ScenarioLimits limits = new ScenarioLimits(1, 10, 3, 40, 5);
        return new ScenarioCatalogue(categories, horizons, defaults(), limits);
    }

    public static ScenarioConfiguration defaults() {
        return new ScenarioConfiguration(8, 5, 5, HorizonCode._1Y, new ArrayList<>(),
            new ArrayList<CustomWildcard>(), new OutputSettings(true, false));
    }
}
