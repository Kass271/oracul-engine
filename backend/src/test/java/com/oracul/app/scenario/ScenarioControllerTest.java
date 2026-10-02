package com.oracul.app.scenario;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// @trace FR-1, FR-2, FR-3
@WebMvcTest
class ScenarioControllerTest {

    private static final String[][] HORIZONS = {
        {"1d", "Tomorrow"}, {"1w", "1 week"}, {"1m", "1 month"}, {"1y", "1 year"},
        {"5y", "5 years"}, {"10y", "10 years"}, {"20y", "20 years"},
    };

    @Autowired
    private MockMvc mvc;

    // @trace FR-3
    @Test
    void catalogueServesSevenHorizonsInOrder() throws Exception {
        var result = mvc.perform(get("/api/scenario/catalogue"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.horizons", hasSize(7)));
        for (int i = 0; i < HORIZONS.length; i++) {
            result.andExpect(jsonPath("$.horizons[" + i + "].code").value(HORIZONS[i][0]))
                .andExpect(jsonPath("$.horizons[" + i + "].label").value(HORIZONS[i][1]));
        }
    }

    // @trace FR-2, FR-3
    @Test
    void catalogueServesDefaults() throws Exception {
        mvc.perform(get("/api/scenario/catalogue"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.defaults.realism").value(8))
            .andExpect(jsonPath("$.defaults.darkness").value(5))
            .andExpect(jsonPath("$.defaults.optimism").value(5))
            .andExpect(jsonPath("$.defaults.horizon").value("1y"))
            .andExpect(jsonPath("$.defaults.wildcards", hasSize(0)))
            .andExpect(jsonPath("$.defaults.customWildcards", hasSize(0)))
            .andExpect(jsonPath("$.defaults.output.story").value(true))
            .andExpect(jsonPath("$.defaults.output.illustration").value(false));
    }

    // @trace FR-2
    @Test
    void catalogueServesLimitsAndCategoryArray() throws Exception {
        mvc.perform(get("/api/scenario/catalogue"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limits.intensityMin").value(1))
            .andExpect(jsonPath("$.limits.intensityMax").value(10))
            .andExpect(jsonPath("$.limits.customWildcardMax").value(3))
            .andExpect(jsonPath("$.limits.customWildcardLabelMaxLength").value(40))
            .andExpect(jsonPath("$.limits.defaultWildcardIntensity").value(5))
            .andExpect(jsonPath("$.categories", instanceOf(List.class)));
    }

    // @trace FR-1, FR-2, FR-3
    @Test
    void configurationEqualsDefaultsForFreshSession() throws Exception {
        mvc.perform(get("/api/scenario/configuration"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.realism").value(8))
            .andExpect(jsonPath("$.darkness").value(5))
            .andExpect(jsonPath("$.optimism").value(5))
            .andExpect(jsonPath("$.horizon").value("1y"))
            .andExpect(jsonPath("$.wildcards", hasSize(0)))
            .andExpect(jsonPath("$.customWildcards", hasSize(0)))
            .andExpect(jsonPath("$.output.story").value(true))
            .andExpect(jsonPath("$.output.illustration").value(false));
    }

    // @trace FR-1
    @ParameterizedTest
    @ValueSource(strings = {"/api/scenario/catalogue", "/api/scenario/configuration"})
    void scenarioResponsesNeverMentionOracle(String path) throws Exception {
        var body = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body.toLowerCase()).doesNotContain("oracle");
    }
}
