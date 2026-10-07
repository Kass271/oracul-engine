package com.oracul.app.research;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** FR-55: the paragraphs of a publisher page that matter for one wildcard. Pure: no I/O, never calls ChatGPT. */
final class FragmentExtractor {

    static final int MAX_FRAGMENTS = 3;
    static final int MAX_CHARS = 1200;
    static final int MIN_FALLBACK = 80;
    static final String ELLIPSIS = "…";

    private static final String STRIPPED = "script, style, noscript, template, svg, iframe, nav, header, footer, aside, form, "
        + "[role=navigation]";

    private FragmentExtractor() {
    }

    static List<String> paragraphs(String body, String contentType) {
        List<String> out = new ArrayList<>();
        if (body == null) {
            return out;
        }
        if (contentType != null && contentType.trim().toLowerCase(Locale.ROOT).startsWith("text/plain")) {
            StringBuilder block = new StringBuilder();
            for (String line : body.split("\\r?\\n|\\r", -1)) {
                if (line.isBlank()) {
                    flush(block, out);
                } else {
                    block.append(line).append(' ');
                }
            }
            flush(block, out);
            return out;
        }
        Document doc = Jsoup.parse(body);
        doc.select(STRIPPED).remove();
        for (Element p : doc.select("p")) {
            String text = collapse(p.text());
            if (!text.isEmpty()) {
                out.add(text);
            }
        }
        return out;
    }

    static int strength(String paragraph, Set<String> labelTerms, Set<String> queryTerms) {
        Set<String> tokens = WildcardSelector.tokens(paragraph);
        int labels = 0;
        for (String t : labelTerms) {
            if (tokens.contains(t)) {
                labels++;
            }
        }
        Set<String> others = new HashSet<>(queryTerms);
        others.removeAll(labelTerms);
        int query = 0;
        for (String t : others) {
            if (tokens.contains(t)) {
                query++;
            }
        }
        return 2 * labels + query;
    }

    static String cut(String text) {
        if (text.length() <= MAX_CHARS) {
            return text;
        }
        int space = text.lastIndexOf(' ', MAX_CHARS - 1);
        String head = space > 0 ? text.substring(0, space) : text.substring(0, MAX_CHARS - 1);
        if (head.length() > MAX_CHARS - 1) {
            head = head.substring(0, MAX_CHARS - 1);
        }
        return head + ELLIPSIS;
    }

    static List<String> extract(String body, String contentType, Set<String> labelTerms, Set<String> queryTerms) {
        List<String> paragraphs = paragraphs(body, contentType);
        List<Integer> order = new ArrayList<>();
        int[] strengths = new int[paragraphs.size()];
        for (int i = 0; i < paragraphs.size(); i++) {
            strengths[i] = strength(paragraphs.get(i), labelTerms, queryTerms);
            if (strengths[i] >= 1) {
                order.add(i);
            }
        }
        List<String> out = new ArrayList<>();
        if (order.isEmpty()) {
            for (String p : paragraphs) {
                if (p.length() >= MIN_FALLBACK) {
                    out.add(cut(p));
                    break;
                }
            }
            return out;
        }
        order.sort((a, b) -> strengths[a] != strengths[b] ? Integer.compare(strengths[b], strengths[a]) : Integer.compare(a, b));
        String first = paragraphs.get(order.get(0));
        if (first.length() > MAX_CHARS) {
            out.add(cut(first));
            return out;
        }
        int total = 0;
        for (int idx : order) {
            if (out.size() >= MAX_FRAGMENTS) {
                break;
            }
            String p = paragraphs.get(idx);
            if (total + p.length() <= MAX_CHARS) {
                out.add(p);
                total += p.length();
            }
        }
        return out;
    }

    private static void flush(StringBuilder block, List<String> out) {
        String p = collapse(block.toString());
        if (!p.isEmpty()) {
            out.add(p);
        }
        block.setLength(0);
    }

    private static String collapse(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\u00a0]+", " ").trim();
    }
}
