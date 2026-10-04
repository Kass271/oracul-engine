package com.oracul.app.reasoning;

import com.oracul.app.api.model.CausalStep;
import com.oracul.app.api.model.StructuredScenario;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** The futures an ALTERNATIVE run must not repeat: the parent first, then earlier runs on the same pack (FR-30). */
@Component
public class AvoidedFutures {

    static final int MAX = 10;

    /** Title and causal-chain statements of one earlier future. */
    public record AvoidedFuture(String title, List<String> steps) {
    }

    private record Found(UUID id, UUID parentId, StructuredScenario scenario) {
    }

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    AvoidedFutures(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<AvoidedFuture> load(UUID runId) {
        UUID parentId = jdbc.query("select parent_run_id from generation_run where id = ?",
            (rs, i) -> rs.getObject("parent_run_id", UUID.class), runId).stream().findFirst().orElse(null);
        List<Found> rows = jdbc.query("select r.id, a.cleaned_scenario from generation_run me "
                + "join generation_run r on r.session_id = me.session_id and r.evidence_pack_id = me.evidence_pack_id "
                + "and r.status = 'COMPLETED' and r.final_attempt is not null and r.created_at < me.created_at "
                + "and r.id <> me.id "
                + "join scenario_attempt a on a.run_id = r.id and a.attempt = r.final_attempt "
                + "where me.id = ? and a.cleaned_scenario is not null order by r.created_at asc, r.id asc",
            (rs, i) -> new Found(rs.getObject("id", UUID.class), null,
                json.readValue(rs.getString("cleaned_scenario"), StructuredScenario.class)), runId);
        Found parent = null;
        List<Found> others = new ArrayList<>();
        for (Found f : rows) {
            if (f.id().equals(parentId)) {
                parent = f;
            } else {
                others.add(f);
            }
        }
        List<Found> ordered = new ArrayList<>();
        int room = parent == null ? MAX : MAX - 1;
        if (others.size() > room) {
            others = new ArrayList<>(others.subList(others.size() - room, others.size()));
        }
        if (parent != null) {
            ordered.add(parent);
        }
        ordered.addAll(others);
        List<AvoidedFuture> out = new ArrayList<>();
        for (Found f : ordered) {
            List<String> steps = f.scenario().getCausalChain().stream()
                .sorted(Comparator.comparingInt(CausalStep::getOrder)).map(CausalStep::getStatement).toList();
            out.add(new AvoidedFuture(f.scenario().getFutureEvent().getTitle(), steps));
        }
        return out;
    }
}
