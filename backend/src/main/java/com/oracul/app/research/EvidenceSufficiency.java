package com.oracul.app.research;

import java.util.OptionalInt;

/** The sufficiency check at the end of stage 6 (FR-31). */
public final class EvidenceSufficiency {

    private EvidenceSufficiency() {
    }

    public static boolean sufficient(int coreItems, int realism, MinCoreThresholds thresholds) {
        return coreItems >= thresholds.forRealism(realism);
    }

    public static OptionalInt suggestedRealism(int realism) {
        return realism > 1 ? OptionalInt.of(Math.max(1, realism - 2)) : OptionalInt.empty();
    }
}
