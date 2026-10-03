package com.oracul.app.reasoning;

import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.GuardOutcome;
import com.oracul.app.api.model.GuardReport;
import com.oracul.app.api.model.ResearchCounts;
import com.oracul.app.api.model.RunStage;
import com.oracul.app.api.model.ScenarioAttemptReason;
import com.oracul.app.api.model.StructuredScenario;
import com.oracul.app.chatgpt.CallAbandonedException;
import com.oracul.app.chatgpt.ChatGptCallException;
import com.oracul.app.chatgpt.HttpResponsesClient;
import com.oracul.app.research.EvidencePackRepository;
import com.oracul.app.runs.GenerationRunRepository;
import com.oracul.app.runs.RunGuard;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Stages 7-9 of the generation pipeline: scenario generation, Evidence Guard, acceptance (FR-19, FR-20, FR-21). */
@Component
public class ReasoningPipeline {

    /** How the stages ended. */
    public enum Result {
        /** The run was ended (FAILED) or abandoned; later stages must not run. */
        STOPPED,
        /** The pack is empty: no scenario was generated, stages 8-10 stay placeholders. */
        EMPTY_PACK,
        /** A scenario was accepted; stage 10 follows. */
        ACCEPTED
    }

    static final String INVALID_MESSAGE = "ORACUL could not construct a valid scenario";
    static final String REJECTED_MESSAGE = "ORACUL could not construct a scenario supported by current evidence";
    private static final String PURPOSE = "SCENARIO_GENERATION";

    private final GenerationRunRepository runs;
    private final EvidencePackRepository packs;
    private final ScenarioAttemptRepository attempts;
    private final ModelCallRepository modelCalls;
    private final HttpResponsesClient responses;
    private final RunGuard guard;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration minStageDuration;

    ReasoningPipeline(GenerationRunRepository runs, EvidencePackRepository packs, ScenarioAttemptRepository attempts,
                      ModelCallRepository modelCalls, HttpResponsesClient responses, RunGuard guard,
                      TransactionTemplate tx, Clock clock,
                      @Value("${oracul.run.min-stage-duration:PT0S}") Duration minStageDuration) {
        this.runs = runs;
        this.packs = packs;
        this.attempts = attempts;
        this.modelCalls = modelCalls;
        this.responses = responses;
        this.guard = guard;
        this.tx = tx;
        this.clock = clock;
        this.minStageDuration = minStageDuration;
    }

    /** An attempt that was called and stored. */
    private record Called(int attempt, StructuredScenarioParser.ParseResult parse) {
        boolean valid() {
            return parse.scenario().isPresent();
        }
    }

    /** Thrown to unwind when a guarded commit finds the run no longer RUNNING. */
    private static final class Stop extends RuntimeException {
        Stop() {
            super("run stopped", null, false, false);
        }
    }

    public Result run(UUID runId, UUID sessionId) throws InterruptedException {
        try {
            return execute(runId, sessionId);
        } catch (ChatGptCallException e) {
            runs.markFailed(runId, e.code(), e.getMessage(), now());
            return Result.STOPPED;
        } catch (CallAbandonedException | Stop e) {
            runs.failTimedOut(runId, now());
            return Result.STOPPED;
        }
    }

    private Result execute(UUID runId, UUID sessionId) throws InterruptedException {
        var row = runs.find(runId, sessionId).orElseThrow();
        EvidencePack pack = packs.find(runId, row.evidencePackId()).orElseThrow();

        long started = begin(runId, RunStage.EXPLORING_FUTURES);
        if (pack.getCore().isEmpty() && pack.getSupporting().isEmpty() && pack.getCounterSignals().isEmpty()) {
            return Result.EMPTY_PACK;
        }
        boolean schemaUsed = false;
        Called current = call(runId, sessionId, pack,
            new GenerationRequest(1, ScenarioAttemptReason.INITIAL, List.of(), List.of()));
        if (!current.valid()) {
            current = call(runId, sessionId, pack, new GenerationRequest(2, ScenarioAttemptReason.SCHEMA_CORRECTION,
                current.parse().errors(), List.of()));
            schemaUsed = true;
            if (!current.valid()) {
                fail(runId, "INVALID_SCENARIO", INVALID_MESSAGE);
                return Result.STOPPED;
            }
        }
        remainder(started);

        started = begin(runId, RunStage.CHALLENGING_ASSUMPTIONS);
        EvidenceGuard.GuardResult checked = EvidenceGuard.check(current.parse().scenario().get(), pack,
            current.attempt(), false);
        storeGuard(runId, current.attempt(), checked, null);
        remainder(started);

        started = begin(runId, RunStage.CONSTRUCTING_SCENARIO);
        if (checked.report().getOutcome() == GuardOutcome.FAIL) {
            List<com.oracul.app.api.model.GuardViolation> violations = checked.report().getViolations();
            int next = current.attempt() + 1;
            current = call(runId, sessionId, pack,
                new GenerationRequest(next, ScenarioAttemptReason.GUARD_REGENERATION, List.of(), violations));
            if (!current.valid()) {
                if (schemaUsed) {
                    fail(runId, "INVALID_SCENARIO", INVALID_MESSAGE);
                    return Result.STOPPED;
                }
                current = call(runId, sessionId, pack, new GenerationRequest(next + 1,
                    ScenarioAttemptReason.SCHEMA_CORRECTION, current.parse().errors(), violations));
                if (!current.valid()) {
                    fail(runId, "INVALID_SCENARIO", INVALID_MESSAGE);
                    return Result.STOPPED;
                }
            }
            checked = EvidenceGuard.check(current.parse().scenario().get(), pack, current.attempt(), true);
            if (checked.report().getOutcome() == GuardOutcome.FAIL) {
                storeGuard(runId, current.attempt(), checked,
                    new Failure("SCENARIO_REJECTED", REJECTED_MESSAGE));
                return Result.STOPPED;
            }
            storeGuard(runId, current.attempt(), checked, null);
        }
        accept(runId, sessionId, current.attempt(), checked.cleaned());
        remainder(started);
        return Result.ACCEPTED;
    }

    // ---- calls ------------------------------------------------------------------------------------------------------

    private Called call(UUID runId, UUID sessionId, EvidencePack pack, GenerationRequest req) {
        var body = ScenarioGenerationPrompt.body(responses.model(), pack, req);
        Long[] callId = new Long[1];
        Runnable beforeSend = () -> {
            if (!guard.check(runId)) {
                throw new CallAbandonedException();
            }
            if (callId[0] == null) {
                callId[0] = modelCalls.insert(runId, PURPOSE, req.attempt(), body, now());
            }
        };
        Optional<String> text = responses.createTextOrThrow(sessionId, body, beforeSend, status -> {
            if (callId[0] != null) {
                modelCalls.setStatus(callId[0], status);
            }
        });
        StructuredScenarioParser.ParseResult parsed = StructuredScenarioParser.parse(text);
        Boolean stored = tx.execute(s -> {
            if (!guard.lockAndCheck(runId)) {
                s.setRollbackOnly();
                return false;
            }
            attempts.insert(runId, req.attempt(), req.reason(), parsed.scenario(), parsed.errors(), now());
            return true;
        });
        if (!Boolean.TRUE.equals(stored)) {
            throw new Stop();
        }
        return new Called(req.attempt(), parsed);
    }

    private record Failure(String code, String message) {
    }

    private void storeGuard(UUID runId, int attempt, EvidenceGuard.GuardResult result, Failure failure) {
        GuardReport report = result.report();
        Boolean stored = tx.execute(s -> {
            if (!guard.lockAndCheck(runId)) {
                s.setRollbackOnly();
                return false;
            }
            attempts.storeGuard(runId, attempt, report, result.cleaned());
            if (failure != null) {
                runs.markFailed(runId, failure.code(), failure.message(), now());
            }
            return true;
        });
        if (!Boolean.TRUE.equals(stored)) {
            throw new Stop();
        }
    }

    private void fail(UUID runId, String code, String message) {
        Boolean failed = tx.execute(s -> {
            if (!guard.lockAndCheck(runId)) {
                s.setRollbackOnly();
                return false;
            }
            runs.markFailed(runId, code, message, now());
            return true;
        });
        if (!Boolean.TRUE.equals(failed)) {
            throw new Stop();
        }
    }

    private void accept(UUID runId, UUID sessionId, int attempt, StructuredScenario cleaned) {
        Set<String> used = new LinkedHashSet<>();
        cleaned.getFactsUsed().forEach(f -> used.addAll(f.getEvidenceIds()));
        Boolean accepted = tx.execute(s -> {
            if (!guard.lockAndCheck(runId)) {
                s.setRollbackOnly();
                return false;
            }
            ResearchCounts counts = runs.find(runId, sessionId).orElseThrow().counts();
            counts.setSourcesUsed(used.size());
            return runs.storeAccepted(runId, attempt, counts, now());
        });
        if (!Boolean.TRUE.equals(accepted)) {
            throw new Stop();
        }
    }

    // ---- stages -----------------------------------------------------------------------------------------------------

    private long begin(UUID runId, RunStage stage) {
        Boolean ok = tx.execute(s -> {
            if (!guard.lockAndCheck(runId)) {
                s.setRollbackOnly();
                return false;
            }
            return runs.markStageIfRunning(runId, stage, now());
        });
        if (!Boolean.TRUE.equals(ok)) {
            throw new Stop();
        }
        return System.nanoTime();
    }

    private void remainder(long startedNanos) throws InterruptedException {
        if (minStageDuration.isZero() || minStageDuration.isNegative()) {
            return;
        }
        long left = minStageDuration.toNanos() - (System.nanoTime() - startedNanos);
        if (left > 0) {
            Thread.sleep(Duration.ofNanos(left));
        }
    }

    private OffsetDateTime now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }
}
