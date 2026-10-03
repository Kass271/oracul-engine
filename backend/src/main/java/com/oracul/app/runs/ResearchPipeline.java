package com.oracul.app.runs;

import com.oracul.app.api.model.ResearchCounts;
import com.oracul.app.api.model.ResearchProfile;
import com.oracul.app.api.model.RunStage;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.NormalizedEvent;
import com.oracul.app.api.model.Source;
import com.oracul.app.chatgpt.CallAbandonedException;
import com.oracul.app.chatgpt.ChatGptCallException;
import com.oracul.app.common.ApiException;
import com.oracul.app.research.EventClassifier;
import com.oracul.app.research.EventNormalizer;
import com.oracul.app.research.EventRepository;
import com.oracul.app.research.QueryExpander;
import com.oracul.app.research.SearchPlanner;
import com.oracul.app.research.SourceRepository;
import com.oracul.app.research.SourceRetrieval;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Real stages 2-4 of the generation pipeline: strategy, search, source reading (FR-12, FR-13). */
@Component
public class ResearchPipeline {

    static final String SESSION_EXPIRED_MESSAGE = "ChatGPT session expired — please reconnect";
    static final String NEWS_UNAVAILABLE_MESSAGE = "ORACUL could not reach its news sources — try again later";

    private final GenerationRunRepository runs;
    private final SourceRepository sources;
    private final SearchPlanner planner;
    private final QueryExpander expander;
    private final SourceRetrieval retrieval;
    private final EventNormalizer normalizer;
    private final EventClassifier classifier;
    private final EventRepository events;
    private final RunGuard guard;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int queryBudget;
    private final Duration minStageDuration;

    ResearchPipeline(GenerationRunRepository runs, SourceRepository sources, SearchPlanner planner,
                     QueryExpander expander, SourceRetrieval retrieval, EventNormalizer normalizer,
                     EventClassifier classifier, EventRepository events, RunGuard guard, TransactionTemplate tx,
                     Clock clock,
                     @Value("${oracul.research.query-budget:20}") int queryBudget,
                     @Value("${oracul.run.min-stage-duration:PT0S}") Duration minStageDuration) {
        if (queryBudget < 4 || queryBudget > 100) {
            throw new IllegalStateException("oracul.research.query-budget must be between 4 and 100");
        }
        this.runs = runs;
        this.sources = sources;
        this.planner = planner;
        this.expander = expander;
        this.retrieval = retrieval;
        this.normalizer = normalizer;
        this.classifier = classifier;
        this.events = events;
        this.guard = guard;
        this.tx = tx;
        this.clock = clock;
        this.queryBudget = queryBudget;
        this.minStageDuration = minStageDuration;
    }

    /** Runs stages 2-4. Returns false when the run was ended (FAILED) and later stages must not run. */
    boolean run(UUID runId, UUID sessionId, ScenarioConfiguration cfg, ResearchProfile profile)
        throws InterruptedException {
        // ---- stage 2: RESEARCH_STRATEGY ----
        long started = begin(runId, RunStage.RESEARCH_STRATEGY);
        SearchPlan plan;
        try {
            SearchPlan template = planner.plan(profile, cfg, queryBudget);
            plan = expander.expand(sessionId, profile, cfg, template);
        } catch (ApiException e) {
            runs.markFailed(runId, "CHATGPT_SESSION_EXPIRED", SESSION_EXPIRED_MESSAGE, now());
            return false;
        }
        runs.storeSearchPlan(runId, plan, now());
        remainder(started);

        // ---- stage 3: SEARCHING ----
        started = begin(runId, RunStage.SEARCHING);
        SourceRetrieval.SearchOutcome outcome = retrieval.search(plan, cfg.getHorizon());
        ResearchCounts counts = new ResearchCounts(outcome.searches(), outcome.articlesRetrieved(), 0, 0, 0, 0, 0);
        if (outcome.allFailed()) {
            tx.executeWithoutResult(s -> {
                runs.storeSearchResults(runId, outcome.plan(), counts, now());
                runs.markFailed(runId, "NEWS_UNAVAILABLE", NEWS_UNAVAILABLE_MESSAGE, now());
            });
            return false;
        }
        runs.storeSearchResults(runId, outcome.plan(), counts, now());
        remainder(started);

        // ---- stage 4: READING_SOURCES ----
        started = begin(runId, RunStage.READING_SOURCES);
        List<SourceRepository.Stored> found = retrieval.readSources(outcome, cfg.getHorizon());
        ResearchCounts withSources = new ResearchCounts(counts.getSearches(), counts.getArticlesRetrieved(),
            found.size(), 0, 0, 0, 0);
        tx.executeWithoutResult(s -> {
            sources.insertAll(runId, found);
            runs.storeCounts(runId, withSources, now());
        });
        remainder(started);

        // ---- stage 5: CONNECTING_SIGNALS ----
        started = begin(runId, RunStage.CONNECTING_SIGNALS);
        List<NormalizedEvent> normalized = List.of();
        if (!found.isEmpty()) {
            List<Source> list = found.stream().map(SourceRepository.Stored::source).toList();
            try {
                normalized = normalizer.normalize(sessionId, runId, list);
                classifier.classify(sessionId, runId, profile.getTopics(), normalized, list);
            } catch (ChatGptCallException e) {
                runs.markFailed(runId, e.code(), e.getMessage(), now());
                return false;
            } catch (CallAbandonedException e) {
                abandon(runId);
                return false;
            }
        }
        Map<String, List<String>> entities = new LinkedHashMap<>();
        for (NormalizedEvent e : normalized) {
            for (String id : e.getSourceIds()) {
                entities.put(id, e.getEntities());
            }
        }
        ResearchCounts withEvents = new ResearchCounts(withSources.getSearches(), withSources.getArticlesRetrieved(),
            withSources.getArticlesConsidered(), normalized.size(), 0, 0, 0);
        List<NormalizedEvent> toStore = normalized;
        Boolean stored = tx.execute(s -> {
            if (!guard.lockAndCheck(runId)) {
                s.setRollbackOnly();
                return false;
            }
            events.insertAll(runId, toStore);
            sources.updateEntities(runId, entities);
            runs.storeCounts(runId, withEvents, now());
            return true;
        });
        if (!Boolean.TRUE.equals(stored)) {
            abandon(runId);
            return false;
        }
        remainder(started);
        return true;
    }

    /** The run guard stopped stage 5: commit RUN_TIMEOUT if the run is still RUNNING past its deadline, else do nothing. */
    private void abandon(UUID runId) {
        runs.failTimedOut(runId, now());
    }

    private long begin(UUID runId, RunStage stage) {
        runs.markStage(runId, stage, now());
        return System.nanoTime();
    }

    private void remainder(long startedNanos) throws InterruptedException {
        if (minStageDuration.isZero() || minStageDuration.isNegative()) {
            return;
        }
        long left = minStageDuration.toNanos() - (System.nanoTime() - startedNanos);
        if (left > 0) {
            Thread.sleep(Duration.ofNanos(left));
        }
    }

    private OffsetDateTime now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }
}
