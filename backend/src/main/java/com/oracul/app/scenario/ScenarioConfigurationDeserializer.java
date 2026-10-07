package com.oracul.app.scenario;

import com.oracul.app.api.model.CustomWildcard;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.OutputSettings;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.StoryLanguage;
import com.oracul.app.api.model.WritingMode;
import com.oracul.app.api.model.WritingStyleSelection;
import com.oracul.app.api.model.WildcardSetting;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.jackson.JacksonComponent;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Strict but non-failing reading of a scenario configuration: wrong types become null so that bean validation
 * reports violations in the specified precedence order instead of the first one Jackson meets. A body that is not
 * a JSON object is a syntax-level failure.
 */
@JacksonComponent
public class ScenarioConfigurationDeserializer extends ValueDeserializer<ScenarioConfiguration> {

    @Override
    public ScenarioConfiguration deserialize(JsonParser p, DeserializationContext ctxt) {
        JsonNode root = ctxt.readTree(p);
        if (root == null || !root.isObject()) {
            return ctxt.reportInputMismatch(ScenarioConfiguration.class, "not an object");
        }
        return new ScenarioConfiguration(
            integer(root.get("realism")),
            integer(root.get("darkness")),
            integer(root.get("optimism")),
            horizon(root.get("horizon")),
            wildcards(root.get("wildcards")),
            customWildcards(root.get("customWildcards")),
            output(root.get("output")));
    }

    private static Integer integer(JsonNode n) {
        return n != null && n.isIntegralNumber() && n.canConvertToInt() ? n.intValue() : null;
    }

    private static HorizonCode horizon(JsonNode n) {
        if (n == null || !n.isString()) return null;
        for (HorizonCode c : HorizonCode.values()) {
            if (c.getValue().equals(n.stringValue())) return c;
        }
        return null;
    }

    private static <T> T value(DeserializationContext ctxt, JsonNode n, Class<T> type) {
        if (n == null || !n.isObject()) return null;
        try {
            return ctxt.readTreeAsValue(n, type);
        } catch (JacksonException e) {
            return null;
        }
    }

    private static List<WildcardSetting> wildcards(JsonNode n) {
        if (n == null || !n.isArray()) return null;
        List<WildcardSetting> out = new ArrayList<>();
        for (JsonNode item : n) {
            if (item == null || !item.isObject()) return null;
            JsonNode id = item.get("wildcardId");
            out.add(new WildcardSetting(id != null && id.isString() ? id.stringValue() : null,
                integer(item.get("intensity"))));
        }
        return out;
    }

    private static <T> List<T> list(DeserializationContext ctxt, JsonNode n, Class<T> type) {
        if (n == null || !n.isArray()) return null;
        List<T> out = new ArrayList<>();
        for (JsonNode item : n) {
            T v = value(ctxt, item, type);
            if (v == null) return null;
            out.add(v);
        }
        return out;
    }

    private static List<CustomWildcard> customWildcards(JsonNode n) {
        if (n == null || !n.isArray()) return null;
        List<CustomWildcard> out = new ArrayList<>();
        for (JsonNode item : n) {
            if (item == null || !item.isObject()) return null;
            JsonNode label = item.get("label");
            out.add(new CustomWildcard(label != null && label.isString() ? label.stringValue().strip() : null,
                integer(item.get("intensity"))));
        }
        return out;
    }

    private static OutputSettings output(JsonNode n) {
        if (n == null || !n.isObject()) return null;
        boolean styleInvalid = false;
        boolean languageInvalid = false;
        WritingStyleSelection style = null;
        JsonNode ws = n.get("writingStyle");
        if (ws != null && !ws.isNull()) {
            JsonNode mode = ws.isObject() ? ws.get("mode") : null;
            JsonNode id = ws.isObject() ? ws.get("profileId") : null;
            WritingMode m = null;
            if (mode != null && mode.isString()) {
                for (WritingMode c : WritingMode.values()) {
                    if (c.getValue().equals(mode.stringValue())) m = c;
                }
            }
            if (m != null && id != null && id.isString() && id.stringValue().matches("[a-z0-9-]{1,40}")) {
                style = new WritingStyleSelection(m, id.stringValue());
            } else {
                styleInvalid = true;
            }
        }
        StoryLanguage language = null;
        JsonNode ln = n.get("language");
        if (ln != null && !ln.isNull()) {
            if (ln.isString() && ("en".equals(ln.stringValue()) || "uk".equals(ln.stringValue()))) {
                language = StoryLanguage.fromValue(ln.stringValue());
            } else {
                languageInvalid = true;
            }
        }
        ParsedOutputSettings out = new ParsedOutputSettings(bool(n.get("story")), bool(n.get("illustration")),
            styleInvalid, languageInvalid);
        out.setWritingStyle(style);
        out.setLanguage(language);
        return out;
    }

    private static Boolean bool(JsonNode n) {
        return n != null && n.isBoolean() ? n.booleanValue() : null;
    }
}
