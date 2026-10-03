package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oracul.app.BackendApplication;
import com.oracul.app.TestcontainersConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** research-pipeline.md "Slice 06_events" configuration: batch sizes 1...100, concurrencies 1...16, max-sources 1...1000, otherwise startup fails. */
// @trace FR-14, FR-15
class EventConfigurationTest {

    @ParameterizedTest(name = "{0}={1} fails startup")
    @CsvSource({
        "oracul.events.normalization-batch-size,0",
        "oracul.events.normalization-batch-size,101",
        "oracul.events.normalization-batch-size,-5",
        "oracul.events.classification-batch-size,0",
        "oracul.events.classification-batch-size,101",
        "oracul.events.classification-batch-size,-5",
        "oracul.events.normalization-concurrency,0",
        "oracul.events.normalization-concurrency,17",
        "oracul.events.normalization-concurrency,-1",
        "oracul.events.classification-concurrency,0",
        "oracul.events.classification-concurrency,17",
        "oracul.events.classification-concurrency,-1",
        "oracul.events.max-sources,0",
        "oracul.events.max-sources,1001",
        "oracul.events.max-sources,-1",
    })
    void outOfRangeBatchSizeFailsStartup(String property, String value) {
        assertThatThrownBy(() -> {
            ConfigurableApplicationContext c = new SpringApplicationBuilder(BackendApplication.class, TestcontainersConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(property + "=" + value)
                .run();
            c.close();
        }).satisfies(e -> {
            Throwable t = e;
            StringBuilder all = new StringBuilder();
            while (t != null) {
                all.append(t.getMessage()).append('\n');
                t = t.getCause();
            }
            assertThat(all.toString()).contains(property);
        });
    }
}
