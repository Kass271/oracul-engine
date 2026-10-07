package com.oracul.app.result;

import com.oracul.app.style.Language;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Dateline of a future story (FR-23, FR-66). Pure. */
public final class Datelines {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    private static final String[] UK_MONTHS = {"січня", "лютого", "березня", "квітня", "травня", "червня", "липня",
        "серпня", "вересня", "жовтня", "листопада", "грудня"};

    private Datelines() {
    }

    public static String format(LocalDate d) {
        return format(d, Language.EN);
    }

    public static String format(LocalDate d, Language language) {
        if (language == Language.UK) {
            return "ORACUL FUTURE — " + d.getDayOfMonth() + " " + UK_MONTHS[d.getMonthValue() - 1] + " " + d.getYear();
        }
        return "ORACUL FUTURE — " + FORMAT.format(d);
    }
}
