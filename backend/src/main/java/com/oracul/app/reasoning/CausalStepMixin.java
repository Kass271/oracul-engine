package com.oracul.app.reasoning;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.CausalStep;
import org.springframework.boot.jackson.JacksonMixin;

/** Optional fields of CausalStep are absent, not null, in JSON. */
@JacksonMixin(CausalStep.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class CausalStepMixin {
}
