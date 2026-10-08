package com.oracul.app.runs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.oracul.app.api.model.RewriteRequest;
import com.oracul.app.api.model.StoryLanguage;
import com.oracul.app.api.model.WritingStyleSelection;

/** A rewrite request as read from the body: remembers an unusable writingStyle / language (placeholders stand in). */
public final class ParsedRewriteRequest extends RewriteRequest {

    private final boolean styleInvalid;
    private final boolean languageInvalid;

    ParsedRewriteRequest(WritingStyleSelection style, StoryLanguage language, boolean styleInvalid,
                         boolean languageInvalid) {
        super(style, language);
        this.styleInvalid = styleInvalid;
        this.languageInvalid = languageInvalid;
    }

    @JsonIgnore
    boolean styleInvalid() {
        return styleInvalid;
    }

    @JsonIgnore
    boolean languageInvalid() {
        return languageInvalid;
    }
}
