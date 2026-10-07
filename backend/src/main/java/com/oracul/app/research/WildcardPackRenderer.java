package com.oracul.app.research;

import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.PackSourceItem;
import com.oracul.app.api.model.PackWildcardSection;
import com.oracul.app.api.model.Source;
import com.oracul.app.api.model.SourceExcerpt;
import com.oracul.app.api.model.WildcardPipeline;
import com.oracul.app.api.model.WildcardPipelineKind;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Sections and prompt text of the Evidence Pack grouped by wildcard (FR-57). Pure. */
public final class WildcardPackRenderer {

    private static final DateTimeFormatter CUTOFF = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int SNIPPET_MAX = 600;

    private WildcardPackRenderer() {
    }

    public static List<PackWildcardSection> sections(List<WildcardPipeline> pipelines, List<Source> sources) {
        Map<String, Source> byId = new HashMap<>();
        for (Source s : sources) {
            byId.put(s.getId(), s);
        }
        List<PackWildcardSection> out = new ArrayList<>();
        for (WildcardPipeline p : pipelines) {
            List<PackSourceItem> items = new ArrayList<>();
            if (p.getSourceIds() != null) {
                for (String id : p.getSourceIds()) {
                    Source s = byId.get(id);
                    if (s != null) {
                        items.add(item(s, p.getId()));
                    }
                }
            }
            PackWildcardSection section = new PackWildcardSection(p.getId(), p.getKind(), p.getLabel(), p.getHeading(),
                items);
            section.setLevel(p.getLevel());
            out.add(section);
        }
        return out;
    }

    private static PackSourceItem item(Source s, String pipelineId) {
        List<String> fragments = new ArrayList<>();
        if (s.getExcerpts() != null) {
            for (SourceExcerpt e : s.getExcerpts()) {
                if (pipelineId.equals(e.getPipelineId())) {
                    fragments.addAll(e.getFragments());
                    break;
                }
            }
        }
        boolean retrieved = !fragments.isEmpty();
        PackSourceItem i = new PackSourceItem("E" + s.getId().replaceAll("\\D", ""), s.getId(), s.getTitle(),
            s.getPublisher(), s.getUrl(), retrieved, fragments);
        i.setPublishedAt(s.getPublishedAt());
        if (!retrieved) {
            String text = s.getSummary() == null || s.getSummary().isBlank() ? s.getTitle() : s.getSummary();
            i.setSnippet(cut(text));
        }
        return i;
    }

    /** Every Evidence ID of the pack: legacy items and wildcard section items. */
    public static Set<String> evidenceIds(EvidencePack pack) {
        Set<String> ids = new LinkedHashSet<>();
        pack.getCore().forEach(i -> ids.add(i.getEvidenceId()));
        pack.getSupporting().forEach(i -> ids.add(i.getEvidenceId()));
        pack.getCounterSignals().forEach(i -> ids.add(i.getEvidenceId()));
        if (pack.getWildcardSections() != null) {
            for (PackWildcardSection s : pack.getWildcardSections()) {
                s.getItems().forEach(i -> ids.add(i.getEvidenceId()));
            }
        }
        return ids;
    }

    public static String render(EvidencePack pack) {
        var cfg = pack.getConfiguration();
        List<PackWildcardSection> sections = pack.getWildcardSections() == null ? List.of() : pack.getWildcardSections();
        List<String> lines = new ArrayList<>();
        lines.add("ORACUL EVIDENCE PACK");
        lines.add("Generation: " + pack.getGenerationId());
        lines.add("Cutoff: " + pack.getCutoff().withOffsetSameInstant(ZoneOffset.UTC).format(CUTOFF));
        lines.add("SCENARIO");
        lines.add("Realism: " + cfg.getRealism() + " | Darkness: " + cfg.getDarkness() + " | Optimism: "
            + cfg.getOptimism() + " | Horizon: " + horizon(cfg.getHorizon()));
        lines.add("WILDCARDS");
        List<String> topics = new ArrayList<>();
        for (PackWildcardSection s : sections) {
            if (s.getKind() != WildcardPipelineKind.GENERAL) {
                topics.add(PromptText.sanitize(s.getLabel()) + ": " + s.getLevel());
            }
        }
        lines.add(topics.isEmpty() ? "none" : String.join(" | ", topics));
        for (PackWildcardSection s : sections) {
            lines.add(s.getKind() == WildcardPipelineKind.GENERAL ? "General: major current world events"
                : "Wildcard: " + PromptText.sanitize(s.getLabel()) + " " + s.getLevel() + "/10");
            if (s.getItems().isEmpty()) {
                lines.add("no current sources found");
                continue;
            }
            for (PackSourceItem i : s.getItems()) {
                String publisher = PromptText.sanitize(i.getPublisher());
                lines.add("[" + i.getEvidenceId() + "] " + PromptText.sanitize(i.getTitle()) + " · "
                    + (publisher.isEmpty() ? "unknown" : publisher) + " · "
                    + (i.getPublishedAt() == null ? "unknown"
                        : i.getPublishedAt().withOffsetSameInstant(ZoneOffset.UTC).format(DAY))
                    + " · " + PromptText.sanitize(String.valueOf(i.getUrl())));
                if (i.getFragments() != null && !i.getFragments().isEmpty()) {
                    for (String f : i.getFragments()) {
                        lines.add("Excerpt: " + PromptText.sanitize(f));
                    }
                } else {
                    lines.add("Content not retrieved. Snippet: " + cut(PromptText.sanitize(i.getSnippet())));
                }
            }
        }
        return String.join("\n", lines);
    }

    private static String cut(String s) {
        if (s == null || s.length() <= SNIPPET_MAX) {
            return s == null ? "" : s;
        }
        int end = SNIPPET_MAX;
        if (Character.isHighSurrogate(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
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
