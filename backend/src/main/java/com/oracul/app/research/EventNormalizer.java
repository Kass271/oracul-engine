package com.oracul.app.research;

import com.oracul.app.api.model.NormalizedEvent;
import com.oracul.app.api.model.Source;
import com.oracul.app.chatgpt.CallAbandonedException;
import com.oracul.app.chatgpt.HttpResponsesClient;
import com.oracul.app.runs.RunGuard;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Event normalisation and deduplication (FR-14): batches, validation, retry, fallback, cross-batch merge. */
@Component
public class EventNormalizer {

    static final String SCHEMA_ERROR = "answer does not match the schema";
    private static final int MAX_SUMMARY = 600;
    private static final int MAX_DISAGREEMENT = 200;
    private static final Comparator<String> ID_ORDER =
        Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder());

    /** One event before ids are assigned. */
    private record Draft(int batch, List<String> sourceIds, LocalDate date, String category, List<String> entities,
                         String summary, String rawSummary, String disagreement, double confidence) {
    }

    private final HttpResponsesClient responses;
    private final JsonMapper json = JsonMapper.builder().build();
    private final RunGuard guard;
    private final int batchSize;
    private final int concurrency;
    private final int maxSources;

    EventNormalizer(HttpResponsesClient responses, RunGuard guard,
                    @Value("${oracul.events.normalization-batch-size:40}") int batchSize,
                    @Value("${oracul.events.normalization-concurrency:4}") int concurrency,
                    @Value("${oracul.events.max-sources:120}") int maxSources) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalStateException("oracul.events.normalization-batch-size must be between 1 and 100");
        }
        if (concurrency < 1 || concurrency > 16) {
            throw new IllegalStateException("oracul.events.normalization-concurrency must be between 1 and 16");
        }
        if (maxSources < 1 || maxSources > 1000) {
            throw new IllegalStateException("oracul.events.max-sources must be between 1 and 1000");
        }
        this.responses = responses;
        this.guard = guard;
        this.batchSize = batchSize;
        this.concurrency = concurrency;
        this.maxSources = maxSources;
    }

    /**
     * Sources in, events with ids EV001... out. At most max-sources sources are used (best quality, then most recent);
     * they are batched in id order and the batches run in parallel. May throw ChatGptCallException (transport failure)
     * or CallAbandonedException (the run guard said stop).
     */
    public List<NormalizedEvent> normalize(UUID sessionId, UUID runId, List<Source> sources) {
        List<Source> ordered = select(sources);
        int batches = (ordered.size() + batchSize - 1) / batchSize;
        Runnable check = () -> {
            if (!guard.check(runId)) {
                throw new CallAbandonedException();
            }
        };
        List<List<Draft>> perBatch = BatchRunner.run(concurrency, batches, check, (b, gate) -> {
            List<Source> batch = ordered.subList(b * batchSize, Math.min(ordered.size(), (b + 1) * batchSize));
            return normalizeBatch(sessionId, b, batches, batch, gate);
        });
        List<Draft> drafts = new ArrayList<>();
        for (List<Draft> d : perBatch) {
            drafts.addAll(d);
        }
        if (batches > 1) {
            drafts = merge(drafts);
        }
        drafts.sort(Comparator.comparing(d -> d.sourceIds().get(0), ID_ORDER));
        List<NormalizedEvent> out = new ArrayList<>();
        for (Draft d : drafts) {
            NormalizedEvent e = new NormalizedEvent(String.format("EV%03d", out.size() + 1), d.category(),
                d.entities(), d.summary(), d.sourceIds(), d.confidence());
            e.setDate(d.date());
            e.setDisagreement(d.disagreement());
            out.add(e);
        }
        return out;
    }

    /** Source cap: quality desc, then publishedAt desc (undated last), then id; the selection is returned in id order. */
    private List<Source> select(List<Source> sources) {
        List<Source> all = new ArrayList<>(sources);
        if (all.size() > maxSources) {
            all.sort(Comparator.comparingDouble((Source s) -> s.getSourceQuality() == null ? 0.0 : s.getSourceQuality())
                .reversed()
                .thenComparing(Comparator.comparing(Source::getPublishedAt,
                    Comparator.nullsLast(Comparator.<OffsetDateTime>reverseOrder())))
                .thenComparing(Source::getId, ID_ORDER));
            all = new ArrayList<>(all.subList(0, maxSources));
        }
        all.sort(Comparator.comparing(Source::getId, ID_ORDER));
        return all;
    }

    private List<Draft> normalizeBatch(UUID sessionId, int index, int batches, List<Source> batch, Runnable gate) {
        Map<String, Source> byId = new HashMap<>();
        for (Source s : batch) {
            byId.put(s.getId(), s);
        }
        List<String> errors = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            String input = EventNormalizationPrompt.input(index + 1, batches, batch, errors);
            Optional<String> text = responses.createTextOrThrow(sessionId,
                EventNormalizationPrompt.body(responses.model(), input), gate);
            List<String> found = new ArrayList<>();
            List<Draft> drafts = text.isPresent() ? parse(text.get(), index, batch, byId, found) : null;
            if (text.isEmpty()) {
                found.add(SCHEMA_ERROR);
            }
            if (drafts != null && found.isEmpty()) {
                return drafts;
            }
            errors = found;
        }
        return fallback(index, batch);
    }

    /** Returns the drafts; violations are added to {@code errors}. */
    private List<Draft> parse(String text, int index, List<Source> batch, Map<String, Source> byId, List<String> errors) {
        JsonNode root;
        try {
            root = json.readTree(text);
        } catch (Exception e) {
            errors.add(SCHEMA_ERROR);
            return null;
        }
        if (root == null || !root.isObject() || !root.path("events").isArray()) {
            errors.add(SCHEMA_ERROR);
            return null;
        }
        for (JsonNode ev : root.path("events")) {
            if (!wellFormed(ev)) {
                errors.add(SCHEMA_ERROR);
                return null;
            }
        }
        List<Draft> drafts = new ArrayList<>();
        int n = 0;
        for (JsonNode ev : root.path("events")) {
            n++;
            List<String> ids = new ArrayList<>();
            for (JsonNode id : ev.path("sourceIds")) {
                ids.add(id.asString());
            }
            String summary = ev.path("summary").asString().trim();
            String category = ev.path("category").asString().trim();
            LocalDate date = null;
            boolean dateOk = true;
            if (!ev.path("date").isNull()) {
                try {
                    date = LocalDate.parse(ev.path("date").asString(), DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (DateTimeParseException e) {
                    dateOk = false;
                }
            }
            double confidence = ev.path("confidence").asDouble();
            if (ids.isEmpty()) {
                errors.add("event " + n + " has no source ids");
            }
            if (summary.isEmpty()) {
                errors.add("event " + n + " has a blank summary");
            }
            if (category.isEmpty()) {
                errors.add("event " + n + " has a blank category");
            }
            if (!dateOk) {
                errors.add("event " + n + " has an invalid date");
            }
            if (confidence < 0 || confidence > 1) {
                errors.add("event " + n + " has confidence outside 0..1");
            }
            if (errors.isEmpty()) {
                drafts.add(accept(index, ids, date, category, ev, summary, confidence, byId));
            }
        }
        Set<String> seen = new HashSet<>();
        Set<String> reported = new HashSet<>();
        for (JsonNode ev : root.path("events")) {
            for (JsonNode idNode : ev.path("sourceIds")) {
                String id = idNode.asString();
                if (!byId.containsKey(id)) {
                    if (reported.add("u" + id)) {
                        errors.add("unknown source id " + untrusted(id));
                    }
                } else if (!seen.add(id) && reported.add("d" + id)) {
                    errors.add("source id " + untrusted(id) + " appears more than once");
                }
            }
        }
        for (Source s : batch) {
            if (!seen.contains(s.getId())) {
                errors.add("source id " + s.getId() + " is missing");
            }
        }
        return drafts;
    }

    /** A model-made id inside an error line: sanitized, cut to 32 characters plus an ellipsis. */
    private static String untrusted(String id) {
        String clean = PromptText.sanitize(id);
        return clean.length() > 32 ? cut(clean, 32) + "…" : clean;
    }

    private static boolean wellFormed(JsonNode ev) {
        if (!ev.isObject() || !ev.path("sourceIds").isArray() || !ev.path("entities").isArray()) {
            return false;
        }
        for (JsonNode id : ev.path("sourceIds")) {
            if (!id.isString()) {
                return false;
            }
        }
        for (JsonNode en : ev.path("entities")) {
            if (!en.isString()) {
                return false;
            }
        }
        return ev.has("date") && (ev.path("date").isNull() || ev.path("date").isString())
            && ev.path("category").isString() && ev.path("summary").isString()
            && ev.has("disagreement") && (ev.path("disagreement").isNull() || ev.path("disagreement").isString())
            && ev.path("confidence").isNumber();
    }

    private Draft accept(int index, List<String> ids, LocalDate modelDate, String category, JsonNode ev, String summary,
                         double confidence, Map<String, Source> byId) {
        List<String> sorted = new ArrayList<>(new LinkedHashSet<>(ids));
        sorted.sort(ID_ORDER);
        List<String> entities = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode en : ev.path("entities")) {
            String clean = en.asString().replaceAll("\\s+", " ").trim();
            if (!clean.isEmpty() && seen.add(clean.toLowerCase(Locale.ROOT))) {
                entities.add(clean);
            }
        }
        LocalDate date = modelDate;
        if (date == null) {
            for (String id : sorted) {
                date = earliest(date, dateOf(byId.get(id)));
            }
        }
        String disagreement = null;
        if (!ev.path("disagreement").isNull()) {
            String d = cut(ev.path("disagreement").asString().replaceAll("\\s+", " ").trim(), MAX_DISAGREEMENT)
                .stripTrailing();
            disagreement = d.isEmpty() ? null : d;
        }
        String finalSummary = applySummaryRule(summary, disagreement);
        return new Draft(index, sorted, date, category, entities, finalSummary, summary, disagreement, confidence);
    }

    /** Cuts to at most {@code max} characters without splitting a surrogate pair. */
    static String cut(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        int end = max;
        if (end > 0 && Character.isHighSurrogate(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }

    /** Summary rule of research-pipeline.md: the summary states the disagreement and stays within 600 characters. */
    static String applySummaryRule(String summary, String disagreement) {
        if (disagreement == null) {
            return cut(summary, MAX_SUMMARY);
        }
        String head = summary.substring(0, Math.min(MAX_SUMMARY, summary.length()));
        if (head.toLowerCase(Locale.ROOT).contains("reports differ")) {
            return cut(summary, MAX_SUMMARY);
        }
        String d = disagreement.endsWith(".") ? disagreement.substring(0, disagreement.length() - 1) : disagreement;
        String suffix = " Reports differ on " + d + ".";
        return cut(summary, MAX_SUMMARY - suffix.length()).stripTrailing() + suffix;
    }

    private static LocalDate dateOf(Source s) {
        OffsetDateTime t = s == null ? null : s.getPublishedAt();
        return t == null ? null : LocalDate.ofInstant(t.toInstant(), ZoneOffset.UTC);
    }

    private static LocalDate earliest(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isBefore(b) ? a : b;
    }

    /** One group per source, titles with Jaccard >= 0.6 joined transitively. */
    private List<Draft> fallback(int index, List<Source> batch) {
        int n = batch.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (TokenSimilarity.jaccard(batch.get(i).getTitle(), batch.get(j).getTitle()) >= 0.6) {
                    parent[find(parent, i)] = find(parent, j);
                }
            }
        }
        Map<Integer, List<Source>> groups = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(batch.get(i));
        }
        List<Draft> out = new ArrayList<>();
        for (List<Source> g : groups.values()) {
            g.sort(Comparator.comparing(Source::getId, ID_ORDER));
            Source first = g.get(0);
            LocalDate date = null;
            List<String> ids = new ArrayList<>();
            for (Source s : g) {
                ids.add(s.getId());
                date = earliest(date, dateOf(s));
            }
            String topic = first.getTopic() == null || first.getTopic().isBlank() ? "general" : first.getTopic();
            String title = first.getTitle();
            out.add(new Draft(index, ids, date, topic, new ArrayList<>(), cut(title, MAX_SUMMARY), title, null, 0.5));
        }
        out.sort(Comparator.comparing(d -> d.sourceIds().get(0), ID_ORDER));
        return out;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private List<Draft> merge(List<Draft> drafts) {
        List<Draft> kept = new ArrayList<>();
        for (Draft d : drafts) {
            int target = -1;
            for (int i = 0; i < kept.size(); i++) {
                Draft k = kept.get(i);
                if (k.batch() != d.batch() && sharesEntity(k.entities(), d.entities())
                    && TokenSimilarity.jaccard(k.rawSummary(), d.rawSummary()) >= 0.5) {
                    target = i;
                    break;
                }
            }
            if (target < 0) {
                kept.add(d);
                continue;
            }
            Draft k = kept.get(target);
            List<String> ids = new ArrayList<>(new LinkedHashSet<>(k.sourceIds()));
            ids.addAll(d.sourceIds());
            ids = new ArrayList<>(new LinkedHashSet<>(ids));
            ids.sort(ID_ORDER);
            List<String> entities = new ArrayList<>(k.entities());
            Set<String> seen = new HashSet<>();
            for (String e : entities) {
                seen.add(e.toLowerCase(Locale.ROOT));
            }
            for (String e : d.entities()) {
                if (seen.add(e.toLowerCase(Locale.ROOT))) {
                    entities.add(e);
                }
            }
            String disagreement = k.disagreement() != null ? k.disagreement() : d.disagreement();
            kept.set(target, new Draft(k.batch(), ids, earliest(k.date(), d.date()), k.category(), entities,
                applySummaryRule(k.summary(), disagreement), k.rawSummary(), disagreement,
                Math.max(k.confidence(), d.confidence())));
        }
        return kept;
    }

    private static boolean sharesEntity(List<String> a, List<String> b) {
        Set<String> x = new HashSet<>();
        for (String e : a) {
            x.add(e.toLowerCase(Locale.ROOT));
        }
        for (String e : b) {
            if (x.contains(e.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
