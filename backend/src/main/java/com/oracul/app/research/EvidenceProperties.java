package com.oracul.app.research;

/** Size and diversity configuration of the evidence selection (FR-17). */
public record EvidenceProperties(int maxItems, int core, int supporting, int counterSignals, int maxPerEntity,
                                 int maxPerPublisher, int maxPerGeography, double maxCategoryShare,
                                 double minSourceQuality) {

    public static EvidenceProperties defaults() {
        return new EvidenceProperties(25, 10, 10, 5, 2, 3, 6, 0.4, 0.30);
    }
}
