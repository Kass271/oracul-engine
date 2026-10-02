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

    ResearchController(ObjectProvider<GenerationRunRepository> runs, ObjectProvider<CurrentSession> session) {
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
        return ResponseEntity.ok(new RunResearch(run.id(), run.researchProfile(), run.counts()));
    }

    @Override
    public ResponseEntity<EvidencePack> getEvidencePack(UUID runId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<EventList> listRunEvents(UUID runId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<SourceList> listRunSources(UUID runId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
