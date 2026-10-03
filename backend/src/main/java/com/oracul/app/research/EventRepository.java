package com.oracul.app.research;

import com.oracul.app.api.model.EventClassification;
import com.oracul.app.api.model.NormalizedEvent;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class EventRepository {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    EventRepository(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void insertAll(UUID runId, List<NormalizedEvent> events) {
        jdbc.batchUpdate("insert into event (run_id, id, event_date, category, entities, summary, disagreement, "
                + "source_ids, confidence, classification, excluded_reason) values (?, ?, ?, ?, cast(? as jsonb), ?, ?, "
                + "cast(? as jsonb), ?, cast(? as jsonb), ?)",
            events, 100, (ps, e) -> {
                ps.setObject(1, runId);
                ps.setString(2, e.getId());
                ps.setDate(3, e.getDate() == null ? null : Date.valueOf(e.getDate()));
                ps.setString(4, e.getCategory());
                ps.setString(5, json.writeValueAsString(e.getEntities()));
                ps.setString(6, e.getSummary());
                ps.setString(7, e.getDisagreement());
                ps.setString(8, json.writeValueAsString(e.getSourceIds()));
                ps.setDouble(9, e.getConfidence());
                ps.setString(10, e.getClassification() == null ? null : json.writeValueAsString(e.getClassification()));
                ps.setString(11, e.getExcludedReason());
            });
    }

    public List<NormalizedEvent> list(UUID runId) {
        return jdbc.query("select id, event_date, category, entities, summary, disagreement, source_ids, confidence, "
                + "classification, excluded_reason from event where run_id = ? order by length(id), id",
            (rs, i) -> {
                NormalizedEvent e = new NormalizedEvent();
                e.setId(rs.getString("id"));
                e.setDate(rs.getObject("event_date", LocalDate.class));
                e.setCategory(rs.getString("category"));
                e.setEntities(json.readValue(rs.getString("entities"), new TypeReference<List<String>>() { }));
                e.setSummary(rs.getString("summary"));
                e.setDisagreement(rs.getString("disagreement"));
                e.setSourceIds(json.readValue(rs.getString("source_ids"), new TypeReference<List<String>>() { }));
                e.setConfidence(rs.getDouble("confidence"));
                if (rs.getString("classification") != null) {
                    e.setClassification(json.readValue(rs.getString("classification"), EventClassification.class));
                }
                e.setExcludedReason(rs.getString("excluded_reason"));
                return e;
            }, runId);
    }
}
