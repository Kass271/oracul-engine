package com.oracul.app.research;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.PackWildcardSection;
import java.util.List;
import org.springframework.boot.jackson.JacksonMixin;

/** wildcardSections is absent for packs without wildcard sections (older runs), never []. */
@JacksonMixin(EvidencePack.class)
abstract class EvidencePackMixin {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    abstract List<PackWildcardSection> getWildcardSections();
}
