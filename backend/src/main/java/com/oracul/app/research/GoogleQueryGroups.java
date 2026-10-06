package com.oracul.app.research;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure rules of the Google News OR-group search (FR-48): grouping, elements, query string, maxrecords, attribution. */
public final class GoogleQueryGroups {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Set<String> OPERATORS = Set.of("OR", "AND", "NOT");

    private GoogleQueryGroups() {
    }

    /** Sizes of the contiguous groups of {@code n} elements: G = min(max, n), sizes differ by at most 1, larger first. */
    public static List<Integer> groupSizes(int n, int maxRequests) {
        List<Integer> sizes = new ArrayList<>();
        if (n <= 0) {
            return sizes;
        }
        int g = Math.min(Math.max(1, maxRequests), n);
        for (int i = 0; i < g; i++) {
            sizes.add(n / g + (i < n % g ? 1 : 0));
        }
        return sizes;
    }

    /**
     * The element of a query text: quotes and parentheses replaced by spaces, whitespace collapsed, standalone upper-case
     * OR / AND / NOT removed; one token stays bare, several become a quoted phrase. Null when nothing is left.
     */
    public static String element(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replace('"', ' ').replace('(', ' ').replace(')', ' ');
        List<String> tokens = new ArrayList<>();
        for (String t : WHITESPACE.split(cleaned.trim())) {
            if (!t.isEmpty() && !OPERATORS.contains(t)) {
                tokens.add(t);
            }
        }
        if (tokens.isEmpty()) {
            return null;
        }
        String joined = String.join(" ", tokens);
        return tokens.size() == 1 ? joined : "\"" + joined + "\"";
    }

    /** The element without its quotes, as matched against titles. */
    public static String unquoted(String element) {
        return element.length() >= 2 && element.startsWith("\"") && element.endsWith("\"")
            ? element.substring(1, element.length() - 1) : element;
    }

    /** Fixed number of OR-group requests of one search. */
    public static final int GROUPS = 4;

    /** Days covered by the {@code when:<N>d} window of a horizon (also the age filter). */
    public static int timespanDays(com.oracul.app.api.model.HorizonCode horizon) {
        return switch (horizon) {
            case _1D, _1W -> 7;
            case _1M -> 14;
            default -> 90;
        };
    }

    /** min(250, perQuery x group size). */
    public static int maxRecords(int perQuery, int groupSize) {
        return (int) Math.min(250L, (long) perQuery * groupSize);
    }

    /**
     * The index of the element a title is attributed to: the first element whose token sequence occurs contiguously in the
     * title's tokens; else the element sharing the most distinct tokens (ties: earlier); else the first element.
     */
    public static int attribute(List<String> elements, String title) {
        List<String> titleTokens = tokens(title);
        List<List<String>> elementTokens = new ArrayList<>();
        for (String e : elements) {
            elementTokens.add(tokens(unquoted(e)));
        }
        for (int i = 0; i < elementTokens.size(); i++) {
            List<String> et = elementTokens.get(i);
            if (!et.isEmpty() && contains(titleTokens, et)) {
                return i;
            }
        }
        Set<String> titleSet = new HashSet<>(titleTokens);
        int best = 0;
        int bestShared = 0;
        for (int i = 0; i < elementTokens.size(); i++) {
            Set<String> shared = new HashSet<>(elementTokens.get(i));
            shared.retainAll(titleSet);
            if (shared.size() > bestShared) {
                bestShared = shared.size();
                best = i;
            }
        }
        return best;
    }

    private static boolean contains(List<String> haystack, List<String> needle) {
        for (int i = 0; i + needle.size() <= haystack.size(); i++) {
            if (haystack.subList(i, i + needle.size()).equals(needle)) {
                return true;
            }
        }
        return false;
    }

    static List<String> tokens(String text) {
        if (text == null) {
            return List.of();
        }
        String normalised = NON_ALNUM.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
        if (normalised.isEmpty()) {
            return List.of();
        }
        return List.of(normalised.split(" "));
    }
}
