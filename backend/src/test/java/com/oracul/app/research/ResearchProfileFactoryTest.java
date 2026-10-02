package com.oracul.app.research;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.oracul.app.api.model.CustomWildcard;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.OutputSettings;
import com.oracul.app.api.model.ResearchProfile;
import com.oracul.app.api.model.ResearchTopic;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.WildcardSetting;
import java.lang.reflect.Constructor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure unit table of research-pipeline.md "Slice 04_run-start - FR-11 test contract". */
// @trace FR-11
class ResearchProfileFactoryTest {

    private static ResearchProfile from(ScenarioConfiguration cfg) throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.oracul.app.research.ResearchProfileFactory");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("com.oracul.app.research.ResearchProfileFactory is missing");
        }
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object factory = ctor.newInstance();
        return (ResearchProfile) type.getMethod("from", ScenarioConfiguration.class).invoke(factory, cfg);
    }

    private static ScenarioConfiguration cfg(int realism, int darkness, int optimism, HorizonCode horizon,
                                             List<WildcardSetting> wildcards, List<CustomWildcard> custom) {
        return new ScenarioConfiguration(realism, darkness, optimism, horizon, wildcards, custom, new OutputSettings(true, false));
    }

    private static final ResearchTopic PANDEMIC = new ResearchTopic("biology-new-pandemic", "New pandemic", "biology", 0.8, false);
    private static final ResearchTopic HUMANOID = new ResearchTopic("robotics-humanoid-boom", "Humanoid robot boom", "robotics", 0.6, false);

    @Test
    void acceptanceConfigurationMapsEveryField() throws Exception {
        ResearchProfile p = from(cfg(8, 9, 2, HorizonCode._5Y,
            List.of(new WildcardSetting("biology-new-pandemic", 8), new WildcardSetting("robotics-humanoid-boom", 6)), List.of()));
        assertEquals(0.9, p.getDarkness());
        assertEquals(0.2, p.getOptimism());
        assertEquals(0.8, p.getRealism());
        assertEquals(HorizonCode._5Y, p.getHorizon());
        assertEquals(List.of(PANDEMIC, HUMANOID), p.getTopics());
    }

    @Test
    void topicsKeepRequestOrder() throws Exception {
        ResearchProfile p = from(cfg(8, 9, 2, HorizonCode._5Y,
            List.of(new WildcardSetting("robotics-humanoid-boom", 6), new WildcardSetting("biology-new-pandemic", 8)), List.of()));
        assertEquals(List.of(HUMANOID, PANDEMIC), p.getTopics());
    }

    @Test
    void noWildcardsGivesEmptyTopics() throws Exception {
        ResearchProfile p = from(cfg(8, 5, 5, HorizonCode._1Y, List.of(), List.of()));
        assertEquals(0.5, p.getDarkness());
        assertEquals(0.5, p.getOptimism());
        assertEquals(0.8, p.getRealism());
        assertEquals(HorizonCode._1Y, p.getHorizon());
        assertEquals(List.of(), p.getTopics());
    }

    @Test
    void minimumAndMaximumValues() throws Exception {
        ResearchProfile lo = from(cfg(1, 1, 1, HorizonCode._1D, List.of(), List.of()));
        assertEquals(0.1, lo.getDarkness());
        assertEquals(0.1, lo.getOptimism());
        assertEquals(0.1, lo.getRealism());
        assertEquals(HorizonCode._1D, lo.getHorizon());
        ResearchProfile hi = from(cfg(10, 10, 10, HorizonCode._20Y, List.of(), List.of()));
        assertEquals(1.0, hi.getDarkness());
        assertEquals(1.0, hi.getOptimism());
        assertEquals(1.0, hi.getRealism());
        assertEquals(HorizonCode._20Y, hi.getHorizon());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void everyValueIsExactlyOneTenthWithoutFloatingArtefacts(int v) throws Exception {
        double expected = Double.parseDouble(v / 10 + "." + v % 10);
        ResearchProfile p = from(cfg(v, v, v, HorizonCode._1Y, List.of(), List.of()));
        assertEquals(expected, p.getDarkness());
        assertEquals(expected, p.getOptimism());
        assertEquals(expected, p.getRealism());
    }

    @Test
    void darkness3Optimism7() throws Exception {
        ResearchProfile p = from(cfg(8, 3, 7, HorizonCode._1Y, List.of(), List.of()));
        assertEquals(0.3, p.getDarkness());
        assertEquals(0.7, p.getOptimism());
    }

    @Test
    void wildcardIntensityIsWeightOverTen() throws Exception {
        ResearchProfile p = from(cfg(8, 5, 5, HorizonCode._1Y,
            List.of(new WildcardSetting("biology-new-pandemic", 1), new WildcardSetting("robotics-humanoid-boom", 10)), List.of()));
        assertEquals(0.1, p.getTopics().get(0).getWeight());
        assertEquals(1.0, p.getTopics().get(1).getWeight());
    }

    @Test
    void customWildcardsFollowCatalogueTopicsWithTrimmedLabel() throws Exception {
        ResearchProfile p = from(cfg(8, 5, 5, HorizonCode._1Y,
            List.of(new WildcardSetting("biology-new-pandemic", 8)), List.of(new CustomWildcard("  Mars colony ", 7))));
        assertEquals(2, p.getTopics().size());
        assertEquals(PANDEMIC, p.getTopics().get(0));
        assertEquals(new ResearchTopic("custom-1", "Mars colony", "custom", 0.7, true), p.getTopics().get(1));
    }
}
