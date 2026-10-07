package com.oracul.app.research;

import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.WildcardPipeline;
import com.oracul.app.api.model.WildcardPipelineKind;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Template queries per wildcard pipeline, so a search plan is always complete without ChatGPT (FR-51 step 6). */
@Component
public class QueryTemplates {

    private static final int MAX_LABEL_TOKENS = 6;
    private static final String EMPTY_LABEL = "future developments";

    private static final List<List<String>> BAND_TEMPLATES = List.of(
        List.of(" latest research", " new developments", " early studies"),
        List.of(" serious disruption", " major escalation", " growing concerns"),
        List.of(" extreme scenario", " unprecedented scale", " radical upheaval"));

    private static final List<List<String>> DARK_WORDS = List.of(
        List.of("risk", "failure", "warning"),
        List.of("crisis", "conflict", "threat"),
        List.of("disaster", "catastrophe", "collapse"));

    private static final List<String> BRIGHT_WORDS = List.of("progress", "breakthrough", "innovation");

    public QueryTemplates() {
    }

    /** Exactly three template texts [t1, t2, t3]. */
    public List<String> forPipeline(WildcardPipeline p, ScenarioConfiguration cfg) {
        boolean general = p.getKind() == WildcardPipelineKind.GENERAL || p.getLevel() == null;
        int band = 2;
        List<String> base;
        String label;
        if (general) {
            label = "major world events";
            base = List.of(label + " today", "global economy politics developments",
                "international science technology developments");
        } else {
            label = label(p.getLabel());
            int level = p.getLevel();
            band = level <= 3 ? 0 : level <= 7 ? 1 : 2;
            base = new ArrayList<>();
            for (String t : BAND_TEMPLATES.get(band)) {
                base.add(label + t);
            }
        }
        List<String> words = null;
        if (cfg.getDarkness() >= 7) {
            words = DARK_WORDS.get(band);
        } else if (cfg.getDarkness() <= 3 || cfg.getOptimism() >= 7) {
            words = BRIGHT_WORDS;
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < base.size(); i++) {
            out.add(words == null ? base.get(i) : base.get(i) + " " + words.get(i));
        }
        return out;
    }

    /** The label L used in queries (FR-51 step 6). */
    public static String label(String raw) {
        if (raw == null) {
            return EMPTY_LABEL;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append(Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '\'' ? c : ' ');
        }
        List<String> kept = new ArrayList<>();
        for (String tok : sb.toString().trim().split("\\s+")) {
            if (tok.isEmpty() || tok.equalsIgnoreCase("or") || tok.equals("AND") || tok.equals("NOT")
                || tok.chars().allMatch(ch -> ch == '-' || ch == '\'')) {
                continue;
            }
            kept.add(tok);
            if (kept.size() == MAX_LABEL_TOKENS) {
                break;
            }
        }
        return kept.isEmpty() ? EMPTY_LABEL : String.join(" ", kept);
    }
}
