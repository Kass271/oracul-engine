package com.oracul.app.research;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.WildcardPipeline;
import java.util.List;
import org.springframework.boot.jackson.JacksonMixin;

/** pipelines is absent for runs without wildcard pipelines (older runs), never []. */
@JacksonMixin(SearchPlan.class)
abstract class SearchPlanMixin {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    abstract List<WildcardPipeline> getPipelines();
}
