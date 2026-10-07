package com.oracul.app.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.ResultGroupSource;
import org.springframework.boot.jackson.JacksonMixin;

/** Optional fields are absent (never null). */
@JacksonMixin(ResultGroupSource.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class ResultGroupSourceMixin {
}
