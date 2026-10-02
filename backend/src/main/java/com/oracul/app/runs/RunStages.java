package com.oracul.app.runs;

import com.oracul.app.api.model.RunStage;

/** Fixed stage table of generation-runs.md: one-based index and user-facing label. */
public final class RunStages {

    public static final int COUNT = 10;

    private static final String[] LABELS = {
        "Understanding your future…", "Building research strategy…", "Searching current events…",
        "Reading relevant sources…", "Connecting signals…", "Ranking evidence…",
        "Exploring possible futures…", "Challenging assumptions…", "Constructing scenario…",
        "Writing from the future…",
    };

    private RunStages() {
    }

    public static int index(RunStage stage) {
        return stage.ordinal() + 1;
    }

    public static String label(RunStage stage) {
        return LABELS[stage.ordinal()];
    }
}
