package com.oracul.app.research;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.EvidenceItem;
import org.springframework.boot.jackson.JacksonMixin;

/** Optional evidence item fields (date, disagreement) are absent, not null, in JSON. */
@JacksonMixin(EvidenceItem.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class EvidenceItemMixin {
}
