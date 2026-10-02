package com.oracul.app.runs;

import com.oracul.app.api.RunsApi;
import com.oracul.app.api.model.GenerationRun;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RunsController implements RunsApi {

    @Override
    public ResponseEntity<GenerationRun> startRun(ScenarioConfiguration scenarioConfiguration) {
        // Body is validated by bean validation; no ChatGPT connection can exist before slice 02.
        throw new ApiException(HttpStatus.UNAUTHORIZED, "CHATGPT_NOT_CONNECTED", "Connect ChatGPT to generate");
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
