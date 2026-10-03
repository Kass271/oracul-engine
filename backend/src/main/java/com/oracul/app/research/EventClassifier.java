package com.oracul.app.research;

import com.oracul.app.api.model.EventClassification;
import com.oracul.app.api.model.NormalizedEvent;
import com.oracul.app.api.model.ResearchTopic;
import com.oracul.app.api.model.Source;
import com.oracul.app.api.model.Trend;
import com.oracul.app.api.model.WildcardMatch;
import com.oracul.app.chatgpt.CallAbandonedException;
import com.oracul.app.chatgpt.HttpResponsesClient;
import com.oracul.app.runs.RunGuard;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Semantic classification of events (FR-15): batches, validation without clamping, one follow-up pass. */
@Component
public class EventClassifier {

    public static final String FAILED = "CLASSIFICATION_FAILED";

    private final HttpResponsesClient responses;
    private final JsonMapper json = JsonMapper.builder().build();
    private final RunGuard guard;
    private final int batchSize;
    private final int concurrency;

    EventClassifier(HttpResponsesClient responses, RunGuard guard,
                    @Value("${oracul.events.classification-batch-size:20}") int batchSize,
                    @Value("${oracul.events.classification-concurrency:4}") int concurrency) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalStateException("oracul.events.classification-batch-size must be between 1 and 100");
        }
        if (concurrency < 1 || concurrency > 16) {
            throw new IllegalStateException("oracul.events.classification-concurrency must be between 1 and 16");
        }
        this.responses = responses;
        this.guard = guard;
        this.batchSize = batchSize;
        this.concurrency = concurrency;
    }

    /**
     * Sets classification on every event that gets a valid one and excludedReason CLASSIFICATION_FAILED on the rest.
     * Events must be in id order. May throw ChatGptCallException or CallAbandonedException (run guard).
     */
    public void classify(UUID sessionId, UUID runId, List<ResearchTopic> topics, List<NormalizedEvent> events,
                         List<Source> sources) {
        Map<String, Double> quality = new HashMap<>();
        for (Source s : sources) {
            quality.put(s.getId(), s.getSourceQuality());
        }
        List<NormalizedEvent> bad = pass(sessionId, runId, topics, events, quality);
        if (!bad.isEmpty()) {
            bad = pass(sessionId, runId, topics, bad, quality);
        }
        for (NormalizedEvent e : bad) {
            e.setClassification(null);
            e.setExcludedReason(FAILED);
        }
    }

    /** One pass in parallel batches; returns the events that stay unclassified, in the order of {@code events}. */
    private List<NormalizedEvent> pass(UUID sessionId, UUID runId, List<ResearchTopic> topics,
                                       List<NormalizedEvent> events, Map<String, Double> quality) {
        int batches = (events.size() + batchSize - 1) / batchSize;
        Runnable check = () -> {
            if (!guard.check(runId)) {
                throw new CallAbandonedException();
            }
        };
        List<List<NormalizedEvent>> perBatch = BatchRunner.run(concurrency, batches, check, (b, gate) -> {
            List<NormalizedEvent> batch = events.subList(b * batchSize, Math.min(events.size(), (b + 1) * batchSize));
            String input = EventClassificationPrompt.input(topics, batch);
            Optional<String> text = responses.createTextOrThrow(sessionId,
                EventClassificationPrompt.body(responses.model(), input), gate);
            Map<String, JsonNode> entries = text.isPresent() ? entries(text.get(), batch) : Map.of();
            List<NormalizedEvent> bad = new ArrayList<>();
            for (NormalizedEvent e : batch) {
                EventClassification c = entries.containsKey(e.getId())
                    ? build(entries.get(e.getId()), topics, e, quality) : null;
                if (c == null) {
                    bad.add(e);
                } else {
                    e.setClassification(c);
                }
            }
            return bad;
        });
        List<NormalizedEvent> bad = new ArrayList<>();
        for (List<NormalizedEvent> b : perBatch) {
            bad.addAll(b);
        }
        return bad;
    }

    /** First entry per known event id of the batch. */
    private Map<String, JsonNode> entries(String text, List<NormalizedEvent> batch) {
        Set<String> ids = new HashSet<>();
        for (NormalizedEvent e : batch) {
            ids.add(e.getId());
        }
        Map<String, JsonNode> out = new HashMap<>();
        try {
            JsonNode root = json.readTree(text);
            if (root == null || !root.isObject() || !root.path("classifications").isArray()) {
                return out;
            }
            for (JsonNode entry : root.path("classifications")) {
                if (entry.isObject() && entry.path("eventId").isString()) {
                    String id = entry.path("eventId").asString();
                    if (ids.contains(id)) {
                        out.putIfAbsent(id, entry);
                    }
                }
            }
        } catch (Exception e) {
            return new HashMap<>();
        }
        return out;
    }

    /** Null when the entry is bad. */
    private EventClassification build(JsonNode n, List<ResearchTopic> topics, NormalizedEvent event,
                                      Map<String, Double> quality) {
        if (!n.path("topic").isString() || n.path("topic").asString().isBlank()) {
            return null;
        }
        if (!n.path("subtopics").isArray() || !n.path("geography").isString() || !n.path("wildcardMatches").isArray()) {
            return null;
        }
        List<String> subtopics = new ArrayList<>();
        for (JsonNode s : n.path("subtopics")) {
            if (!s.isString()) {
                return null;
            }
            if (!s.asString().isBlank()) {
                subtopics.add(s.asString().trim());
            }
        }
        Double sentiment = number(n, "sentiment", -1, 1);
        Double risk = number(n, "risk", 0, 1);
        Double opportunity = number(n, "opportunity", 0, 1);
        Double impact = number(n, "impact", 0, 1);
        Double novelty = number(n, "novelty", 0, 1);
        if (sentiment == null || risk == null || opportunity == null || impact == null || novelty == null) {
            return null;
        }
        Trend trend;
        if (!n.path("trend").isString()) {
            return null;
        }
        try {
            trend = Trend.fromValue(n.path("trend").asString());
        } catch (IllegalArgumentException e) {
            return null;
        }
        Set<String> known = new HashSet<>();
        for (ResearchTopic t : topics) {
            known.add(t.getKey());
        }
        Map<String, Double> scores = new HashMap<>();
        for (JsonNode w : n.path("wildcardMatches")) {
            if (!w.isObject() || !w.path("key").isString() || !w.path("score").isNumber()) {
                return null;
            }
            String key = w.path("key").asString();
            if (!known.contains(key) || scores.containsKey(key)) {
                continue;
            }
            double score = w.path("score").asDouble();
            if (score < 0 || score > 1) {
                return null;
            }
            scores.put(key, score);
        }
        List<WildcardMatch> matches = new ArrayList<>();
        for (ResearchTopic t : topics) {
            matches.add(new WildcardMatch(t.getKey(), scores.getOrDefault(t.getKey(), 0.0)));
        }
        double sourceQuality = 0;
        for (String id : event.getSourceIds()) {
            sourceQuality = Math.max(sourceQuality, quality.getOrDefault(id, 0.0));
        }
        String geography = n.path("geography").asString().trim();
        return new EventClassification(n.path("topic").asString().trim(), subtopics, sentiment, risk, opportunity,
            impact, novelty, sourceQuality, trend, geography.isEmpty() ? "global" : geography, matches);
    }

    private static Double number(JsonNode n, String field, double min, double max) {
        JsonNode v = n.path(field);
        if (!v.isNumber()) {
            return null;
        }
        double d = v.asDouble();
        return d < min || d > max ? null : d;
    }
}
