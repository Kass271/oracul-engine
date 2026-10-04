package com.oracul.app.research;

import com.oracul.app.api.model.Source;
import com.oracul.app.api.model.SourceType;
import java.net.URI;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class SourceRepository {

    /** A source together with the provider language kept for later stages. */
    public record Stored(Source source, String language) {
    }

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    SourceRepository(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void insertAll(UUID runId, List<Stored> sources) {
        jdbc.batchUpdate("insert into source (run_id, id, url, publisher, title, published_at, retrieved_at, summary, "
                + "topic, entities, source_type, source_quality, metadata_fetched, language, query_ids, publisher_url) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, cast(? as jsonb), ?)",
            sources, 100, (ps, st) -> {
                Source s = st.source();
                ps.setObject(1, runId);
                ps.setString(2, s.getId());
                ps.setString(3, s.getUrl().toString());
                ps.setString(4, s.getPublisher());
                ps.setString(5, s.getTitle());
                ps.setObject(6, s.getPublishedAt());
                ps.setObject(7, s.getRetrievedAt());
                ps.setString(8, s.getSummary());
                ps.setString(9, s.getTopic());
                ps.setString(10, json.writeValueAsString(s.getEntities()));
                ps.setString(11, s.getSourceType().getValue());
                ps.setDouble(12, s.getSourceQuality());
                ps.setBoolean(13, s.getMetadataFetched());
                ps.setString(14, st.language());
                ps.setString(15, json.writeValueAsString(s.getQueryIds()));
                ps.setString(16, s.getPublisherUrl() == null ? null : s.getPublisherUrl().toString());
            });
    }

    /** Sets the entities of each listed source (source id to entities). */
    public void updateEntities(UUID runId, java.util.Map<String, List<String>> entities) {
        List<java.util.Map.Entry<String, List<String>>> rows = new java.util.ArrayList<>(entities.entrySet());
        jdbc.batchUpdate("update source set entities = cast(? as jsonb) where run_id = ? and id = ?", rows, 100,
            (ps, row) -> {
                ps.setString(1, json.writeValueAsString(row.getValue()));
                ps.setObject(2, runId);
                ps.setString(3, row.getKey());
            });
    }

    public List<Source> list(UUID runId) {
        return jdbc.query("select id, url, publisher, title, published_at, retrieved_at, summary, topic, entities, "
                + "source_type, source_quality, metadata_fetched, query_ids, publisher_url from source where run_id = ? "
                + "order by length(id), id",
            (rs, i) -> {
                Source s = new Source();
                s.setId(rs.getString("id"));
                s.setUrl(URI.create(rs.getString("url")));
                s.setPublisher(rs.getString("publisher"));
                s.setTitle(rs.getString("title"));
                s.setPublishedAt(utc(rs.getObject("published_at", OffsetDateTime.class)));
                s.setRetrievedAt(utc(rs.getObject("retrieved_at", OffsetDateTime.class)));
                s.setSummary(rs.getString("summary"));
                s.setTopic(rs.getString("topic"));
                s.setEntities(json.readValue(rs.getString("entities"), new TypeReference<List<String>>() { }));
                s.setSourceType(SourceType.fromValue(rs.getString("source_type")));
                s.setSourceQuality(rs.getDouble("source_quality"));
                s.setMetadataFetched(rs.getBoolean("metadata_fetched"));
                s.setQueryIds(json.readValue(rs.getString("query_ids"), new TypeReference<List<String>>() { }));
                String publisherUrl = rs.getString("publisher_url");
                if (publisherUrl != null) {
                    s.setPublisherUrl(URI.create(publisherUrl));
                }
                return s;
            }, runId);
    }

    private static OffsetDateTime utc(OffsetDateTime t) {
        return t == null ? null : t.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
