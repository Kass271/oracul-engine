package com.oracul.app.result;

import com.oracul.app.api.model.HorizonCode;

/** Display label of a horizon. Pure. */
public final class HorizonLabels {

    private HorizonLabels() {
    }

    public static String label(HorizonCode h) {
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
