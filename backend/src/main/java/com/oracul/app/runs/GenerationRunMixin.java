package com.oracul.app.runs;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.GenerationRun;
import java.util.UUID;
import org.springframework.boot.jackson.JacksonMixin;

/** evidencePackId is absent (not null) until the Evidence Pack is committed. */
@JacksonMixin(GenerationRun.class)
abstract class GenerationRunMixin {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract UUID getEvidencePackId();
}
