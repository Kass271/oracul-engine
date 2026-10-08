package com.oracul.app.runs;

import com.oracul.app.api.RewriteApi;
import com.oracul.app.api.model.GenerationRun;
import com.oracul.app.api.model.RewriteRequest;
import com.oracul.app.chatgpt.ChatGptAuthService;
import com.oracul.app.common.ApiException;
import com.oracul.app.session.CurrentSession;
import com.oracul.app.style.WritingStyles;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** FR-67: POST /api/runs/{runId}/rewrites. */
@RestController
public class RewriteController implements RewriteApi {

    private final ObjectProvider<ChatGptAuthService> auth;
    private final ObjectProvider<CurrentSession> session;
    private final ObjectProvider<RunService> runs;
    private final ObjectProvider<WritingStyles> styles;

    RewriteController(ObjectProvider<ChatGptAuthService> auth, ObjectProvider<CurrentSession> session,
                      ObjectProvider<RunService> runs, ObjectProvider<WritingStyles> styles) {
        this.auth = auth;
        this.session = session;
        this.runs = runs;
        this.styles = styles;
    }

    @Override
    public ResponseEntity<GenerationRun> startRewriteRun(UUID runId, RewriteRequest request) {
        CurrentSession current = session.getIfAvailable();
        RunService service = runs.getObject();
        if (current == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Future not found");
        }
        service.get(runId, current.id()); // 404 for unknown / foreign runs
        boolean styleInvalid = !(request instanceof ParsedRewriteRequest parsed) || parsed.styleInvalid()
            || !styleKnown(request);
        if (styleInvalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Unknown writing style");
        }
        if (((ParsedRewriteRequest) request).languageInvalid()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Unknown story language");
        }
        ChatGptAuthService chatGpt = auth.getIfAvailable();
        if (chatGpt == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CHATGPT_NOT_CONNECTED", "Connect ChatGPT to generate");
        }
        chatGpt.requireUsableCredentials(current.id());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(service.startRewrite(current.id(), runId, request.getWritingStyle(), request.getLanguage()));
    }

    private boolean styleKnown(RewriteRequest request) {
        WritingStyles known = styles.getIfAvailable();
        var sel = request.getWritingStyle();
        if (known == null || sel == null) return false;
        return known.find(sel.getProfileId())
            .filter(p -> p.mode().name().equals(sel.getMode().getValue())).isPresent();
    }
}
