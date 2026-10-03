package com.oracul.app.reasoning;

import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.HorizonCode;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;

/** Cutoff date and future event window of an Evidence Pack. */
record ScenarioWindow(LocalDate cutoff, LocalDate end) {

    static ScenarioWindow of(EvidencePack pack) {
        LocalDate cutoff = pack.getCutoff().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
        return new ScenarioWindow(cutoff, cutoff.plus(period(pack.getConfiguration().getHorizon())));
    }

    static Period period(HorizonCode h) {
        return switch (h) {
            case _1D -> Period.ofDays(1);
            case _1W -> Period.ofDays(7);
            case _1M -> Period.ofMonths(1);
            case _1Y -> Period.ofYears(1);
            case _5Y -> Period.ofYears(5);
            case _10Y -> Period.ofYears(10);
            case _20Y -> Period.ofYears(20);
        };
    }

    static String label(HorizonCode h) {
        return switch (h) {
            case _1D -> "Tomorrow";
            case _1W -> "1 week";
            case _1M -> "1 month";
            case _1Y -> "1 year";
            case _5Y -> "5 years";
            case _10Y -> "10 years";
            case _20Y -> "20 years";
        };
    }
}
