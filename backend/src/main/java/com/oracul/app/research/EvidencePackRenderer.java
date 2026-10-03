package com.oracul.app.research;

import com.oracul.app.api.model.EvidenceItem;
import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.ResearchTopic;
import com.oracul.app.api.model.Source;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Renders the Evidence Pack prompt text, the only current-world context ChatGPT sees (FR-18). Pure. */
public final class EvidencePackRenderer {

    private static final DateTimeFormatter CUTOFF = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'");
    private static final int SUMMARY_MAX = 600;

    private EvidencePackRenderer() {
    }

    public static String render(EvidencePack pack) {
        Map<String, Source> sources = new HashMap<>();
        if (pack.getSources() != null) {
            for (Source s : pack.getSources()) {
                sources.put(s.getId(), s);
            }
        }
        var cfg = pack.getConfiguration();
        List<String> lines = new ArrayList<>();
        lines.add("ORACUL EVIDENCE PACK");
        lines.add("Generation: " + pack.getGenerationId());
        lines.add("Cutoff: " + pack.getCutoff().withOffsetSameInstant(ZoneOffset.UTC).format(CUTOFF));
        lines.add("SCENARIO");
        lines.add("Realism: " + cfg.getRealism() + " | Darkness: " + cfg.getDarkness() + " | Optimism: "
            + cfg.getOptimism() + " | Horizon: " + horizon(cfg.getHorizon()));
        lines.add("WILDCARDS");
        List<String> topics = new ArrayList<>();
        for (ResearchTopic t : pack.getProfile().getTopics()) {
            topics.add(PromptText.sanitize(t.getLabel()) + ": " + Math.round(t.getWeight() * 10));
        }
        lines.add(topics.isEmpty() ? "none" : String.join(" | ", topics));
        lines.add("CORE EVIDENCE");
        section(lines, pack.getCore(), sources);
        lines.add("SUPPORTING EVIDENCE");
        section(lines, pack.getSupporting(), sources);
        lines.add("COUNTER-SIGNALS");
        section(lines, pack.getCounterSignals(), sources);
        return String.join("\n", lines);
    }

    private static void section(List<String> lines, List<EvidenceItem> items, Map<String, Source> sources) {
        if (items == null || items.isEmpty()) {
            lines.add("none");
            return;
        }
        for (EvidenceItem i : items) {
            String summary = PromptText.sanitize(i.getSummary());
            if (summary.length() > SUMMARY_MAX) {
                int end = SUMMARY_MAX;
                if (Character.isHighSurrogate(summary.charAt(end - 1))) {
                    end--;
                }
                summary = summary.substring(0, end);
            }
            List<String> ids = new ArrayList<>(i.getSourceIds());
            ids.sort(EventCopies.ID_ORDER);
            List<String> refs = new ArrayList<>();
            for (String id : ids) {
                Source s = sources.get(id);
                String publisher = s == null ? "unknown" : PromptText.sanitize(s.getPublisher());
                refs.add(publisher + " (" + id + ")");
            }
            lines.add("[" + i.getEvidenceId() + "] " + (i.getDate() == null ? "unknown" : i.getDate().toString())
                + " · " + PromptText.sanitize(i.getCategory()) + " · " + summary
                + " · sources: " + String.join(", ", refs)
                + " · quality " + BigDecimal.valueOf(i.getSourceQuality()).setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
    }

    private static String horizon(HorizonCode h) {
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
