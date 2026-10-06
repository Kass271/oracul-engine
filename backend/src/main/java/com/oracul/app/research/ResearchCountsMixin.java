package com.oracul.app.research;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.ResearchCounts;
import org.springframework.boot.jackson.JacksonMixin;

/** Optional counts (sourcesKept, sourcesWithContent) are absent, not null, in JSON. */
@JacksonMixin(ResearchCounts.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class ResearchCountsMixin {
}
