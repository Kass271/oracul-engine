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
import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.research.EventClassifier;
import com.oracul.app.research.EventRanker;
import com.oracul.app.research.EvidencePackRepository;
import com.oracul.app.research.EvidencePackService;
import com.oracul.app.research.EvidenceSelector;
import com.oracul.app.research.MinCoreThresholds;
import com.oracul.app.research.EventNormalizer;
import com.oracul.app.research.EventRepository;
import com.oracul.app.research.QueryExpander;
import com.oracul.app.research.SearchPlanner;
import com.oracul.app.research.SourceRepository;
import com.oracul.app.research.SourceRetrieval;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

    static final String SESSION_EXPIRED_MESSAGE = RunFailures.message(com.oracul.app.api.model.RunFailureCode.CHATGPT_SESSION_EXPIRED);

    private final GenerationRunRepository runs;
    private final SourceRepository sources;
    private final SearchPlanner planner;
    private final QueryExpander expander;
    private final SourceRetrieval retrieval;
    private final EventNormalizer normalizer;
    private final EventClassifier classifier;
    private final EventRepository events;
    private final EventRanker ranker;
    private final EvidenceSelector selector;
    private final MinCoreThresholds minCore;
    private final EvidencePackService packService;
    private final EvidencePackRepository packs;
    private final RunGuard guard;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int queryBudget;
    private final Duration minStageDuration;

    ResearchPipeline(GenerationRunRepository runs, SourceRepository sources, SearchPlanner planner,
                     QueryExpander expander, SourceRetrieval retrieval, EventNormalizer normalizer,
                     EventClassifier classifier, EventRepository events, EventRanker ranker, EvidenceSelector selector,
                     MinCoreThresholds minCore, EvidencePackService packService, EvidencePackRepository packs, RunGuard guard, TransactionTemplate tx,
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
        this.ranker = ranker;
        this.selector = selector;
        this.minCore = minCore;
        this.packService = packService;
        this.packs = packs;
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
        Instant t0 = clock.instant();
        long started = begin(runId, RunStage.RESEARCH_STRATEGY, t0);
        if (started < 0) {
            return false;
        }
        Instant deadlineAt = runs.deadlineAt(runId);
        SearchPlan plan;
        try {
            SearchPlan template = planner.plan(profile, cfg, queryBudget);
            plan = expander.expand(sessionId, profile, cfg, template);
        } catch (ChatGptCallException e) {
            runs.markFailed(runId, e.code(), e.getMessage(), e.providerCode(), now());
            return false;
        }
        runs.storeSearchPlan(runId, plan, now());
        remainder(started);

        // ---- stage 3: SEARCHING ----
        started = begin(runId, RunStage.SEARCHING);
        if (started < 0) {
            return false;
        }
        SourceRetrieval.SearchOutcome outcome = retrieval.search(plan, cfg.getHorizon(), t0, deadlineAt,
            () -> guard.check(runId));
        ResearchCounts counts = new ResearchCounts(outcome.searches(), outcome.articlesRetrieved(), 0, 0, 0, 0, 0);
        Boolean searched = tx.execute(s -> {
            if (!guard.lockAndCheck(runId)) {
                s.setRollbackOnly();
                return false;
            }
            runs.storeSearchResults(runId, outcome.plan(), counts, now());
            return true;
        });
        if (!Boolean.TRUE.equals(searched)) {
            abandon(runId);
            return false;
        }
        remainder(started);

        // ---- stage 4: READING_SOURCES ----
        started = begin(runId, RunStage.READING_SOURCES);
        if (started < 0) {
            return false;
        }
        List<SourceRepository.Stored> found = retrieval.readSources(outcome, cfg.getHorizon(), () -> guard.check(runId));
        ResearchCounts withSources = new ResearchCounts(counts.getSearches(), counts.getArticlesRetrieved(),
            found.size(), 0, 0, 0, 0);
        Boolean read = tx.execute(s -> {
            if (!guard.lockAndCheck(runId)) {
                s.setRollbackOnly();
                return false;
            }
            sources.insertAll(runId, found);
            runs.storeCounts(runId, withSources, now());
            return true;
        });
        if (!Boolean.TRUE.equals(read)) {
            abandon(runId);
            return false;
        }
        remainder(started);

        // ---- stage 5: CONNECTING_SIGNALS ----
        started = begin(runId, RunStage.CONNECTING_SIGNALS);
        if (started < 0) {
            return false;
        }
        List<NormalizedEvent> normalized = List.of();
        if (!found.isEmpty()) {
            List<Source> list = found.stream().map(SourceRepository.Stored::source).toList();
            try {
                normalized = normalizer.normalize(sessionId, runId, list);
                classifier.classify(sessionId, runId, profile.getTopics(), normalized, list);
            } catch (ChatGptCallException e) {
                runs.markFailed(runId, e.code(), e.getMessage(), e.providerCode(), now());
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

        // ---- stage 6: RANKING ----
        started = begin(runId, RunStage.RANKING);
        if (started < 0) {
            return false;
        }
        Instant cutoff = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        Map<String, Source> byId = new LinkedHashMap<>();
        for (SourceRepository.Stored st : found) {
            byId.put(st.source().getId(), st.source());
        }
        List<NormalizedEvent> ranked = ranker.rank(normalized, byId, profile, cutoff);
        EvidenceSelector.Result selection = selector.select(ranked, byId, profile);
        Map<String, NormalizedEvent> selected = new LinkedHashMap<>();
        for (List<NormalizedEvent> section : List.of(selection.core(), selection.supporting(),
            selection.counterSignals())) {
            for (NormalizedEvent e : section) {
                selected.put(e.getId(), e);
            }
        }
        List<NormalizedEvent> finalEvents = new ArrayList<>();
        for (NormalizedEvent e : ranked) {
            finalEvents.add(selected.getOrDefault(e.getId(), e));
        }
        String generationId = runs.find(runId, sessionId).orElseThrow().generationId();
        UUID packId = UUID.randomUUID();
        EvidencePack pack = packService.build(packId, generationId, cutoff, cfg, profile, selection, byId);
        ResearchCounts withPack = new ResearchCounts(withEvents.getSearches(), withEvents.getArticlesRetrieved(),
            withEvents.getArticlesConsidered(), withEvents.getUniqueEvents(), selected.size(),
            selection.counterSignals().size(), 0);
        int realism = cfg.getRealism();
        int total = selection.core().size() + selection.supporting().size() + selection.counterSignals().size();
        var decision = EvidenceNotes.decide(selection.core().size(), total, realism, minCore);
        Boolean packed = tx.execute(s -> {
            if (!guard.lockAndCheck(runId)) {
                s.setRollbackOnly();
                return false;
            }
            events.updateRanking(runId, finalEvents);
            packs.insert(runId, pack, now());
            if (decision.isPresent()) {
                var d = decision.get();
                runs.storePack(runId, packId, withPack, d.kind().getValue(), d.coreItems(), d.coreNeeded(),
                    d.suggestedRealism(), now());
            } else {
                runs.storePack(runId, packId, withPack, now());
            }
            return true;
        });
        if (!Boolean.TRUE.equals(packed)) {
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

    /** Checks the run guard, then persists the stage; -1 when the run must be abandoned. */
    private long begin(UUID runId, RunStage stage) {
        return begin(runId, stage, clock.instant());
    }

    private long begin(UUID runId, RunStage stage, Instant at) {
        if (!guard.check(runId)) {
            abandon(runId);
            return -1;
        }
        runs.markStage(runId, stage, at.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC));
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
