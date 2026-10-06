package com.oracul.app.research;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.WildcardPipeline;
import java.util.List;
import org.springframework.boot.jackson.JacksonMixin;

/** Optional pipeline fields (level, topicKey, candidatesConsidered) are absent, not null; sourceIds absent when empty. */
@JacksonMixin(WildcardPipeline.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class WildcardPipelineMixin {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    abstract List<String> getSourceIds();
}
