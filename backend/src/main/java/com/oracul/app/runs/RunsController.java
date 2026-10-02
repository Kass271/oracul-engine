package com.oracul.app.runs;

import com.oracul.app.api.RunsApi;
import com.oracul.app.api.model.GenerationRun;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.chatgpt.ChatGptAuthService;
import com.oracul.app.common.ApiException;
import com.oracul.app.session.CurrentSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RunsController implements RunsApi {

    private final ObjectProvider<ChatGptAuthService> auth;
    private final ObjectProvider<CurrentSession> session;

    private final ObjectProvider<RunService> runs;

    RunsController(ObjectProvider<ChatGptAuthService> auth, ObjectProvider<CurrentSession> session,
                   ObjectProvider<RunService> runs) {
        this.runs = runs;
        this.auth = auth;
        this.session = session;
    }

    @Override
    public ResponseEntity<GenerationRun> startRun(ScenarioConfiguration scenarioConfiguration) {
        com.oracul.app.scenario.WildcardRules.firstViolation(scenarioConfiguration.getWildcards()).ifPresent(m -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", m);
        });
        // Body is validated by bean validation first, then the ChatGPT connection is checked.
        ChatGptAuthService service = auth.getIfAvailable();
        CurrentSession current = session.getIfAvailable();
        if (service == null || current == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CHATGPT_NOT_CONNECTED", "Connect ChatGPT to generate");
        }
        service.requireUsableCredentials(current.id());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(runs.getObject().start(current.id(), scenarioConfiguration));
    }

    @Override
    public ResponseEntity<GenerationRun> startAlternativeRun(java.util.UUID runId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<GenerationRun> getRun(java.util.UUID runId) {
        return ResponseEntity.ok(runs.getObject().get(runId, session.getObject().id()));
    }
}
