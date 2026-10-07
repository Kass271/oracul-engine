package com.oracul.app.research;

import com.oracul.app.api.model.CustomWildcard;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.OutputSettings;
import com.oracul.app.api.model.ResearchProfile;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.QueryBucket;
import com.oracul.app.api.model.QueryExpansionMode;
import com.oracul.app.api.model.SearchIntent;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import com.oracul.app.api.model.WildcardCategory;
import com.oracul.app.api.model.WildcardDefinition;
import com.oracul.app.api.model.WildcardPipeline;
import com.oracul.app.api.model.WildcardSetting;
import com.oracul.app.scenario.ScenarioCatalogueData;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared builders of the search-plan tests (slice 04_wildcard-queries: {@code plan(cfg)}, {@code templates(pipeline, cfg)},
 * {@code legacyPlan(texts)}); reaches production classes and methods that may not exist yet only by reflection (RED must compile).
 */
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

    /** SearchPlanner.plan(profile, configuration) - the template plan (one pipeline per profile topic). */
    static SearchPlan plan(ScenarioConfiguration cfg) {
        Object planner = newInstance("com.oracul.app.research.SearchPlanner");
        try {
            return (SearchPlan) planner.getClass()
                .getMethod("plan", ResearchProfile.class, ScenarioConfiguration.class)
                .invoke(planner, profile(cfg), cfg);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError("SearchPlanner.plan(ResearchProfile, ScenarioConfiguration) is missing: " + e);
        } catch (InvocationTargetException e) {
            throw new AssertionError("SearchPlanner.plan failed: " + e.getTargetException(), e.getTargetException());
        }
    }

    /** QueryTemplates.forPipeline(pipeline, configuration) - exactly 3 template texts. */
    @SuppressWarnings("unchecked")
    static List<String> templates(WildcardPipeline pipeline, ScenarioConfiguration cfg) {
        Object t = newInstance("com.oracul.app.research.QueryTemplates");
        try {
            return (List<String>) t.getClass().getMethod("forPipeline", WildcardPipeline.class, ScenarioConfiguration.class)
                .invoke(t, pipeline, cfg);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError("QueryTemplates.forPipeline(WildcardPipeline, ScenarioConfiguration) is missing: " + e);
        } catch (InvocationTargetException e) {
            throw new AssertionError("QueryTemplates.forPipeline failed: " + e.getTargetException(), e.getTargetException());
        }
    }

    /**
     * The phase-01 plan shape the direct-seam ITs build (no pipelines): one intent I01 and Q01...Qn in it, all PENDING.
     * No run creates it any more; SourceRetrieval.search keeps reading it.
     */
    static SearchPlan legacyPlan(List<String> texts) {
        List<SearchQuery> queries = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            queries.add(new SearchQuery(String.format("Q%02d", i + 1), "I01", QueryBucket.WILDCARD, texts.get(i),
                SearchQueryStatus.PENDING, 0));
        }
        SearchIntent intent = new SearchIntent("I01", QueryBucket.WILDCARD, "Current developments related to New pandemic", List.of())
            .topicKey("biology-new-pandemic");
        return new SearchPlan(texts.size(), QueryExpansionMode.TEMPLATE_FALLBACK, new ArrayList<>(), new ArrayList<>(List.of(intent)), queries);
    }

    /** All 30 catalogue wildcard ids in catalogue order. */
    static List<String> catalogueIds() {
        List<String> out = new ArrayList<>();
        for (WildcardCategory c : ScenarioCatalogueData.catalogue().getCategories()) {
            for (WildcardDefinition d : c.getWildcards()) out.add(d.getId());
        }
        return out;
    }

    /** Catalogue label of a wildcard id. */
    static String labelOf(String wildcardId) {
        for (WildcardCategory c : ScenarioCatalogueData.catalogue().getCategories()) {
            for (WildcardDefinition d : c.getWildcards()) if (d.getId().equals(wildcardId)) return d.getLabel();
        }
        throw new AssertionError("no catalogue wildcard " + wildcardId);
    }

    /** Configuration with the first {@code catalogue} catalogue wildcards (intensity {@code intensity}) and {@code custom} custom ones. */
    static ScenarioConfiguration cfgOfSize(int catalogue, int custom) {
        List<WildcardSetting> ws = new ArrayList<>();
        for (int i = 0; i < catalogue; i++) ws.add(new WildcardSetting(catalogueIds().get(i), 5));
        List<CustomWildcard> cws = new ArrayList<>();
        for (int i = 0; i < custom; i++) cws.add(new CustomWildcard("Custom topic " + (i + 1), 5));
        return cfg(8, 9, 2, HorizonCode._5Y, ws, cws);
    }
}
