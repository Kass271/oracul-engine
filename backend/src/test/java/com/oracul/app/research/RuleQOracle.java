package com.oracul.app.research;

/**
 * The test-side statement of rule Q (wildcard-search.md FR-51 "Rules"), independent of production code: after trimming, 3-12
 * whitespace-separated words, at most 120 characters, none of {@code " “ ” ( )}, no {@code :}, and no standalone token
 * {@code OR} in any letter case.
 */
final class RuleQOracle {

    private RuleQOracle() {
    }

    static boolean holds(String raw) {
        if (raw == null) return false;
        String text = raw.trim();
        if (text.isEmpty() || text.length() > 120) return false;
        for (char c : new char[] {'"', '“', '”', '(', ')', ':'}) {
            if (text.indexOf(c) >= 0) return false;
        }
        String[] tokens = text.split("\\s+");
        if (tokens.length < 3 || tokens.length > 12) return false;
        for (String t : tokens) {
            if (t.equalsIgnoreCase("or")) return false;
        }
        return true;
    }

    /** The first rule Q violation of {@code text} as words, or null when it holds (for assertion messages). */
    static String why(String text) {
        return holds(text) ? null : "'" + text + "' breaks rule Q";
    }
}
