package com.oracul.app.research;

import com.oracul.app.api.model.EvidenceItem;
import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.NormalizedEvent;
import com.oracul.app.api.model.ResearchProfile;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.Source;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Assembles the immutable Evidence Pack of a run from the selection (FR-18). */
@Service
public class EvidencePackService {

    public EvidencePack build(UUID packId, String generationId, Instant cutoff, ScenarioConfiguration configuration,
                              ResearchProfile profile, EvidenceSelector.Result selection,
                              Map<String, Source> sourcesById) {
        List<EvidenceItem> core = items(selection.core());
        List<EvidenceItem> supporting = items(selection.supporting());
        List<EvidenceItem> counter = items(selection.counterSignals());
        TreeSet<String> ids = new TreeSet<>(EventCopies.ID_ORDER);
        for (List<EvidenceItem> section : List.of(core, supporting, counter)) {
            for (EvidenceItem i : section) {
                ids.addAll(i.getSourceIds());
            }
        }
        List<Source> sources = new ArrayList<>();
        for (String id : ids) {
            Source s = sourcesById.get(id);
            if (s != null) {
                sources.add(s);
            }
        }
        EvidencePack pack = new EvidencePack(packId, generationId, cutoff.atOffset(ZoneOffset.UTC), configuration,
            profile, core, supporting, counter, sources, "");
        pack.setPromptText(EvidencePackRenderer.render(pack));
        return pack;
    }

    private static List<EvidenceItem> items(List<NormalizedEvent> events) {
        List<EvidenceItem> out = new ArrayList<>();
        for (NormalizedEvent e : events) {
            List<String> sourceIds = new ArrayList<>(e.getSourceIds());
            sourceIds.sort(EventCopies.ID_ORDER);
            EvidenceItem i = new EvidenceItem(e.getSelection().getEvidenceId(), e.getSelection().getSection(),
                e.getId(), e.getCategory(), e.getSummary(), new ArrayList<>(e.getEntities()), sourceIds,
                e.getClassification().getSourceQuality(), e.getConfidence());
            i.setDate(e.getDate());
            i.setDisagreement(e.getDisagreement());
            out.add(i);
        }
        return out;
    }
}
