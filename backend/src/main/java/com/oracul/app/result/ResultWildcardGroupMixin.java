package com.oracul.app.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.ResultWildcardGroup;
import org.springframework.boot.jackson.JacksonMixin;

/** Optional fields are absent (never null). */
@JacksonMixin(ResultWildcardGroup.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class ResultWildcardGroupMixin {
}
