package com.oracul.app.scenario;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// @trace FR-4
@WebMvcTest
class WildcardCatalogueTest {

    /** {categoryId, categoryLabel, id1, label1, id2, label2, ...} in table order. */
    private static final String[][] TABLE = {
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

    @Autowired
    private MockMvc mvc;

    private ResultActions catalogue() throws Exception {
        return mvc.perform(get("/api/scenario/catalogue")).andExpect(status().isOk());
    }

    // @trace FR-4
    @Test
    void serves_nine_categories_in_table_order_with_labels() throws Exception {
        ResultActions r = catalogue().andExpect(jsonPath("$.categories", hasSize(9)));
        for (int i = 0; i < TABLE.length; i++) {
            r.andExpect(jsonPath("$.categories[" + i + "].id").value(TABLE[i][0]))
                .andExpect(jsonPath("$.categories[" + i + "].label").value(TABLE[i][1]));
        }
    }

    // @trace FR-4
    @Test
    void every_category_lists_its_wildcards_in_table_order() throws Exception {
        ResultActions r = catalogue();
        int total = 0;
        for (int i = 0; i < TABLE.length; i++) {
            int n = (TABLE[i].length - 2) / 2;
            total += n;
            r.andExpect(jsonPath("$.categories[" + i + "].wildcards", hasSize(n)));
            for (int j = 0; j < n; j++) {
                String p = "$.categories[" + i + "].wildcards[" + j + "]";
                r.andExpect(jsonPath(p + ".id").value(TABLE[i][2 + 2 * j]))
                    .andExpect(jsonPath(p + ".label").value(TABLE[i][3 + 2 * j]))
                    .andExpect(jsonPath(p + ".categoryId").value(TABLE[i][0]));
            }
        }
        org.assertj.core.api.Assertions.assertThat(total).isEqualTo(30);
    }

    // @trace FR-4
    @Test
    void wildcard_ids_are_unique_across_the_catalogue() throws Exception {
        String body = catalogue().andReturn().getResponse().getContentAsString();
        java.util.List<String> ids = com.jayway.jsonpath.JsonPath.read(body, "$.categories[*].wildcards[*].id");
        org.assertj.core.api.Assertions.assertThat(ids).hasSize(30).doesNotHaveDuplicates();
    }

    // @trace FR-4
    @Test
    void defaults_have_no_wildcards_enabled() throws Exception {
        catalogue().andExpect(jsonPath("$.defaults.wildcards", hasSize(0)));
    }
}
