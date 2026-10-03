package com.oracul.app.result;

import com.oracul.app.api.ResultApi;
import com.oracul.app.api.model.FutureResult;
import com.oracul.app.session.CurrentSession;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResultController implements ResultApi {

    private final ObjectProvider<CurrentSession> session;
    private final ObjectProvider<FutureResultService> service;

    ResultController(ObjectProvider<CurrentSession> session, ObjectProvider<FutureResultService> service) {
        this.session = session;
        this.service = service;
    }

    @Override
    public ResponseEntity<FutureResult> getFutureResult(UUID runId) {
        CurrentSession current = session.getIfAvailable();
        return ResponseEntity.ok(service.getObject().result(runId, current == null ? null : current.id()));
    }
}
