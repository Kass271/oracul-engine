package com.oracul.app.research;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.WildcardPipeline;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;
import org.springframework.boot.jackson.JacksonMixin;

/**
 * Optional pipeline fields (level, topicKey, candidatesConsidered) are absent, not null. sourceIds is absent while the
 * READING_SOURCES commit has not happened (no candidatesConsidered) and empty, else sent even when [] (FR-53).
 */
@JacksonMixin(WildcardPipeline.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonSerialize(using = WildcardPipelineMixin.Writer.class)
abstract class WildcardPipelineMixin {

    static final class Writer extends ValueSerializer<WildcardPipeline> {
        @Override
        public void serialize(WildcardPipeline p, JsonGenerator g, SerializationContext provider) {
            g.writeStartObject();
            g.writeStringProperty("id", p.getId());
            provider.defaultSerializeProperty("kind", p.getKind(), g);
            g.writeStringProperty("label", p.getLabel());
            if (p.getLevel() != null) {
                g.writeNumberProperty("level", p.getLevel());
            }
            if (p.getTopicKey() != null) {
                g.writeStringProperty("topicKey", p.getTopicKey());
            }
            g.writeStringProperty("heading", p.getHeading());
            provider.defaultSerializeProperty("queryMode", p.getQueryMode(), g);
            provider.defaultSerializeProperty("queries", p.getQueries(), g);
            if (p.getCandidatesConsidered() != null) {
                g.writeNumberProperty("candidatesConsidered", p.getCandidatesConsidered());
            }
            if (p.getSourceIds() != null && (!p.getSourceIds().isEmpty() || p.getCandidatesConsidered() != null)) {
                provider.defaultSerializeProperty("sourceIds", p.getSourceIds(), g);
            }
            g.writeEndObject();
        }
    }
}
