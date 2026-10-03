package com.oracul.app.result;

import com.oracul.app.api.model.FutureStory;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** future_story rows: one per COMPLETED run. */
@Repository
public class FutureStoryRepository {

    private final JdbcTemplate jdbc;

    FutureStoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void insert(UUID runId, FutureStory story, OffsetDateTime now) {
        jdbc.update("insert into future_story (run_id, headline, dateline, future_date, body, created_at) "
                + "values (?, ?, ?, ?, ?, ?)",
            runId, story.getHeadline(), story.getDateline(), story.getFutureDate(), story.getBody(), now);
    }

    Optional<FutureStory> find(UUID runId) {
        return jdbc.query("select headline, dateline, future_date, body from future_story where run_id = ?",
            (rs, i) -> new FutureStory(rs.getString("headline"), rs.getString("dateline"),
                rs.getObject("future_date", LocalDate.class), rs.getString("body")),
            runId).stream().findFirst();
    }
}
