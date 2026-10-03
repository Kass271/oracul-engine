package com.oracul.app.research;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.NormalizedEvent;
import org.springframework.boot.jackson.JacksonMixin;

/** Optional event fields are absent (not null) in the JSON response, per the research-pipeline spec. */
@JacksonMixin(NormalizedEvent.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class NormalizedEventMixin {
}
