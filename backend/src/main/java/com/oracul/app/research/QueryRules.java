package com.oracul.app.research;

/** Rule Q of FR-51: the shape of every query text, model-written or template. */
public final class QueryRules {

    private static final int MIN_WORDS = 3;
    private static final int MAX_WORDS = 12;
    private static final int MAX_LENGTH = 120;

    private QueryRules() {
    }

    /** Trim and collapse inner whitespace to one space; null stays null. */
    public static String clean(String raw) {
        return raw == null ? null : raw.trim().replaceAll("\\s+", " ");
    }

    /** Rule Q on the cleaned text; null or blank is false. */
    public static boolean valid(String text) {
        String t = clean(text);
        if (t == null || t.isEmpty() || t.length() > MAX_LENGTH) {
            return false;
        }
        if (t.indexOf('"') >= 0 || t.indexOf('“') >= 0 || t.indexOf('”') >= 0 || t.indexOf('(') >= 0
            || t.indexOf(')') >= 0 || t.indexOf(':') >= 0) {
            return false;
        }
        String[] words = t.split(" ");
        if (words.length < MIN_WORDS || words.length > MAX_WORDS) {
            return false;
        }
        for (String w : words) {
            if (w.equalsIgnoreCase("or")) {
                return false;
            }
        }
        return true;
    }
}
