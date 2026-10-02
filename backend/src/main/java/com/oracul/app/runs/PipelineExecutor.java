package com.oracul.app.runs;

import com.oracul.app.api.model.RunStage;
import com.oracul.app.research.ResearchProfileFactory;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Runs the generation pipeline of one run per task on a bounded pool; every transition is its own commit. */
@Component
public class PipelineExecutor {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutor.class);

    private final GenerationRunRepository runs;
    private final ResearchProfileFactory profiles;
    private final Clock clock;
    private final Duration stageDelay;
    private final ThreadPoolExecutor pool;

    PipelineExecutor(GenerationRunRepository runs, ResearchProfileFactory profiles, Clock clock,
                     @Value("${oracul.run.placeholder-stage-delay:PT1S}") Duration stageDelay,
                     @Value("${oracul.run.executor-threads:4}") int threads) {
        this.runs = runs;
        this.profiles = profiles;
        this.clock = clock;
        this.stageDelay = stageDelay;
        AtomicInteger n = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "pipeline-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.pool = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), factory);
    }

    void submit(UUID runId, UUID sessionId) {
        pool.execute(() -> execute(runId, sessionId));
    }

    private void execute(UUID runId, UUID sessionId) {
        try {
            runs.markStage(runId, RunStage.UNDERSTANDING, now());
            var run = runs.find(runId, sessionId).orElseThrow();
            runs.storeProfile(runId, profiles.from(run.configuration()), now());
            RunStage[] stages = RunStage.values();
            for (int i = 1; i < stages.length; i++) {
                runs.markStage(runId, stages[i], now());
                pause();
            }
            runs.markCompleted(runId, RunStage.WRITING_STORY, now());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.error("Pipeline failed for run {}", runId, e);
            try {
                runs.markFailed(runId, "INTERNAL_ERROR", "Something went wrong — try again", now());
            } catch (RuntimeException inner) {
                log.error("Could not mark run {} failed", runId, inner);
            }
        }
    }

    private void pause() throws InterruptedException {
        if (!stageDelay.isZero() && !stageDelay.isNegative()) {
            Thread.sleep(stageDelay.toMillis());
        }
    }

    private OffsetDateTime now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}
