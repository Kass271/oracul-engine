package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oracul.app.BackendApplication;
import com.oracul.app.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * research-pipeline.md "Slice 07_evidence-pack" configuration: negative weights, all-zero weights, an out-of-range
 * minimum quality, caps and section sizes beyond max-items fail startup.
 */
// @trace FR-16, FR-17
class EvidenceConfigurationTest {

    private static String failureOf(String... properties) {
        try {
            new SpringApplicationBuilder(BackendApplication.class, TestcontainersConfiguration.class)
                .web(WebApplicationType.NONE).properties(properties).run().close();
        } catch (Throwable e) {
            StringBuilder all = new StringBuilder();
            for (Throwable t = e; t != null; t = t.getCause()) all.append(t.getMessage()).append('\n');
            return all.toString();
        }
        throw new AssertionError("the context started with " + String.join(" ", properties));
    }

    @ParameterizedTest(name = "{0}={1} fails startup")
    @CsvSource({
        "oracul.ranking.weights.topic-match,-0.1",
        "oracul.ranking.weights.wildcard-match,-1",
        "oracul.ranking.weights.darkness-match,-0.5",
        "oracul.ranking.weights.optimism-match,-2",
        "oracul.ranking.weights.recency,-0.01",
        "oracul.ranking.weights.source-quality,-1",
        "oracul.ranking.weights.impact,-1",
        "oracul.ranking.weights.trend-strength,-1",
        "oracul.ranking.weights.cross-topic,-1",
        "oracul.ranking.weights.realism-compatibility,-1",
        "oracul.ranking.min-source-quality,-0.1",
        "oracul.ranking.min-source-quality,1.1",
        "oracul.evidence.max-items,0",
        "oracul.evidence.max-items,101",
        "oracul.evidence.core,-1",
        "oracul.evidence.core,101",
        "oracul.evidence.supporting,-1",
        "oracul.evidence.counter-signals,-1",
        "oracul.evidence.max-per-entity,0",
        "oracul.evidence.max-per-publisher,0",
        "oracul.evidence.max-per-geography,0",
        "oracul.evidence.max-category-share,0",
        "oracul.evidence.max-category-share,1.1",
    })
    void anInvalidValueFailsStartupAndNamesItsConfiguration(String property, String value) {
        // the message names the property (or at least its configuration prefix for cross-field rules)
        assertThat(failureOf(property + "=" + value)).contains(property.substring(0, property.lastIndexOf('.')));
    }

    @Test
    void allWeightsZeroFailsStartup() {
        String[] props = new String[10];
        String[] names = {"topic-match", "wildcard-match", "darkness-match", "optimism-match", "recency", "source-quality",
            "impact", "trend-strength", "cross-topic", "realism-compatibility"};
        for (int i = 0; i < names.length; i++) props[i] = "oracul.ranking.weights." + names[i] + "=0";
        assertThatThrownBy(() -> {
            ConfigurableApplicationContext c = new SpringApplicationBuilder(BackendApplication.class, TestcontainersConfiguration.class)
                .web(WebApplicationType.NONE).properties(props).run();
            c.close();
        }).isNotNull();
    }

    @Test
    void sectionSizesBeyondMaxItemsFailStartup() {
        assertThatThrownBy(() -> {
            ConfigurableApplicationContext c = new SpringApplicationBuilder(BackendApplication.class, TestcontainersConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties("oracul.evidence.max-items=20", "oracul.evidence.core=10", "oracul.evidence.supporting=10",
                    "oracul.evidence.counter-signals=5")
                .run();
            c.close();
        }).isNotNull();
    }
}
