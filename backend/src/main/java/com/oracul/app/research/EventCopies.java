package com.oracul.app.research;

import com.oracul.app.api.model.NormalizedEvent;
import java.util.ArrayList;
import java.util.Comparator;

/** Shared helpers of the pure ranking / selection classes. */
final class EventCopies {

    /** Ids like EV001 / S012: shorter first, then lexicographic. */
    static final Comparator<String> ID_ORDER = Comparator.<String>comparingInt(String::length)
        .thenComparing(Comparator.naturalOrder());

    private EventCopies() {
    }

    static NormalizedEvent copy(NormalizedEvent in) {
        NormalizedEvent e = new NormalizedEvent(in.getId(), in.getCategory(), new ArrayList<>(in.getEntities()),
            in.getSummary(), new ArrayList<>(in.getSourceIds()), in.getConfidence());
        e.setDate(in.getDate());
        e.setDisagreement(in.getDisagreement());
        e.setClassification(in.getClassification());
        e.setRanking(in.getRanking());
        e.setSelection(in.getSelection());
        e.setExcludedReason(in.getExcludedReason());
        return e;
    }
}
