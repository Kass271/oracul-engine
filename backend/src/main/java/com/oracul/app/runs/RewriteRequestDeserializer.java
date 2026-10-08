package com.oracul.app.runs;

import com.oracul.app.api.model.RewriteRequest;
import com.oracul.app.api.model.StoryLanguage;
import com.oracul.app.api.model.WritingMode;
import com.oracul.app.api.model.WritingStyleSelection;
import org.springframework.boot.jackson.JacksonComponent;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/** Non-failing reading of a rewrite request, so the controller reports style before language (and 404 first). */
@JacksonComponent
public class RewriteRequestDeserializer extends ValueDeserializer<RewriteRequest> {

    private static final WritingStyleSelection PLACEHOLDER_STYLE = new WritingStyleSelection(WritingMode.OFFICIAL, "x");

    @Override
    public RewriteRequest deserialize(JsonParser p, DeserializationContext ctxt) {
        return parse(ctxt.readTree(p));
    }

    @Override
    public RewriteRequest getNullValue(DeserializationContext ctxt) {
        return parse(null);
    }

    private static RewriteRequest parse(JsonNode root) {
        if (root == null || !root.isObject()) {
            return new ParsedRewriteRequest(PLACEHOLDER_STYLE, StoryLanguage.EN, true, true);
        }
        WritingStyleSelection style = null;
        JsonNode ws = root.get("writingStyle");
        if (ws != null && ws.isObject()) {
            JsonNode mode = ws.get("mode");
            JsonNode id = ws.get("profileId");
            WritingMode m = null;
            if (mode != null && mode.isString()) {
                for (WritingMode c : WritingMode.values()) {
                    if (c.getValue().equals(mode.stringValue())) m = c;
                }
            }
            if (m != null && id != null && id.isString() && id.stringValue().matches("[a-z0-9-]{1,40}")) {
                style = new WritingStyleSelection(m, id.stringValue());
            }
        }
        StoryLanguage language = null;
        JsonNode ln = root.get("language");
        if (ln != null && ln.isString() && ("en".equals(ln.stringValue()) || "uk".equals(ln.stringValue()))) {
            language = StoryLanguage.fromValue(ln.stringValue());
        }
        return new ParsedRewriteRequest(style == null ? PLACEHOLDER_STYLE : style,
            language == null ? StoryLanguage.EN : language, style == null, language == null);
    }
}
