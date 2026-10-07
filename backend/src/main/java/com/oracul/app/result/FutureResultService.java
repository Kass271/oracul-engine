package com.oracul.app.result;

import com.oracul.app.api.model.EvidenceItem;
import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.FutureResult;
import com.oracul.app.api.model.FutureStory;
import com.oracul.app.api.model.ResearchExplanation;
import com.oracul.app.api.model.ResultSource;
import com.oracul.app.api.model.RunStatus;
import com.oracul.app.api.model.SearchIntent;
import com.oracul.app.api.model.Source;
import com.oracul.app.api.model.StructuredScenario;
import com.oracul.app.common.ApiException;
import com.oracul.app.reasoning.ScenarioAttemptRepository;
import com.oracul.app.research.EvidencePackRepository;
import com.oracul.app.runs.GenerationRunRepository;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Assembles the FutureResult of a COMPLETED run from its snapshot (FR-23, FR-25). */
@Service
public class FutureResultService {

    static final List<String> LABELS = List.of("AI-GENERATED FUTURE SCENARIO", "POSSIBLE FUTURE — NOT CURRENT NEWS");

    private final GenerationRunRepository runs;
    private final FutureStoryRepository stories;
    private final ScenarioAttemptRepository attempts;
    private final EvidencePackRepository packs;

    FutureResultService(GenerationRunRepository runs, FutureStoryRepository stories,
                        ScenarioAttemptRepository attempts, EvidencePackRepository packs) {
        this.runs = runs;
        this.stories = stories;
        this.attempts = attempts;
        this.packs = packs;
    }

    FutureResult result(UUID runId, UUID sessionId) {
        var run = (sessionId == null ? java.util.Optional.<GenerationRunRepository.Row>empty()
            : runs.find(runId, sessionId))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Future not found"));
        FutureStory story = run.status() == RunStatus.COMPLETED ? stories.find(runId).orElse(null) : null;
        if (story == null || run.evidencePackId() == null) {
            throw notReady();
        }
        int finalAttempt = attempts.finalAttempt(runId).orElseThrow(FutureResultService::notReady);
        var accepted = attempts.list(runId).stream().filter(a -> a.attempt() == finalAttempt).findFirst()
            .orElseThrow(FutureResultService::notReady);
        StructuredScenario scenario = accepted.cleaned().orElseThrow(FutureResultService::notReady);
        List<com.oracul.app.api.model.CriticIssue> openIssues = accepted.criticReport()
            .filter(r -> r.getVerdict() == com.oracul.app.api.model.CriticVerdict.FAIL)
            .map(r -> new ArrayList<>(r.getIssues())).orElseGet(ArrayList::new);
        EvidencePack pack = packs.findById(run.evidencePackId()).orElseThrow(FutureResultService::notReady);

        Set<String> used = new HashSet<>();
        scenario.getFactsUsed().forEach(f -> used.addAll(f.getEvidenceIds()));
        Map<String, Source> bySource = new HashMap<>();
        pack.getSources().forEach(s -> bySource.put(s.getId(), s));
        List<EvidenceItem> items = new ArrayList<>();
        items.addAll(pack.getCore());
        items.addAll(pack.getSupporting());
        items.addAll(pack.getCounterSignals());
        items.sort(Comparator.comparingInt((EvidenceItem i) -> number(i.getEvidenceId()))
            .thenComparing(EvidenceItem::getEvidenceId));
        List<ResultSource> sources = new ArrayList<>();
        if (pack.getWildcardSections() != null) {
            Map<String, com.oracul.app.api.model.PackSourceItem> distinct = new java.util.TreeMap<>(
                Comparator.comparingInt(FutureResultService::number).thenComparing(Comparator.naturalOrder()));
            for (var section : pack.getWildcardSections()) {
                for (var item : section.getItems()) {
                    distinct.putIfAbsent(item.getEvidenceId(), item);
                }
            }
            for (var item : distinct.values()) {
                ResultSource rs = new ResultSource(item.getEvidenceId(),
                    com.oracul.app.api.model.EvidenceSection.CORE, item.getTitle(), item.getPublisher(),
                    item.getUrl(), used.contains(item.getEvidenceId()), false);
                rs.setPublishedAt(item.getPublishedAt());
                sources.add(rs);
            }
        }
        for (EvidenceItem item : items) {
            Source first = item.getSourceIds().isEmpty() ? null : bySource.get(item.getSourceIds().get(0));
            ResultSource rs = new ResultSource(item.getEvidenceId(), item.getSection(),
                first == null ? "" : first.getTitle(), first == null ? "" : first.getPublisher(),
                first == null ? URI.create("") : first.getUrl(), used.contains(item.getEvidenceId()),
                item.getSection() == com.oracul.app.api.model.EvidenceSection.COUNTER_SIGNAL);
            if (first != null) {
                rs.setPublishedAt(first.getPublishedAt());
            }
            sources.add(rs);
        }
        List<SearchIntent> intents = run.searchPlan() == null ? new ArrayList<>()
            : new ArrayList<>(run.searchPlan().getIntents());
        var metadata = ScenarioMetadataMapper.map(run.configuration(), run.counts());
        metadata.setModel(run.model());
        FutureResult result = new FutureResult(run.id(), run.generationId(), new ArrayList<>(LABELS), story,
            metadata,
            new ArrayList<>(scenario.getCausalChain()), sources, new ResearchExplanation(intents, run.counts()),
            openIssues);
        if (run.searchPlan() != null && run.searchPlan().getPipelines() != null
            && !run.searchPlan().getPipelines().isEmpty() && pack.getWildcardSections() != null
            && !pack.getWildcardSections().isEmpty()) {
            result.setWildcardGroups(groups(run.searchPlan().getPipelines(), pack.getWildcardSections(), used));
        }
        return result;
    }

    private static List<com.oracul.app.api.model.ResultWildcardGroup> groups(
        List<com.oracul.app.api.model.WildcardPipeline> pipelines,
        List<com.oracul.app.api.model.PackWildcardSection> sections, Set<String> used) {
        List<com.oracul.app.api.model.ResultWildcardGroup> groups = new ArrayList<>();
        for (var p : pipelines) {
            List<com.oracul.app.api.model.ResultGroupSource> list = new ArrayList<>();
            for (var section : sections) {
                if (!p.getId().equals(section.getPipelineId()) || section.getItems() == null) {
                    continue;
                }
                for (var item : section.getItems()) {
                    var g = new com.oracul.app.api.model.ResultGroupSource(item.getEvidenceId(), item.getSourceId(),
                        item.getTitle(), item.getPublisher(), item.getUrl(), item.getContentRetrieved(),
                        item.getFragments() == null ? new ArrayList<>() : new ArrayList<>(item.getFragments()),
                        used.contains(item.getEvidenceId()));
                    g.setPublishedAt(item.getPublishedAt());
                    list.add(g);
                }
            }
            var group = new com.oracul.app.api.model.ResultWildcardGroup(p.getId(), p.getKind(), p.getLabel(),
                p.getHeading(), new ArrayList<>(p.getQueries()), list);
            group.setLevel(p.getLevel());
            groups.add(group);
        }
        return groups;
    }

    private static int number(String evidenceId) {
        String digits = evidenceId.replaceAll("\\D", "");
        return digits.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(digits);
    }

    private static ApiException notReady() {
        return new ApiException(HttpStatus.CONFLICT, "RESULT_NOT_READY", "This future is not ready yet");
    }
}
