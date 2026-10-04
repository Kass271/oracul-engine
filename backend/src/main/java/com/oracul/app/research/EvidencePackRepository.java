package com.oracul.app.research;

import com.oracul.app.api.model.EvidenceItem;
import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.ResearchProfile;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.Source;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class EvidencePackRepository {

    /** JSON shape of the items column. */
    record Items(List<EvidenceItem> core, List<EvidenceItem> supporting, List<EvidenceItem> counterSignals) {
    }

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final SourceRepository sources;

    EvidencePackRepository(JdbcTemplate jdbc, JsonMapper json, SourceRepository sources) {
        this.jdbc = jdbc;
        this.json = json;
        this.sources = sources;
    }

    public void insert(UUID runId, EvidencePack pack, OffsetDateTime createdAt) {
        List<String> sourceIds = new ArrayList<>();
        for (Source s : pack.getSources()) {
            sourceIds.add(s.getId());
        }
        jdbc.update("insert into evidence_pack (id, run_id, generation_id, cutoff, configuration, profile, items, "
                + "source_ids, prompt_text, created_at) values (?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), "
                + "cast(? as jsonb), cast(? as jsonb), ?, ?)",
            pack.getId(), runId, pack.getGenerationId(), pack.getCutoff(), json.writeValueAsString(pack.getConfiguration()),
            json.writeValueAsString(pack.getProfile()),
            json.writeValueAsString(new Items(pack.getCore(), pack.getSupporting(), pack.getCounterSignals())),
            json.writeValueAsString(sourceIds), pack.getPromptText(), createdAt);
    }

    /** The run that built the pack (owner of its sources and events). */
    public Optional<UUID> ownerRunId(UUID packId) {
        return jdbc.query("select run_id from evidence_pack where id = ?", (rs, i) -> rs.getObject("run_id", UUID.class),
            packId).stream().findFirst();
    }

    public Optional<EvidencePack> findById(UUID packId) {
        List<EvidencePack> rows = jdbc.query("select id, run_id, generation_id, cutoff, configuration, profile, items, "
                + "source_ids, prompt_text from evidence_pack where id = ?",
            (rs, i) -> {
                UUID runId = rs.getObject("run_id", UUID.class);
                Items items = json.readValue(rs.getString("items"), Items.class);
                List<String> ids = json.readValue(rs.getString("source_ids"), new TypeReference<List<String>>() { });
                Map<String, Source> all = new java.util.HashMap<>();
                for (Source s : sources.list(runId)) {
                    all.put(s.getId(), s);
                }
                List<Source> referenced = new ArrayList<>();
                for (String id : ids) {
                    if (all.containsKey(id)) {
                        referenced.add(all.get(id));
                    }
                }
                OffsetDateTime cutoff = rs.getObject("cutoff", OffsetDateTime.class).withOffsetSameInstant(ZoneOffset.UTC);
                return new EvidencePack(rs.getObject("id", UUID.class), rs.getString("generation_id"), cutoff,
                    json.readValue(rs.getString("configuration"), ScenarioConfiguration.class),
                    json.readValue(rs.getString("profile"), ResearchProfile.class),
                    items.core(), items.supporting(), items.counterSignals(), referenced, rs.getString("prompt_text"));
            }, packId);
        return rows.stream().findFirst();
    }
}
