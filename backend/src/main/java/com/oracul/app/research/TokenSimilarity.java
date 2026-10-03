package com.oracul.app.research;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Token Jaccard similarity of two texts (FR-14). */
public final class TokenSimilarity {

    private TokenSimilarity() {
    }

    public static double jaccard(String a, String b) {
        Set<String> x = tokens(a);
        Set<String> y = tokens(b);
        if (x.isEmpty() && y.isEmpty()) {
            return 0.0;
        }
        Set<String> inter = new HashSet<>(x);
        inter.retainAll(y);
        Set<String> union = new HashSet<>(x);
        union.addAll(y);
        return union.isEmpty() ? 0.0 : (double) inter.size() / union.size();
    }

    static Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) {
            return out;
        }
        for (String t : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
