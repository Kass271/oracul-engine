package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.CustomWildcard;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.OutputSettings;
import com.oracul.app.api.model.ResearchCounts;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.ScenarioMetadata;
import com.oracul.app.api.model.WildcardDisplay;
import com.oracul.app.api.model.WildcardSetting;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** future-result.md "Slice 09_future-story" FR-25: ScenarioMetadataMapper rows (A, B, 1d, custom, reversed). */
// @trace FR-25
class ScenarioMetadataMapperTest {

    private static final ResearchCounts COUNTS = new ResearchCounts(20, 31, 12, 7, 2, 1, 2);

    private static ScenarioConfiguration a() {
        return new ScenarioConfiguration(8, 9, 2, HorizonCode._5Y,
            new ArrayList<>(List.of(new WildcardSetting("biology-new-pandemic", 8), new WildcardSetting("robotics-humanoid-boom", 6))),
            new ArrayList<>(), new OutputSettings(true, false));
    }

    private static ScenarioConfiguration b() {
        return new ScenarioConfiguration(8, 5, 5, HorizonCode._1Y, new ArrayList<>(), new ArrayList<>(), new OutputSettings(true, false));
    }

    private static List<String> render(ScenarioMetadata m) {
        return m.getWildcards().stream().map(w -> w.getLabel() + "|" + w.getIntensity() + "|" + w.getCustom()).toList();
    }

    @Test
    void bodyAGivesTheCatalogueLabelsInConfigurationOrder() {
        ScenarioMetadata m = StoryHarness.map(a(), COUNTS);
        assertThat(m.getHorizonLabel()).isEqualTo("5 years");
        assertThat(render(m)).containsExactly("New pandemic|8|false", "Humanoid robot boom|6|false");
        assertThat(m.getConfiguration()).isEqualTo(a());
        assertThat(m.getCounts()).isEqualTo(COUNTS);
    }

    @Test
    void bodyBHasNoWildcards() {
        ScenarioMetadata m = StoryHarness.map(b(), COUNTS);
        assertThat(m.getHorizonLabel()).isEqualTo("1 year");
        assertThat(m.getWildcards()).isNotNull().isEmpty();
    }

    @Test
    void horizonOneDayIsTomorrow() {
        ScenarioConfiguration cfg = b();
        cfg.setHorizon(HorizonCode._1D);
        assertThat(StoryHarness.map(cfg, COUNTS).getHorizonLabel()).isEqualTo("Tomorrow");
    }

    @Test
    void customWildcardsFollowTheCatalogueOnes() {
        ScenarioConfiguration cfg = a();
        cfg.setCustomWildcards(new ArrayList<>(List.of(new CustomWildcard("Mars colony", 7))));
        List<WildcardDisplay> w = StoryHarness.map(cfg, COUNTS).getWildcards();
        assertThat(w).hasSize(3);
        assertThat(w.get(2).getLabel()).isEqualTo("Mars colony");
        assertThat(w.get(2).getIntensity()).isEqualTo(7);
        assertThat(w.get(2).getCustom()).isTrue();
    }

    @Test
    void reversedConfigurationOrderIsKept() {
        ScenarioConfiguration cfg = a();
        cfg.setWildcards(new ArrayList<>(List.of(new WildcardSetting("robotics-humanoid-boom", 6), new WildcardSetting("biology-new-pandemic", 8))));
        assertThat(render(StoryHarness.map(cfg, COUNTS))).containsExactly("Humanoid robot boom|6|false", "New pandemic|8|false");
    }
}
