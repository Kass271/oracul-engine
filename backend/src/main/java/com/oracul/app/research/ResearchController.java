package com.oracul.app.research;

import com.oracul.app.api.ResearchApi;
import com.oracul.app.api.model.EventList;
import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.RunResearch;
import com.oracul.app.api.model.SourceList;
import com.oracul.app.common.ApiException;
import com.oracul.app.runs.GenerationRunRepository;
import com.oracul.app.session.CurrentSession;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResearchController implements ResearchApi {

    private final ObjectProvider<GenerationRunRepository> runs;
    private final ObjectProvider<CurrentSession> session;
    private final ObjectProvider<SourceRepository> sources;
    private final ObjectProvider<EventRepository> events;
    private final ObjectProvider<EvidencePackRepository> packs;

    ResearchController(ObjectProvider<GenerationRunRepository> runs, ObjectProvider<CurrentSession> session,
                       ObjectProvider<SourceRepository> sources,
                       ObjectProvider<EventRepository> events, ObjectProvider<EvidencePackRepository> packs) {
        this.packs = packs;
        this.events = events;
        this.sources = sources;
        this.runs = runs;
        this.session = session;
    }

    @Override
    public ResponseEntity<RunResearch> getRunResearch(UUID runId) {
        var run = runs.getObject().find(runId, session.getObject().id())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Future not found"));
        if (run.researchProfile() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "RESEARCH_NOT_READY", "Research has not started yet");
        }
        return ResponseEntity.ok(new RunResearch(run.id(), run.researchProfile(), run.counts()).searchPlan(run.searchPlan()));
    }

    @Override
    public ResponseEntity<EvidencePack> getEvidencePack(UUID runId) {
        var run = runs.getObject().find(runId, session.getObject().id())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Future not found"));
        if (run.evidencePackId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "EVIDENCE_PACK_NOT_READY", "The Evidence Pack is not ready yet");
        }
        return ResponseEntity.ok(packs.getObject().findById(run.evidencePackId())
            .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "EVIDENCE_PACK_NOT_READY",
                "The Evidence Pack is not ready yet")));
    }

    @Override
    public ResponseEntity<EventList> listRunEvents(UUID runId) {
        var run = runs.getObject().find(runId, session.getObject().id())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Future not found"));
        return ResponseEntity.ok(new EventList(events.getObject().list(owner(run))));
    }

    @Override
    public ResponseEntity<SourceList> listRunSources(UUID runId) {
        var run = runs.getObject().find(runId, session.getObject().id())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Future not found"));
        return ResponseEntity.ok(new SourceList(sources.getObject().list(owner(run))));
    }

    private UUID owner(GenerationRunRepository.Row run) {
        if (run.evidencePackId() == null) {
            return run.id();
        }
        return packs.getObject().ownerRunId(run.evidencePackId()).orElse(run.id());
    }
}
