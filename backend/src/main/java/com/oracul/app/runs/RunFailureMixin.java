package com.oracul.app.runs;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.RunFailure;
import org.springframework.boot.jackson.JacksonMixin;

/** providerCode is absent (not null) unless the failure carries one. */
@JacksonMixin(RunFailure.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class RunFailureMixin {
}
