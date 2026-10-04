package com.oracul.app.reasoning;

import com.oracul.app.api.model.CriticIssue;
import com.oracul.app.api.model.CriticReport;
import com.oracul.app.api.model.CriticVerdict;
import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.GuardOutcome;
import com.oracul.app.api.model.GuardReport;
import com.oracul.app.api.model.GuardViolation;
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

    static final String INVALID_MESSAGE = com.oracul.app.runs.RunFailures.message(com.oracul.app.api.model.RunFailureCode.INVALID_SCENARIO);
    static final String REJECTED_MESSAGE = com.oracul.app.runs.RunFailures.message(com.oracul.app.api.model.RunFailureCode.SCENARIO_REJECTED);
    private static final String PURPOSE = "SCENARIO_GENERATION";

    private final GenerationRunRepository runs;
    private final EvidencePackRepository packs;
    private final ScenarioAttemptRepository attempts;
    private final ModelCallRepository modelCalls;
    private final AvoidedFutures avoidedFutures;
    private final HttpResponsesClient responses;
    private final ScenarioCritic critic;
    private final RunGuard guard;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration minStageDuration;

    ReasoningPipeline(GenerationRunRepository runs, EvidencePackRepository packs, ScenarioAttemptRepository attempts,
                      ModelCallRepository modelCalls, AvoidedFutures avoidedFutures, HttpResponsesClient responses, ScenarioCritic critic,
                      RunGuard guard,
                      TransactionTemplate tx, Clock clock,
                      @Value("${oracul.run.min-stage-duration:PT0S}") Duration minStageDuration) {
        this.runs = runs;
        this.packs = packs;
        this.attempts = attempts;
        this.modelCalls = modelCalls;
        this.avoidedFutures = avoidedFutures;
        this.responses = responses;
        this.critic = critic;
        this.guard = guard;
        this.tx = tx;
        this.clock = clock;
        this.minStageDuration = minStageDuration;
    }

    /** An attempt that was called and stored. */
    private record Called(int attempt, ScenarioAttemptReason reason, StructuredScenarioParser.ParseResult parse) {
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
        EvidencePack pack = packs.findById(row.evidencePackId()).orElseThrow();

        long started = begin(runId, RunStage.EXPLORING_FUTURES);
        if (pack.getCore().isEmpty() && pack.getSupporting().isEmpty() && pack.getCounterSignals().isEmpty()) {
            return Result.EMPTY_PACK;
        }
        State st = new State();
        boolean alternative = row.kind() == com.oracul.app.api.model.RunKind.ALTERNATIVE;
        List<AvoidedFutures.AvoidedFuture> avoided = alternative ? avoidedFutures.load(runId) : List.of();
        ScenarioGenerationPrompt.Alternative base = alternative
            ? new ScenarioGenerationPrompt.Alternative(avoided, null, List.of()) : ScenarioGenerationPrompt.Alternative.NONE;
        st.base = base;
        Called current = call(runId, sessionId, pack, new GenerationRequest(++st.attempt,
            ScenarioAttemptReason.INITIAL, List.of(), List.of(), List.of()), base);
        if (!current.valid()) {
            st.schemaUsed = true;
            current = call(runId, sessionId, pack, new GenerationRequest(++st.attempt,
                ScenarioAttemptReason.SCHEMA_CORRECTION, current.parse().errors(), List.of(), List.of()), base);
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
        CriticReport report = null;
        if (passed(checked)) {
            report = critique(runId, sessionId, pack, current, checked);
        }
        remainder(started);

        started = begin(runId, RunStage.CONSTRUCTING_SCENARIO);
        while (true) {
            if (!passed(checked)) {
                st.guardUsed = true;
                current = regenerate(runId, sessionId, pack, st, ScenarioAttemptReason.GUARD_REGENERATION,
                    checked.report().getViolations(), List.of());
                if (current == null) {
                    return Result.STOPPED;
                }
                checked = checkNew(runId, pack, current, true);
                if (checked == null) {
                    return Result.STOPPED;
                }
                report = null;
                continue;
            }
            if (report == null) {
                report = critique(runId, sessionId, pack, current, checked);
            }
            if (report.getVerdict() == CriticVerdict.PASS || st.criticUsed) {
                if (!alternative) {
                    break;
                }
                List<String> findings = AlternativeDistinctness.findings(checked.cleaned(), avoided);
                if (findings.isEmpty()) {
                    break;
                }
                if (st.distinctUsed) {
                    fail(runId, "ALTERNATIVE_NOT_DISTINCT", com.oracul.app.runs.RunFailures.message(
                        com.oracul.app.api.model.RunFailureCode.ALTERNATIVE_NOT_DISTINCT));
                    return Result.STOPPED;
                }
                st.distinctUsed = true;
                String rejected = checked.cleaned().getFutureEvent().getTitle();
                current = regenerate(runId, sessionId, pack, st, ScenarioAttemptReason.ALTERNATIVE_DISTINCT,
                    List.of(), List.of(), new ScenarioGenerationPrompt.Alternative(avoided, rejected, findings));
                if (current == null) {
                    return Result.STOPPED;
                }
                checked = checkNew(runId, pack, current, st.guardUsed);
                if (checked == null) {
                    return Result.STOPPED;
                }
                report = null;
                continue;
            }
            st.criticUsed = true;
            current = regenerate(runId, sessionId, pack, st, ScenarioAttemptReason.CRITIC_REGENERATION, List.of(),
                report.getIssues());
            if (current == null) {
                return Result.STOPPED;
            }
            checked = checkNew(runId, pack, current, st.guardUsed);
            if (checked == null) {
                return Result.STOPPED;
            }
            report = null;
        }
        accept(runId, sessionId, current.attempt(), checked.cleaned());
        remainder(started);
        return Result.ACCEPTED;
    }

    /** Mutable bookkeeping of one run: call counter and the one-shot budgets. */
    private static final class State {
        int attempt;
        boolean schemaUsed;
        boolean guardUsed;
        boolean criticUsed;
        boolean distinctUsed;
        ScenarioGenerationPrompt.Alternative base = ScenarioGenerationPrompt.Alternative.NONE;
    }

    private static boolean passed(EvidenceGuard.GuardResult r) {
        return r.report().getOutcome() != GuardOutcome.FAIL;
    }

    /** A regeneration call with at most one schema correction; null when the run was failed INVALID_SCENARIO. */
    private Called regenerate(UUID runId, UUID sessionId, EvidencePack pack, State st, ScenarioAttemptReason reason,
                              List<GuardViolation> violations, List<CriticIssue> issues) {
        return regenerate(runId, sessionId, pack, st, reason, violations, issues, st.base);
    }

    private Called regenerate(UUID runId, UUID sessionId, EvidencePack pack, State st, ScenarioAttemptReason reason,
                              List<GuardViolation> violations, List<CriticIssue> issues,
                              ScenarioGenerationPrompt.Alternative alt) {
        Called c = call(runId, sessionId, pack, new GenerationRequest(++st.attempt, reason, List.of(), violations,
            issues), alt);
        if (c.valid()) {
            return c;
        }
        if (!st.schemaUsed) {
            st.schemaUsed = true;
            c = call(runId, sessionId, pack, new GenerationRequest(++st.attempt,
                ScenarioAttemptReason.SCHEMA_CORRECTION, c.parse().errors(), violations, issues), alt);
            if (c.valid()) {
                return c;
            }
        }
        fail(runId, "INVALID_SCENARIO", INVALID_MESSAGE);
        return null;
    }

    /** Guards a regenerated attempt; a FAIL of a final guard ends the run SCENARIO_REJECTED (returns null). */
    private EvidenceGuard.GuardResult checkNew(UUID runId, EvidencePack pack, Called c, boolean finalAttempt) {
        EvidenceGuard.GuardResult checked = EvidenceGuard.check(c.parse().scenario().get(), pack, c.attempt(),
            finalAttempt);
        if (finalAttempt && !passed(checked)) {
            storeGuard(runId, c.attempt(), checked, new Failure("SCENARIO_REJECTED", REJECTED_MESSAGE));
            return null;
        }
        storeGuard(runId, c.attempt(), checked, null);
        return checked;
    }

    private CriticReport critique(UUID runId, UUID sessionId, EvidencePack pack, Called c,
                                  EvidenceGuard.GuardResult checked) {
        CriticReport report = critic.critique(runId, sessionId, pack, c.attempt(), c.reason(), checked.cleaned());
        Boolean stored = tx.execute(s -> {
            if (!guard.lockAndCheck(runId)) {
                s.setRollbackOnly();
                return false;
            }
            attempts.storeCritic(runId, c.attempt(), report);
            return true;
        });
        if (!Boolean.TRUE.equals(stored)) {
            throw new Stop();
        }
        return report;
    }

    // ---- calls ------------------------------------------------------------------------------------------------------

    private Called call(UUID runId, UUID sessionId, EvidencePack pack, GenerationRequest req,
                        ScenarioGenerationPrompt.Alternative alt) {
        var body = ScenarioGenerationPrompt.body(responses.model(), pack, req, alt);
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
        return new Called(req.attempt(), req.reason(), parsed);
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
