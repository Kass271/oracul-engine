package com.oracul.app.scenario;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oracul.app.api.model.OutputSettings;
import org.springframework.boot.jackson.JacksonMixin;

/** Optional writingStyle / language stay absent, never null, in JSON (stored exactly as sent). */
@JacksonMixin(OutputSettings.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class OutputSettingsMixin {
}
