package com.oracul.app.reasoning;

import com.oracul.app.api.ReasoningApi;
import com.oracul.app.api.model.GuardOutcome;
import com.oracul.app.api.model.GuardReport;
import com.oracul.app.api.model.ScenarioAttemptSummary;
import com.oracul.app.api.model.StructuredScenario;
import com.oracul.app.api.model.StructuredScenarioRecord;
import com.oracul.app.common.ApiException;
import com.oracul.app.runs.GenerationRunRepository;
import com.oracul.app.session.CurrentSession;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReasoningController implements ReasoningApi {

    private final ObjectProvider<CurrentSession> session;
    private final ObjectProvider<GenerationRunRepository> runRepository;
    private final ObjectProvider<ScenarioAttemptRepository> attemptRepository;

    ReasoningController(ObjectProvider<CurrentSession> session, ObjectProvider<GenerationRunRepository> runs,
                        ObjectProvider<ScenarioAttemptRepository> attempts) {
        this.session = session;
        this.runRepository = runs;
        this.attemptRepository = attempts;
    }

    @Override
    public ResponseEntity<StructuredScenarioRecord> getStructuredScenario(UUID runId) {
        GenerationRunRepository runs = runRepository.getObject();
        ScenarioAttemptRepository attempts = attemptRepository.getObject();
        CurrentSession current = session.getIfAvailable();
        var run = current == null ? java.util.Optional.<GenerationRunRepository.Row>empty()
            : runs.find(runId, current.id());
        if (run.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Future not found");
        }
        List<ScenarioAttemptRepository.Attempt> rows = attempts.list(runId);
        ScenarioAttemptRepository.Attempt latest = null;
        for (var a : rows) {
            if (a.scenario().isPresent()) {
                latest = a;
            }
        }
        if (latest == null) {
            throw new ApiException(HttpStatus.CONFLICT, "SCENARIO_NOT_READY", "The scenario is not ready yet");
        }
        boolean passed = latest.guardReport().map(GuardReport::getOutcome)
            .filter(o -> o == GuardOutcome.PASS || o == GuardOutcome.PASS_WITH_REMOVALS).isPresent();
        StructuredScenario shown = passed && latest.cleaned().isPresent() ? latest.cleaned().get()
            : latest.scenario().get();
        List<GuardReport> reports = new ArrayList<>();
        List<com.oracul.app.api.model.CriticReport> critics = new ArrayList<>();
        List<ScenarioAttemptSummary> summaries = new ArrayList<>();
        for (var a : rows) {
            a.guardReport().ifPresent(reports::add);
            a.criticReport().ifPresent(critics::add);
            summaries.add(new ScenarioAttemptSummary(a.attempt(), a.reason(), a.scenario().isPresent(),
                new ArrayList<>(a.schemaErrors())));
        }
        boolean accepted = attempts.finalAttempt(runId).filter(f -> f == latestAttempt(rows)).isPresent();
        return ResponseEntity.ok(new StructuredScenarioRecord(runId, run.get().evidencePackId(), latest.attempt(),
            accepted, shown, reports, critics, rows.size(), summaries));
    }

    private static int latestAttempt(List<ScenarioAttemptRepository.Attempt> rows) {
        int out = 0;
        for (var a : rows) {
            if (a.scenario().isPresent()) {
                out = a.attempt();
            }
        }
        return out;
    }
}
