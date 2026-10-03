package com.oracul.app.research;

/** Minimum CORE evidence items per realism band: high 9-10, medium 6-8, low 1-5 (FR-31). 0 disables the check. */
public record MinCoreThresholds(int high, int medium, int low) {

    public static MinCoreThresholds defaults() {
        return new MinCoreThresholds(5, 3, 1);
    }

    public int forRealism(int realism) {
        if (realism < 1 || realism > 10) {
            throw new IllegalArgumentException("realism must be between 1 and 10");
        }
        return realism >= 9 ? high : realism >= 6 ? medium : low;
    }
}
