package com.oracul.app.research;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.ArticleContentStatus;
import com.oracul.app.api.model.Source;
import com.oracul.app.api.model.SourceExcerpt;
import java.util.List;
import org.springframework.boot.jackson.JacksonMixin;

/** Phase-03 source fields are absent, not null or [], until a run sets them (older runs never do). */
@JacksonMixin(Source.class)
abstract class SourceMixin {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract String getPublisherHost();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract ArticleContentStatus getContentStatus();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract List<SourceExcerpt> getExcerpts();

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    abstract List<String> getPipelineIds();
}
