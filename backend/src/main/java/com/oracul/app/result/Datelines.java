package com.oracul.app.result;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Dateline of a future story (FR-23). Pure. */
public final class Datelines {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    private Datelines() {
    }

    public static String format(LocalDate d) {
        return "ORACUL FUTURE — " + FORMAT.format(d);
    }
}
