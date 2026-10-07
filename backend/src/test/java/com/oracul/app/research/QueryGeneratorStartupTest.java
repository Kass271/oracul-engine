package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * NFR-10 part 2 / wildcard-search.md FR-51 range (g): the startup validation of {@code QueryGenerator}'s configuration - the
 * query-generation concurrency (1 ... 8), and 0 < query-generation-window <= search-window <= stage-budget; a violation fails
 * the context start naming the property.
 */
// @trace FR-51, NFR-10
class QueryGeneratorStartupTest {

    private static Throwable startup(Map<String, String> props) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        boolean[] ran = {false};
        QuerySupport.generatorFromProperties(props, f -> {
            ran[0] = true;
            failure.set(f);
        });
        assertThat(ran[0]).isTrue();
        return failure.get();
    }

    private static String chain(Throwable e) {
        return ParallelSearchSupport.chain(e);
    }

    @Test
    void theDefaultsStart() {
        assertThat(startup(Map.of())).isNull();
    }

    // ---- query-generation-concurrency: 1 ... 8 ----------------------------------------------------------------------

    @ParameterizedTest(name = "oracul.search.query-generation-concurrency={0} fails startup naming it")
    @ValueSource(strings = {"0", "9", "-1", "100"})
    void aConcurrencyOutsideOneToEightFailsStartup(String value) {
        Throwable failure = startup(Map.of("oracul.search.query-generation-concurrency", value));
        assertThat(failure).as("the context must not start").isNotNull();
        assertThat(chain(failure)).contains("oracul.search.query-generation-concurrency");
    }

    @ParameterizedTest(name = "oracul.search.query-generation-concurrency={0} starts")
    @ValueSource(strings = {"1", "2", "4", "7", "8"})
    void aConcurrencyOfOneToEightStarts(String value) {
        assertThat(startup(Map.of("oracul.search.query-generation-concurrency", value))).isNull();
    }

    // ---- query-generation-window: 0 < window <= search-window --------------------------------------------------------

    @ParameterizedTest(name = "oracul.search.query-generation-window={0} fails startup naming it")
    @ValueSource(strings = {"PT0S", "-PT1S", "PT61S"})
    void aWindowOfZeroOrLessOrAboveTheSearchWindowFailsStartup(String value) {
        Throwable failure = startup(Map.of("oracul.search.query-generation-window", value));
        assertThat(failure).as("the context must not start").isNotNull();
        assertThat(chain(failure)).contains("oracul.search.query-generation-window");
    }

    @ParameterizedTest(name = "oracul.search.query-generation-window={0} starts")
    @ValueSource(strings = {"PT0.001S", "PT1S", "PT30S", "PT60S"})
    void aPositiveWindowUpToTheSearchWindowStarts(String value) {
        assertThat(startup(Map.of("oracul.search.query-generation-window", value))).isNull();
    }

    @Test
    void theWindowIsComparedWithTheConfiguredSearchWindow() {
        assertThat(startup(Map.of("oracul.search.search-window", "PT90S", "oracul.search.stage-budget", "PT90S",
            "oracul.search.query-generation-window", "PT61S"))).isNull();
        Throwable failure = startup(Map.of("oracul.search.search-window", "PT20S", "oracul.search.query-generation-window", "PT30S"));
        assertThat(failure).isNotNull();
        assertThat(chain(failure)).contains("oracul.search.query-generation-window");
    }

    // ---- stage-budget: search-window <= stage-budget ------------------------------------------------------------------

    @ParameterizedTest(name = "oracul.search.stage-budget={0} fails startup naming it")
    @ValueSource(strings = {"PT59S", "PT0S", "-PT1S"})
    void aStageBudgetBelowTheSearchWindowFailsStartup(String value) {
        Throwable failure = startup(Map.of("oracul.search.stage-budget", value));
        assertThat(failure).as("the context must not start").isNotNull();
        assertThat(chain(failure)).contains("oracul.search.stage-budget");
    }

    @ParameterizedTest(name = "oracul.search.stage-budget={0} starts")
    @ValueSource(strings = {"PT60S", "PT90S", "PT300S"})
    void aStageBudgetOfAtLeastTheSearchWindowStarts(String value) {
        assertThat(startup(Map.of("oracul.search.stage-budget", value))).isNull();
    }
}
