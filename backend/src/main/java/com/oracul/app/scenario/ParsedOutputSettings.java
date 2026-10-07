package com.oracul.app.scenario;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.oracul.app.api.model.OutputSettings;

/** Output as read from a request: remembers that writingStyle / language were present but unusable. */
public final class ParsedOutputSettings extends OutputSettings {

    private final boolean styleInvalid;
    private final boolean languageInvalid;

    ParsedOutputSettings(Boolean story, Boolean illustration, boolean styleInvalid, boolean languageInvalid) {
        super(story, illustration);
        this.styleInvalid = styleInvalid;
        this.languageInvalid = languageInvalid;
    }

    @JsonIgnore
    public boolean styleInvalid() {
        return styleInvalid;
    }

    @JsonIgnore
    public boolean languageInvalid() {
        return languageInvalid;
    }
}
