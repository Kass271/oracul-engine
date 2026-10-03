package com.oracul.app.runs;

import com.oracul.app.api.model.RunFailureCode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** On startup every QUEUED/RUNNING run becomes FAILED RUN_INTERRUPTED (FR-32). */
@Component
public class RunStartupSweep implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(RunStartupSweep.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    RunStartupSweep(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void afterSingletonsInstantiated() {
        sweep();
    }

    public int sweep() {
        OffsetDateTime now = clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
        int n = jdbc.update("update generation_run set status = 'FAILED', failure_code = 'RUN_INTERRUPTED', "
            + "failure_message = ?, updated_at = ?, completed_at = ? where status in ('QUEUED','RUNNING')",
            RunFailures.message(RunFailureCode.RUN_INTERRUPTED), now, now);
        log.info("Startup sweep interrupted {} run(s)", n);
        return n;
    }
}
