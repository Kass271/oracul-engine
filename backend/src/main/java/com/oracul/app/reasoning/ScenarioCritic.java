package com.oracul.app.reasoning;

import com.oracul.app.api.model.CriticReport;
import com.oracul.app.api.model.CriticVerdict;
import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.ScenarioAttemptReason;
import com.oracul.app.api.model.StructuredScenario;
import com.oracul.app.chatgpt.CallAbandonedException;
import com.oracul.app.chatgpt.HttpResponsesClient;
import com.oracul.app.runs.RunGuard;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** The SCENARIO_CRITIC call of one scenario attempt: one malformed retry, model_call rows (FR-22). */
@Component
public class ScenarioCritic {

    private static final Logger LOG = LoggerFactory.getLogger(ScenarioCritic.class);
    private static final String PURPOSE = "SCENARIO_CRITIC";

    private final HttpResponsesClient responses;
    private final ModelCallRepository modelCalls;
    private final RunGuard guard;
    private final Clock clock;

    ScenarioCritic(HttpResponsesClient responses, ModelCallRepository modelCalls, RunGuard guard, Clock clock) {
        this.responses = responses;
        this.modelCalls = modelCalls;
        this.guard = guard;
        this.clock = clock;
    }

    public CriticReport critique(UUID runId, UUID sessionId, EvidencePack pack, int attempt,
                                 ScenarioAttemptReason reason, StructuredScenario cleaned) {
        Map<String, Object> body = responses.prepare(sessionId,
            ScenarioCriticPrompt.body(responses.model(), pack, cleaned, attempt, reason));
        for (int i = 0; i < 2; i++) {
            CriticParser.ParseResult parsed = CriticParser.parse(send(runId, sessionId, attempt, body));
            if (parsed.critique().isPresent()) {
                var c = parsed.critique().get();
                return new CriticReport(c.verdict(), new ArrayList<>(c.issues()), attempt);
            }
        }
        LOG.warn("critic output malformed twice for run {} attempt {}", runId, attempt);
        return new CriticReport(CriticVerdict.PASS, new ArrayList<>(), attempt);
    }

    private Optional<String> send(UUID runId, UUID sessionId, int attempt, Map<String, Object> body) {
        Long[] callId = new Long[1];
        Runnable beforeSend = () -> {
            if (!guard.check(runId)) {
                throw new CallAbandonedException();
            }
            if (callId[0] == null) {
                callId[0] = modelCalls.insert(runId, PURPOSE, attempt, body, now());
            }
        };
        return responses.createTextOrThrow(sessionId, body, beforeSend, status -> {
            if (callId[0] != null) {
                modelCalls.setStatus(callId[0], status);
            }
        });
    }

    private OffsetDateTime now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }
}
