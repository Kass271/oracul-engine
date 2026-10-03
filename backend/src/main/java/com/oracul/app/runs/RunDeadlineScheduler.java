package com.oracul.app.runs;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Fails active runs whose deadline passed with RUN_TIMEOUT (FR-32). */
@Component
public class RunDeadlineScheduler {

    private static final Logger log = LoggerFactory.getLogger(RunDeadlineScheduler.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    RunDeadlineScheduler(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${oracul.run.deadline-check-interval:PT5S}",
        initialDelayString = "${oracul.run.deadline-check-interval:PT5S}")
    public int sweep() {
        try {
            OffsetDateTime now = clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
            int n = jdbc.update("update generation_run set status = 'FAILED', failure_code = 'RUN_TIMEOUT', "
                + "failure_message = ?, updated_at = ?, completed_at = ? "
                + "where status in ('QUEUED','RUNNING') and deadline_at <= ?",
                RunFailures.message(com.oracul.app.api.model.RunFailureCode.RUN_TIMEOUT), now, now, now);
            if (n > 0) {
                log.info("Deadline sweep timed out {} run(s)", n);
            }
            return n;
        } catch (RuntimeException e) {
            log.error("Deadline sweep failed: {}", e.getClass().getSimpleName());
            return 0;
        }
    }
}
