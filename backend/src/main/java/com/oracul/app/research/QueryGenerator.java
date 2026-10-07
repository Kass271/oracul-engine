package com.oracul.app.research;

import com.oracul.app.api.model.HorizonOption;
import com.oracul.app.api.model.PipelineQuery;
import com.oracul.app.api.model.QueryExpansionMode;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.WildcardPipeline;
import com.oracul.app.chatgpt.ChatGptCallException;
import com.oracul.app.chatgpt.HttpResponsesClient;
import com.oracul.app.scenario.ScenarioCatalogueData;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** One QUERY_GENERATION call per pipeline, parallel and windowed (FR-51); every failure falls back to templates. */
@Component
public class QueryGenerator {

    private static final Logger log = LoggerFactory.getLogger(QueryGenerator.class);
    private static final long POLL_MILLIS = 50;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpResponsesClient responses;
    private final QueryTemplates templates;
    private final Clock clock;
    private final Duration window;
    private final int concurrency;
    private final Map<String, String> horizonLabels = new HashMap<>();

    QueryGenerator(HttpResponsesClient responses, QueryTemplates templates, Clock clock,
                   @Value("${oracul.search.query-generation-window:PT30S}") Duration window,
                   @Value("${oracul.search.search-window:PT60S}") Duration searchWindow,
                   @Value("${oracul.search.stage-budget:PT90S}") Duration stageBudget,
                   @Value("${oracul.search.query-generation-concurrency:4}") int concurrency) {
        if (concurrency < 1 || concurrency > 8) {
            throw new IllegalStateException("oracul.search.query-generation-concurrency must be between 1 and 8");
        }
        if (window.isZero() || window.isNegative()) {
            throw new IllegalStateException("oracul.search.query-generation-window must be greater than zero");
        }
        if (window.compareTo(searchWindow) > 0) {
            throw new IllegalStateException(
                "oracul.search.query-generation-window must not exceed oracul.search.search-window");
        }
        if (searchWindow.compareTo(stageBudget) > 0) {
            throw new IllegalStateException(
                "oracul.search.stage-budget must not be shorter than oracul.search.search-window");
        }
        this.responses = responses;
        this.templates = templates;
        this.clock = clock;
        this.window = window;
        this.concurrency = concurrency;
        for (HorizonOption h : ScenarioCatalogueData.catalogue().getHorizons()) {
            horizonLabels.put(h.getCode().getValue(), h.getLabel());
        }
    }

    private enum Reason { FAILED, UNUSABLE, WINDOW, GUARD }

    /** What one pipeline's call produced: model texts, or the reason the templates are used. */
    private record Answer(List<String> texts, Reason reason) {
    }

    /**
     * May throw ChatGptCallException (session expired, registration invalid, plan not eligible); every other failure
     * leaves that pipeline with its template queries.
     */
    public SearchPlan generate(UUID sessionId, ScenarioConfiguration cfg, SearchPlan template, Instant t0,
                               Instant deadlineAt, BooleanSupplier mayStart) throws InterruptedException {
        List<WildcardPipeline> pipelines = template.getPipelines();
        SearchBudget budget = SearchBudget.generation(clock, t0, window, deadlineAt);
        String horizon = horizonLabels.get(cfg.getHorizon().getValue());
        int n = pipelines.size();
        Answer[] answers = new Answer[n];
        Future<?>[] futures = new Future<?>[n];
        AtomicReference<ChatGptCallException> fatal = new AtomicReference<>();
        Semaphore permits = new Semaphore(concurrency, true);
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        try {
            int next = 0;
            while (next < n && !budget.expired(SearchBudget.Phase.QUERY_GENERATION) && fatal.get() == null) {
                if (!permits.tryAcquire(POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                    continue;
                }
                int i = next++;
                WildcardPipeline p = pipelines.get(i);
                futures[i] = pool.submit(() -> {
                    try {
                        answers[i] = call(sessionId, cfg, p, horizon, budget, mayStart, fatal);
                    } finally {
                        permits.release();
                    }
                });
            }
            while (fatal.get() == null && !budget.expired(SearchBudget.Phase.QUERY_GENERATION)
                && !allDone(futures, next)) {
                Thread.sleep(POLL_MILLIS);
            }
            if (fatal.get() != null) {
                throw fatal.get();
            }
        } finally {
            for (Future<?> f : futures) {
                if (f != null) {
                    f.cancel(true);
                }
            }
            pool.shutdownNow();
        }
        List<WildcardPipeline> out = new ArrayList<>();
        boolean anyModel = false;
        for (int i = 0; i < n; i++) {
            WildcardPipeline p = pipelines.get(i);
            Answer a = answers[i];
            Reason reason = a == null ? Reason.WINDOW : a.reason();
            if (a != null && a.texts() != null) {
                WildcardPipeline merged = merge(p, a.texts(), templates.forPipeline(p, cfg));
                out.add(merged);
                if (merged.getQueryMode() == QueryExpansionMode.MODEL) {
                    anyModel = true;
                    continue;
                }
                reason = Reason.UNUSABLE;
            } else {
                out.add(p);
            }
            log.warn("query generation fell back to templates: pipeline={} reason={}", p.getId(), reason);
        }
        return new SearchPlan(template.getQueryBudget(),
            anyModel ? QueryExpansionMode.MODEL : QueryExpansionMode.TEMPLATE_FALLBACK, template.getBuckets(),
            template.getIntents(), template.getQueries()).pipelines(out);
    }

    private static boolean allDone(Future<?>[] futures, int started) {
        for (int i = 0; i < started; i++) {
            if (!futures[i].isDone()) {
                return false;
            }
        }
        return started == futures.length;
    }

    private Answer call(UUID sessionId, ScenarioConfiguration cfg, WildcardPipeline p, String horizon,
                        SearchBudget budget, BooleanSupplier mayStart, AtomicReference<ChatGptCallException> fatal) {
        if (budget.expired(SearchBudget.Phase.QUERY_GENERATION)) {
            return new Answer(null, Reason.WINDOW);
        }
        if (!mayStart.getAsBoolean()) {
            return new Answer(null, Reason.GUARD);
        }
        try {
            String input = QueryGenerationPrompt.input(p, cfg, horizon);
            Optional<String> text = responses.createText(sessionId,
                QueryGenerationPrompt.body(responses.model(), input));
            if (text.isEmpty()) {
                return new Answer(null, Reason.FAILED);
            }
            List<String> texts = parse(text.get());
            if (budget.expired(SearchBudget.Phase.QUERY_GENERATION)) {
                return new Answer(null, Reason.WINDOW);
            }
            return texts == null ? new Answer(null, Reason.UNUSABLE) : new Answer(texts, null);
        } catch (ChatGptCallException e) {
            fatal.compareAndSet(null, e);
            return new Answer(null, Reason.FAILED);
        } catch (RuntimeException e) {
            return new Answer(null, Reason.FAILED);
        }
    }

    /** The query texts of the answer, or null when it is unusable. */
    static List<String> parse(String outputText) {
        if (outputText == null) {
            return null;
        }
        try {
            JsonNode root = JSON.readTree(outputText);
            if (root == null || !root.isObject() || !root.path("queries").isArray()) {
                return null;
            }
            List<String> out = new ArrayList<>();
            for (JsonNode q : root.path("queries")) {
                if (!q.isString()) {
                    return null;
                }
                out.add(q.asString());
            }
            return out;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Post-processing of FR-51 step 5: rule Q, dedup, keep the first q, fill from the templates. */
    static WildcardPipeline merge(WildcardPipeline template, List<String> modelTexts, List<String> templateTexts) {
        int q = template.getQueries().size();
        List<String> kept = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : modelTexts) {
            String t = QueryRules.clean(raw);
            if (kept.size() >= q) {
                break;
            }
            if (QueryRules.valid(t) && seen.add(t.toLowerCase(Locale.ROOT))) {
                kept.add(t);
            }
        }
        boolean model = !kept.isEmpty();
        for (String t : templateTexts) {
            if (kept.size() >= q) {
                break;
            }
            if (seen.add(t.toLowerCase(Locale.ROOT))) {
                kept.add(t);
            }
        }
        List<PipelineQuery> queries = new ArrayList<>();
        for (int i = 0; i < q; i++) {
            PipelineQuery old = template.getQueries().get(i);
            queries.add(new PipelineQuery(old.getId(), kept.get(i), old.getStatus(), old.getArticlesReturned()));
        }
        return new WildcardPipeline(template.getId(), template.getKind(), template.getLabel(), template.getHeading(),
            model ? QueryExpansionMode.MODEL : QueryExpansionMode.TEMPLATE_FALLBACK, queries)
            .level(template.getLevel()).topicKey(template.getTopicKey())
            .candidatesConsidered(template.getCandidatesConsidered()).sourceIds(template.getSourceIds());
    }
}
