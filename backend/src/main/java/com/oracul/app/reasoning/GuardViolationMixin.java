package com.oracul.app.reasoning;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.GuardViolation;
import org.springframework.boot.jackson.JacksonMixin;

/** Optional fields of GuardViolation are absent, not null, in JSON. */
@JacksonMixin(GuardViolation.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class GuardViolationMixin {
}
