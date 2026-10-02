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

    RunsController(ObjectProvider<ChatGptAuthService> auth, ObjectProvider<CurrentSession> session) {
        this.auth = auth;
        this.session = session;
    }

    @Override
    public ResponseEntity<GenerationRun> startRun(ScenarioConfiguration scenarioConfiguration) {
        // Body is validated by bean validation first, then the ChatGPT connection is checked.
        ChatGptAuthService service = auth.getIfAvailable();
        CurrentSession current = session.getIfAvailable();
        if (service == null || current == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CHATGPT_NOT_CONNECTED", "Connect ChatGPT to generate");
        }
        service.requireUsableCredentials(current.id());
        // Interim until slice 04 starts the pipeline.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<GenerationRun> startAlternativeRun(java.util.UUID runId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<GenerationRun> getRun(java.util.UUID runId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
