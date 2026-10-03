package com.oracul.app.research;

import com.oracul.app.api.model.CustomWildcard;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.OutputSettings;
import com.oracul.app.api.model.ResearchProfile;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.SearchIntent;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.WildcardSetting;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/** Shared builders for slice 05 tests; reaches not-yet-existing classes only by reflection (RED must compile). */
final class PlanSupport {

    static final List<String> TEN_WILDCARDS = List.of(
        "ai-agi-breakthrough", "robotics-humanoid-boom", "biology-new-pandemic", "political-democracy-strengthens",
        "economy-global-boom", "energy-fusion-breakthrough", "environment-extreme-climate-event", "space-major-discovery",
        "extreme-alien-contact", "ai-stagnation");

    private PlanSupport() {
    }

    static ScenarioConfiguration cfg(int realism, int darkness, int optimism, HorizonCode horizon,
                                     List<WildcardSetting> wildcards, List<CustomWildcard> custom) {
        return new ScenarioConfiguration(realism, darkness, optimism, horizon, wildcards, custom, new OutputSettings(true, false));
    }

    static ScenarioConfiguration cfgA() {
        return cfg(8, 9, 2, HorizonCode._5Y,
            List.of(new WildcardSetting("biology-new-pandemic", 8), new WildcardSetting("robotics-humanoid-boom", 6)), List.of());
    }

    static ResearchProfile profile(ScenarioConfiguration cfg) {
        return new ResearchProfileFactory().from(cfg);
    }

    static Object newInstance(String className) {
        Class<?> type;
        try {
            type = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(className + " is missing");
        }
        try {
            Constructor<?> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new AssertionError(className + " needs a public/package no-arg constructor (pure class, no Spring)");
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError(className + " cannot be instantiated: " + e);
        }
    }

    /** SearchPlanner.plan(profile, configuration, queryBudget) - the template plan. */
    static SearchPlan plan(ScenarioConfiguration cfg, int budget) {
        Object planner = newInstance("com.oracul.app.research.SearchPlanner");
        try {
            return (SearchPlan) planner.getClass()
                .getMethod("plan", ResearchProfile.class, ScenarioConfiguration.class, int.class)
                .invoke(planner, profile(cfg), cfg, budget);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError("SearchPlanner.plan(ResearchProfile, ScenarioConfiguration, int) is missing: " + e);
        } catch (InvocationTargetException e) {
            throw new AssertionError("SearchPlanner.plan failed: " + e.getTargetException(), e.getTargetException());
        }
    }

    @SuppressWarnings("unchecked")
    static List<String> templates(SearchIntent intent) {
        Object t = newInstance("com.oracul.app.research.QueryTemplates");
        try {
            return (List<String>) t.getClass().getMethod("forIntent", SearchIntent.class).invoke(t, intent);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError("QueryTemplates.forIntent(SearchIntent) is missing: " + e);
        } catch (InvocationTargetException e) {
            throw new AssertionError("QueryTemplates.forIntent failed: " + e.getTargetException(), e.getTargetException());
        }
    }
}
