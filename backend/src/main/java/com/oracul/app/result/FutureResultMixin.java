package com.oracul.app.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.FutureResult;
import com.oracul.app.api.model.ResultWildcardGroup;
import java.util.List;
import org.springframework.boot.jackson.JacksonMixin;

/** wildcardGroups is present only for runs with wildcard pipelines, never []. */
@JacksonMixin(FutureResult.class)
abstract class FutureResultMixin {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    abstract List<ResultWildcardGroup> getWildcardGroups();
}
