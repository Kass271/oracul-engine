package com.oracul.app.research;

import com.oracul.app.api.model.QueryBucket;
import com.oracul.app.api.model.SearchIntent;
import com.oracul.app.api.model.WildcardCategory;
import com.oracul.app.api.model.WildcardDefinition;
import com.oracul.app.scenario.ScenarioCatalogueData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Static query templates per intent so a search plan is always complete without ChatGPT (FR-12). */
@Component
public class QueryTemplates {

    private static final String CUSTOM_PREFIX = "Current developments related to ";
    private static final int MAX_SUBJECT = 70;
    private static final int MAX_WORDS = 4;
    private static final int MAX_CUSTOM = 40;
    private static final int MAX_KEYWORDS = 8;
    private static final int MAX_QUERY = 120;
    private static final int LONG_LABEL_WORDS = 6;
    private static final java.util.Set<String> STOP =
        java.util.Set.of("a", "an", "the", "of", "in", "on", "to", "for", "at", "by", "with");
    private static final String EXTRA_KEYWORD = "research";

    private static final List<String> SUFFIXES = List.of(
        "latest developments", "breaking news", "new report", "analysis", "announcement", "researchers warn",
        "experts say", "government response", "industry impact", "investment", "new study", "trend", "outlook",
        "forecast", "risks", "opportunities", "global impact", "policy changes", "public reaction", "key findings",
        "emerging signals", "update", "debate", "regulation", "early warning");

    private static final List<String> QUALIFIERS = List.of(
        "latest", "breaking", "report", "analysis", "forecast", "outlook", "risks", "trends", "policy", "markets",
        "research", "impact", "warning", "signals", "response", "investment", "regulation", "strategy", "debate",
        "innovation", "crisis", "growth", "demand", "supply", "security", "technology", "economy", "society");

    private final Map<String, WildcardDefinition> wildcards = new HashMap<>();
    private final Map<String, WildcardCategory> categories = new HashMap<>();

    public QueryTemplates() {
        for (WildcardCategory c : ScenarioCatalogueData.catalogue().getCategories()) {
            categories.put(c.getId(), c);
            for (WildcardDefinition d : c.getWildcards()) {
                wildcards.put(d.getId(), d);
            }
        }
    }

    public List<String> forIntent(SearchIntent intent) {
        String subject = subject(intent);
        List<String> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        List<String> longWords = longCustomWords(intent, subject);
        if (longWords != null) {
            String[] full = subject.split(" ");
            add(out, seen, full.length <= MAX_KEYWORDS ? subject
                : String.join(" ", longWords.subList(0, Math.min(MAX_KEYWORDS, longWords.size()))));
            subject = String.join(" ", longWords.subList(0, Math.min(MAX_KEYWORDS - 2, longWords.size())));
        }
        add(out, seen, subject.split(" ").length < 2 ? subject + " " + EXTRA_KEYWORD : subject);
        for (String s : SUFFIXES) {
            add(out, seen, subject + " " + s);
        }
        for (String a : QUALIFIERS) {
            add(out, seen, subject + " " + a);
        }
        for (String a : QUALIFIERS) {
            for (String b : QUALIFIERS) {
                if (!a.equals(b)) {
                    add(out, seen, subject + " " + a + " " + b);
                }
            }
        }
        return out;
    }

    /** Content words of a custom label longer than 6 words, otherwise null (short labels are kept in full). */
    private List<String> longCustomWords(SearchIntent intent, String subject) {
        if (intent.getBucket() != QueryBucket.WILDCARD
            || (intent.getTopicKey() != null && wildcards.containsKey(intent.getTopicKey()))
            || subject.split(" ").length <= LONG_LABEL_WORDS) {
            return null;
        }
        List<String> words = new ArrayList<>();
        for (String w : subject.split(" ")) {
            if (!STOP.contains(w)) {
                words.add(w);
            }
        }
        return words.size() < 2 ? null : words;
    }

    private static void add(List<String> out, List<String> seen, String q) {
        int words = q.split(" ").length;
        String key = q.toLowerCase(Locale.ROOT);
        if (words >= 2 && words <= MAX_KEYWORDS && q.length() <= MAX_QUERY && !seen.contains(key)) {
            seen.add(key);
            out.add(q);
        }
    }

    private String subject(SearchIntent intent) {
        if (intent.getBucket() == QueryBucket.WILDCARD) {
            WildcardDefinition def = intent.getTopicKey() == null ? null : wildcards.get(intent.getTopicKey());
            if (def != null) {
                return cut(sanitise(def.getLabel()));
            }
            return sanitiseCustom(customLabel(intent.getDescription()));
        } else if (intent.getBucket() == QueryBucket.ADJACENT) {
            WildcardCategory c = intent.getCategory() == null ? null : categories.get(intent.getCategory());
            if (c == null) {
                return "science technology economy";
            }
            if ("ai".equals(c.getId())) {
                return "artificial intelligence";
            }
            return cut(sanitise(c.getLabel()));
        } else if (intent.getBucket() == QueryBucket.MAJOR) {
            return "major world events";
        }
        return "unusual early signals";
    }

    /** The label is carried explicitly: only the known planner suffixes are removed, never a cut at any dash. */
    private static String customLabel(String description) {
        String d = description == null ? "" : description;
        if (d.startsWith(CUSTOM_PREFIX)) {
            d = d.substring(CUSTOM_PREFIX.length());
        }
        for (String suffix : List.of(SearchPlanner.BOTH, SearchPlanner.DARK, SearchPlanner.OPT)) {
            if (d.endsWith(suffix)) {
                d = d.substring(0, d.length() - suffix.length());
                break;
            }
        }
        return d.trim();
    }

    private static String sanitiseCustom(String label) {
        String t = label.trim();
        if (t.length() > MAX_CUSTOM) {
            t = t.substring(0, MAX_CUSTOM).trim();
        }
        t = sanitise(t);
        return t.isEmpty() ? "emerging topic" : t;
    }

    private static String sanitise(String raw) {
        String t = raw.toLowerCase(Locale.ROOT).replaceAll("[\"\u201c\u201d\u201e()/]", " ");
        List<String> kept = new ArrayList<>();
        for (String tok : t.trim().split("\\s+")) {
            String k = tok.replaceFirst("^-+", "");
            if (k.chars().noneMatch(Character::isLetterOrDigit)
                || k.equals("or") || k.equals("and") || k.equals("not")) {
                continue;
            }
            kept.add(k);
        }
        return String.join(" ", kept);
    }

    private static String cut(String s) {
        String t = s.length() <= MAX_SUBJECT ? s : s.substring(0, MAX_SUBJECT).trim();
        String[] words = t.trim().split("\\s+");
        if (words.length > MAX_WORDS) {
            t = String.join(" ", java.util.Arrays.copyOf(words, MAX_WORDS));
        }
        return t;
    }
}
