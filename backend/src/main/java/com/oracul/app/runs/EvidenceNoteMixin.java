package com.oracul.app.runs;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.EvidenceNote;
import java.util.List;
import org.springframework.boot.jackson.JacksonMixin;

/** wildcardsWithoutSources is present only when non-empty (FR-59); coreItems/coreNeeded of 0 stay written. */
@JacksonMixin(EvidenceNote.class)
abstract class EvidenceNoteMixin {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    abstract List<String> getWildcardsWithoutSources();
}
