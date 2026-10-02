package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.RunStage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Stages table of generation-runs.md. */
// @trace FR-24
class RunStageTest {

    private static final List<String> ORDER = List.of(
        "UNDERSTANDING", "RESEARCH_STRATEGY", "SEARCHING", "READING_SOURCES", "CONNECTING_SIGNALS",
        "RANKING", "EXPLORING_FUTURES", "CHALLENGING_ASSUMPTIONS", "CONSTRUCTING_SCENARIO", "WRITING_STORY");
    private static final List<String> LABELS = List.of(
        "Understanding your future…", "Building research strategy…", "Searching current events…",
        "Reading relevant sources…", "Connecting signals…", "Ranking evidence…", "Exploring possible futures…",
        "Challenging assumptions…", "Constructing scenario…", "Writing from the future…");

    private static Class<?> stages() {
        try {
            return Class.forName("com.oracul.app.runs.RunStages");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("com.oracul.app.runs.RunStages is missing");
        }
    }

    private static Object call(String method, RunStage stage) throws Exception {
        return stages().getMethod(method, RunStage.class).invoke(null, stage);
    }

    @Test
    void indexIsOneBasedInTableOrderForAllTenStages() throws Exception {
        assertThat(RunStage.values()).hasSize(10);
        for (int i = 0; i < ORDER.size(); i++) {
            assertThat(call("index", RunStage.valueOf(ORDER.get(i)))).as(ORDER.get(i)).isEqualTo(i + 1);
        }
    }

    @Test
    void labelsAreExactWithEllipsisCharacter() throws Exception {
        for (int i = 0; i < ORDER.size(); i++) {
            assertThat(call("label", RunStage.valueOf(ORDER.get(i)))).as(ORDER.get(i)).isEqualTo(LABELS.get(i));
        }
    }
}
