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
    private final ObjectProvider<com.oracul.app.style.WritingStyles> styles;

    private ObjectProvider<com.oracul.app.style.WritingStyles> styles() {
        return styles;
    }

    RunsController(ObjectProvider<ChatGptAuthService> auth, ObjectProvider<CurrentSession> session,
                   ObjectProvider<RunService> runs, ObjectProvider<com.oracul.app.style.WritingStyles> styles) {
        this.styles = styles;
        this.runs = runs;
        this.auth = auth;
        this.session = session;
    }

    @Override
    public ResponseEntity<GenerationRun> startRun(ScenarioConfiguration scenarioConfiguration) {
        com.oracul.app.scenario.WildcardRules.firstViolation(scenarioConfiguration).ifPresent(m -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", m);
        });
        if (scenarioConfiguration.getOutput() instanceof com.oracul.app.scenario.ParsedOutputSettings parsed) {
            if (parsed.styleInvalid() || !styleKnown(parsed.getWritingStyle())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Unknown writing style");
            }
            if (parsed.languageInvalid()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Unknown story language");
            }
        }
        // Body is validated by bean validation first, then the ChatGPT connection is checked.
        ChatGptAuthService service = auth.getIfAvailable();
        CurrentSession current = session.getIfAvailable();
        if (service == null || current == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CHATGPT_NOT_CONNECTED", "Connect ChatGPT to generate");
        }
        service.requireUsableCredentials(current.id());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(runs.getObject().start(current.id(), scenarioConfiguration));
    }

    private boolean styleKnown(com.oracul.app.api.model.WritingStyleSelection sel) {
        if (sel == null) return true;
        com.oracul.app.style.WritingStyles styles = styles().getIfAvailable();
        if (styles == null) return false;
        return styles.find(sel.getProfileId())
            .filter(p -> p.mode().name().equals(sel.getMode().getValue())).isPresent();
    }

    @Override
    public ResponseEntity<GenerationRun> startAlternativeRun(java.util.UUID runId) {
        CurrentSession current = session.getIfAvailable();
        RunService service = runs.getObject();
        if (current == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Future not found");
        }
        service.get(runId, current.id()); // 404 for unknown / foreign runs
        ChatGptAuthService chatGpt = auth.getIfAvailable();
        if (chatGpt == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CHATGPT_NOT_CONNECTED", "Connect ChatGPT to generate");
        }
        chatGpt.requireUsableCredentials(current.id());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.startAlternative(current.id(), runId));
    }

    @Override
    public ResponseEntity<GenerationRun> getRun(java.util.UUID runId) {
        return ResponseEntity.ok(runs.getObject().get(runId, session.getObject().id()));
    }

    @Override
    public ResponseEntity<GenerationRun> stopRun(java.util.UUID runId) {
        CurrentSession current = session.getIfAvailable();
        if (current == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Future not found");
        }
        return ResponseEntity.ok(runs.getObject().stop(runId, current.id()));
    }
}
