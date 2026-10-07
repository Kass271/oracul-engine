package com.oracul.app.research;

import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.ResearchProfile;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.Source;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Assembles the immutable Evidence Pack of a run from the per-wildcard sources (FR-57). */
@Service
public class EvidencePackService {

    public EvidencePack build(UUID packId, String generationId, Instant cutoff, ScenarioConfiguration configuration,
                              ResearchProfile profile, SearchPlan plan, List<Source> keptSources) {
        List<Source> sources = new ArrayList<>(keptSources);
        sources.sort((a, b) -> EventCopies.ID_ORDER.compare(a.getId(), b.getId()));
        EvidencePack pack = new EvidencePack(packId, generationId, cutoff.atOffset(ZoneOffset.UTC), configuration,
            profile, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), sources, "");
        pack.setWildcardSections(WildcardPackRenderer.sections(
            plan.getPipelines() == null ? List.of() : plan.getPipelines(), sources));
        pack.setPromptText(WildcardPackRenderer.render(pack));
        return pack;
    }
}
