package com.oracul.app.result;

import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.FutureStory;
import com.oracul.app.api.model.RunStage;
import com.oracul.app.api.model.StructuredScenario;
import com.oracul.app.chatgpt.CallAbandonedException;
import com.oracul.app.chatgpt.ChatGptCallException;
import com.oracul.app.chatgpt.HttpResponsesClient;
import com.oracul.app.reasoning.ModelCallRepository;
import com.oracul.app.reasoning.ScenarioAttemptRepository;
import com.oracul.app.reasoning.ScenarioWindow;
import com.oracul.app.research.EvidencePackRepository;
import com.oracul.app.runs.GenerationRunRepository;
import com.oracul.app.runs.RunGuard;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Stage 10 of the generation pipeline: the STORY_WRITING call, validation with one correction, completion (FR-23). */
@Component
public class StoryWriter {

    static final String INVALID_MESSAGE = com.oracul.app.runs.RunFailures.message(com.oracul.app.api.model.RunFailureCode.INVALID_SCENARIO);
    private static final String PURPOSE = "STORY_WRITING";

    private final GenerationRunRepository runs;
    private final EvidencePackRepository packs;
    private final ScenarioAttemptRepository attempts;
    private final ModelCallRepository modelCalls;
    private final FutureStoryRepository stories;
    private final HttpResponsesClient responses;
    private final RunGuard guard;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration minStageDuration;

    StoryWriter(GenerationRunRepository runs, EvidencePackRepository packs, ScenarioAttemptRepository attempts,
                ModelCallRepository modelCalls, FutureStoryRepository stories, HttpResponsesClient responses,
                RunGuard guard, TransactionTemplate tx, Clock clock,
                @Value("${oracul.run.min-stage-duration:PT0S}") Duration minStageDuration) {
        this.runs = runs;
        this.packs = packs;
        this.attempts = attempts;
        this.modelCalls = modelCalls;
        this.stories = stories;
        this.responses = responses;
        this.guard = guard;
        this.tx = tx;
        this.clock = clock;
        this.minStageDuration = minStageDuration;
    }

    private static final class Stop extends RuntimeException {
        Stop() {
            super("run stopped", null, false, false);
        }
    }

    /** What a call produced: its output text and the parser's verdict. */
    private record Called(Optional<String> text, StoryParser.ParseResult parse) {
    }

    public void run(UUID runId, UUID sessionId) throws InterruptedException {
        try {
            execute(runId, sessionId);
        } catch (ChatGptCallException e) {
            runs.markFailed(runId, e.code(), e.getMessage(), e.providerCode(), now());
        } catch (CallAbandonedException | Stop e) {
            runs.failTimedOut(runId, now());
        }
    }

    private void execute(UUID runId, UUID sessionId) throws InterruptedException {
        var row = runs.find(runId, sessionId).orElseThrow();
        EvidencePack pack = packs.findById(row.evidencePackId()).orElseThrow();
        int finalAttempt = attempts.finalAttempt(runId).orElseThrow();
        StructuredScenario scenario = attempts.list(runId).stream()
            .filter(a -> a.attempt() == finalAttempt).findFirst().orElseThrow().cleaned().orElseThrow();
        ScenarioWindow window = ScenarioWindow.of(pack);

        long started = begin(runId);
        Called first = call(runId, sessionId, pack, scenario, window, new StoryRequest(1, List.of()));
        FutureStory story = first.parse().story().orElse(null);
        if (story == null) {
            Called second = call(runId, sessionId, pack, scenario, window,
                new StoryRequest(2, first.parse().errors()));
            story = second.parse().story().orElse(null);
            if (story == null) {
                if (!second.parse().onlyDateErrors()) {
                    fail(runId);
                    return;
                }
                var core = StoryParser.core(second.text(), window.cutoff(), window.end());
                var date = scenario.getFutureEvent().getDate();
                story = new FutureStory(core.headline(), Datelines.format(date), date, core.body());
            }
        }
        remainder(started);
        FutureStory done = story;
        Boolean stored = tx.execute(s -> {
            if (!guard.lockAndCheck(runId)) {
                s.setRollbackOnly();
                return false;
            }
            OffsetDateTime t = now();
            stories.insert(runId, done, t);
            return runs.markCompletedWithHeadline(runId, RunStage.WRITING_STORY, done.getHeadline(), t);
        });
        if (!Boolean.TRUE.equals(stored)) {
            throw new Stop();
        }
    }

    private Called call(UUID runId, UUID sessionId, EvidencePack pack, StructuredScenario scenario,
                        ScenarioWindow window, StoryRequest req) {
        var body = responses.prepare(sessionId, StoryWritingPrompt.body(responses.model(), pack, scenario, req));
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
        return new Called(text, StoryParser.parse(text, window.cutoff(), window.end()));
    }

    private long begin(UUID runId) {
        Boolean ok = tx.execute(s -> {
            if (!guard.lockAndCheck(runId)) {
                s.setRollbackOnly();
                return false;
            }
            return runs.markStageIfRunning(runId, RunStage.WRITING_STORY, now());
        });
        if (!Boolean.TRUE.equals(ok)) {
            throw new Stop();
        }
        return System.nanoTime();
    }

    private void fail(UUID runId) {
        Boolean failed = tx.execute(s -> {
            if (!guard.lockAndCheck(runId)) {
                s.setRollbackOnly();
                return false;
            }
            runs.markFailed(runId, "INVALID_SCENARIO", INVALID_MESSAGE, now());
            return true;
        });
        if (!Boolean.TRUE.equals(failed)) {
            throw new Stop();
        }
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
