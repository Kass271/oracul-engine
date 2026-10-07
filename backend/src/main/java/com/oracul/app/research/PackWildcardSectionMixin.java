package com.oracul.app.research;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.PackWildcardSection;
import org.springframework.boot.jackson.JacksonMixin;

/** Optional fields are absent, never null, in JSON (FR-57). */
@JacksonMixin(PackWildcardSection.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class PackWildcardSectionMixin {
}
