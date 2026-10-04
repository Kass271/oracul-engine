package com.oracul.app.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.ScenarioMetadata;
import org.springframework.boot.jackson.JacksonMixin;

/** The optional model field is absent (not null) when unknown. */
@JacksonMixin(ScenarioMetadata.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class ScenarioMetadataMixin {
}
