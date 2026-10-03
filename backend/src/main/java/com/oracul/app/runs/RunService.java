package com.oracul.app.runs;

import com.oracul.app.api.model.CustomWildcard;
import com.oracul.app.api.model.GenerationRun;
import com.oracul.app.api.model.RunFailure;
import com.oracul.app.api.model.RunFailureCode;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RunService {

    private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm");
    private static final int ID_ATTEMPTS = 20;

    private final GenerationRunRepository runs;
    private final PipelineExecutor pipeline;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration timeout;

    RunService(GenerationRunRepository runs, PipelineExecutor pipeline, TransactionTemplate tx, Clock clock,
               @Value("${oracul.run.timeout:PT3M}") Duration timeout) {
        this.runs = runs;
        this.pipeline = pipeline;
        this.tx = tx;
        this.clock = clock;
        this.timeout = timeout;
    }

    public GenerationRun start(UUID sessionId, ScenarioConfiguration requested) {
        ScenarioConfiguration cfg = snapshot(requested);
        OffsetDateTime now = clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
        UUID id = UUID.randomUUID();
        for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
            try {
                tx.executeWithoutResult(status -> {
                    if (runs.hasActiveRun(sessionId)) {
                        throw activeConflict();
                    }
                    runs.insertQueued(id, nextGenerationId(now), sessionId, cfg, now, now.plus(timeout));
                });
                break;
            } catch (DuplicateKeyException e) {
                String message = String.valueOf(e.getMessage());
                if (message.contains("one_active_run_per_session")) {
                    throw activeConflict();
                }
                if (attempt == ID_ATTEMPTS - 1) {
                    throw e;
                }
            }
        }
        GenerationRun created = toApi(runs.find(id, sessionId).orElseThrow());
        pipeline.submit(id, sessionId);
        return created;
    }

    public GenerationRun get(UUID id, UUID sessionId) {
        return toApi(runs.find(id, sessionId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Future not found")));
    }

    private String nextGenerationId(OffsetDateTime now) {
        String base = "ORC-" + ID_FORMAT.format(now);
        String candidate = base;
        for (int n = 2; runs.generationIdExists(candidate); n++) {
            candidate = base + "-" + n;
        }
        return candidate;
    }

    private static ApiException activeConflict() {
        return new ApiException(HttpStatus.CONFLICT, "RUN_ALREADY_ACTIVE", "A generation is already running");
    }

    private static ScenarioConfiguration snapshot(ScenarioConfiguration c) {
        var custom = new ArrayList<CustomWildcard>();
        for (CustomWildcard w : c.getCustomWildcards()) {
            custom.add(new CustomWildcard(w.getLabel().trim(), w.getIntensity()));
        }
        return new ScenarioConfiguration(c.getRealism(), c.getDarkness(), c.getOptimism(), c.getHorizon(),
            new ArrayList<>(c.getWildcards()), custom, c.getOutput());
    }

    static GenerationRun toApi(GenerationRunRepository.Row r) {
        GenerationRun run = new GenerationRun();
        run.setId(r.id());
        run.setGenerationId(r.generationId());
        run.setKind(r.kind());
        run.setParentRunId(r.parentRunId());
        run.setStatus(r.status());
        run.setStage(r.stage());
        run.setStageLabel(r.stage() == null ? null : RunStages.label(r.stage()));
        run.setStageIndex(r.stage() == null ? 0 : RunStages.index(r.stage()));
        run.setStageCount(RunStages.COUNT);
        run.setConfiguration(r.configuration());
        run.setCounts(r.counts());
        run.setEvidencePackId(r.evidencePackId());
        if (r.failureCode() != null) {
            run.setFailure(new RunFailure(RunFailureCode.fromValue(r.failureCode()), r.failureMessage()));
        }
        run.setSuggestedRealism(r.suggestedRealism());
        run.setHeadline(r.headline());
        run.setCreatedAt(r.createdAt());
        run.setUpdatedAt(r.updatedAt());
        run.setCompletedAt(r.completedAt());
        return run;
    }
}
