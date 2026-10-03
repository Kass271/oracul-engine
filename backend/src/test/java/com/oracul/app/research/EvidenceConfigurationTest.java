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

    // @trace FR-31
    @ParameterizedTest(name = "{0}={1} fails startup naming the property")
    @CsvSource({
        "oracul.evidence.min-core.high,-1",
        "oracul.evidence.min-core.high,11",
        "oracul.evidence.min-core.medium,-1",
        "oracul.evidence.min-core.medium,11",
        "oracul.evidence.min-core.low,-1",
        "oracul.evidence.min-core.low,11",
    })
    void anInvalidMinCoreThresholdFailsStartup(String property, String value) {
        assertThat(failureOf(property + "=" + value)).contains(property);
    }

    private static String[] thresholdProps(int core, String band, int value) {
        java.util.List<String> props = new java.util.ArrayList<>();
        props.add("oracul.evidence.max-items=100");
        props.add("oracul.evidence.core=" + core);
        for (String other : new String[] {"high", "medium", "low"}) {
            props.add("oracul.evidence.min-core." + other + "=" + (other.equals(band) ? value : 0));
        }
        return props.toArray(String[]::new);
    }

    private static void assertStarts(String... properties) {
        try (ConfigurableApplicationContext c = new SpringApplicationBuilder(BackendApplication.class, TestcontainersConfiguration.class)
            .web(WebApplicationType.NONE).properties(properties).run()) {
            assertThat(c).isNotNull();
        }
    }

    // @trace FR-31
    @ParameterizedTest(name = "core={0} min-core.{1} range is 0..core")
    @org.junit.jupiter.params.provider.MethodSource("coreAndBand")
    void minCoreThresholdRangeIsZeroToCore(int core, String band) {
        String property = "oracul.evidence.min-core." + band;
        assertStarts(thresholdProps(core, band, core));
        assertStarts(thresholdProps(core, band, 0));
        for (int bad : new int[] {core + 1, -1}) {
            String failure = failureOf(thresholdProps(core, band, bad));
            assertThat(failure).as("value %d", bad).contains(property);
            for (String other : new String[] {"high", "medium", "low"}) {
                if (!other.equals(band)) assertThat(failure).doesNotContain("oracul.evidence.min-core." + other);
            }
        }
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> coreAndBand() {
        java.util.List<org.junit.jupiter.params.provider.Arguments> args = new java.util.ArrayList<>();
        for (int core : new int[] {4, 10, 20}) {
            for (String band : new String[] {"high", "medium", "low"}) {
                args.add(org.junit.jupiter.params.provider.Arguments.of(core, band));
            }
        }
        return args.stream();
    }

    // @trace FR-31
    @Test
    void aThresholdAboveTenIsAcceptedWhenCoreIsTwenty() {
        assertStarts("oracul.evidence.max-items=100", "oracul.evidence.core=20", "oracul.evidence.min-core.high=15");
    }

    // @trace FR-31
    @Test
    void aThresholdAboveTheCoreSectionSizeFailsStartup() {
        assertThat(failureOf("oracul.evidence.core=4", "oracul.evidence.min-core.high=5")).contains("oracul.evidence.min-core");
    }

    // @trace FR-31
    @Test
    void withoutOverridesTheThresholdsAreFiveThreeOne() throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.oracul.app.research.MinCoreThresholds");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("com.oracul.app.research.MinCoreThresholds is missing");
        }
        try (ConfigurableApplicationContext c = new SpringApplicationBuilder(BackendApplication.class, TestcontainersConfiguration.class)
            .web(WebApplicationType.NONE).run()) {
            Object bean = c.getBean(type);
            assertThat(bean).isEqualTo(type.getMethod("defaults").invoke(null));
            assertThat(bean).isEqualTo(type.getConstructor(int.class, int.class, int.class).newInstance(5, 3, 1));
        }
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
