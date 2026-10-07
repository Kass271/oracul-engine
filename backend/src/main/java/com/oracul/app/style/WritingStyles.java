package com.oracul.app.style;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The writing style profiles, read and validated once from the style file at startup. */
@Component
public class WritingStyles {

    static final String OFFICIAL_ID = "official-briefing";
    static final String NEUTRAL_NARRATIVE_ID = "neutral-narrative";
    private static final Pattern ID = Pattern.compile("^[a-z0-9-]{1,40}$");

    private final List<WritingStyleProfile> profiles;

    public WritingStyles(@Value("${oracul.writing-styles.location:classpath:writing-styles.json}") String location,
        ResourceLoader resourceLoader) {
        JsonNode list = read(location, resourceLoader);
        if (list.size() < 2 || list.size() > 50) {
            throw new IllegalStateException("Writing style file " + location + " must have 2 to 50 profiles");
        }
        List<WritingStyleProfile> parsed = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int n = 0;
        for (JsonNode entry : list) {
            n++;
            parsed.add(entry(entry, n, ids));
        }
        long officials = parsed.stream().filter(p -> p.mode() == WritingMode.OFFICIAL).count();
        if (officials != 1 || parsed.stream().noneMatch(p -> p.id().equals(OFFICIAL_ID) && p.mode() == WritingMode.OFFICIAL)) {
            throw new IllegalStateException("Writing style file " + location
                + " must have exactly one OFFICIAL profile, official-briefing");
        }
        if (parsed.stream().noneMatch(p -> p.id().equals(NEUTRAL_NARRATIVE_ID) && p.mode() == WritingMode.NARRATIVE)) {
            throw new IllegalStateException("Writing style file " + location
                + " must have the NARRATIVE profile neutral-narrative");
        }
        List<WritingStyleProfile> ordered = new ArrayList<>();
        parsed.stream().filter(p -> p.mode() == WritingMode.OFFICIAL).forEach(ordered::add);
        parsed.stream().filter(p -> p.mode() == WritingMode.NARRATIVE).forEach(ordered::add);
        this.profiles = List.copyOf(ordered);
    }

    public List<WritingStyleProfile> profiles() {
        return profiles;
    }

    public Optional<WritingStyleProfile> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return profiles.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    private static JsonNode read(String location, ResourceLoader loader) {
        String message = "Writing style file " + location + " cannot be read";
        try (InputStream in = loader.getResource(location).getInputStream()) {
            JsonNode root = JsonMapper.builder().build().readTree(in);
            if (root == null || !root.isObject() || !root.has("profiles") || !root.get("profiles").isArray()) {
                throw new IllegalStateException(message);
            }
            return root.get("profiles");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(message);
        }
    }

    private static String text(JsonNode entry, String key) {
        JsonNode v = entry.get(key);
        return v != null && v.isString() ? v.stringValue() : null;
    }

    private static WritingStyleProfile entry(JsonNode entry, int n, Set<String> ids) {
        String id = entry.isObject() ? text(entry, "id") : null;
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Writing style entry #" + n + ": missing id");
        }
        String prefix = "Writing style entry #" + n + " (" + id + "): ";
        if (!ID.matcher(id).matches()) {
            throw new IllegalStateException(prefix + "invalid id");
        }
        if (!ids.add(id)) {
            throw new IllegalStateException(prefix + "duplicate id");
        }
        String modeText = text(entry, "mode");
        WritingMode mode = "OFFICIAL".equals(modeText) ? WritingMode.OFFICIAL
            : "NARRATIVE".equals(modeText) ? WritingMode.NARRATIVE : null;
        if (mode == null) {
            throw new IllegalStateException(prefix + "unknown mode");
        }
        String label = text(entry, "label");
        if (label == null || label.isBlank()) {
            throw new IllegalStateException(prefix + "missing label");
        }
        String characteristics = text(entry, "characteristics");
        if (characteristics == null || characteristics.isBlank()) {
            throw new IllegalStateException(prefix + "missing characteristics");
        }
        String instructions = text(entry, "instructions");
        if (instructions == null || instructions.isBlank()) {
            throw new IllegalStateException(prefix + "missing prompt text");
        }
        checkLength(label, 80, "label", prefix);
        checkLength(characteristics, 300, "characteristics", prefix);
        checkLength(instructions, 2000, "instructions", prefix);
        checkChars(label, "label", prefix);
        checkChars(characteristics, "characteristics", prefix);
        checkChars(instructions, "instructions", prefix);
        return new WritingStyleProfile(id, mode, label, characteristics, instructions);
    }

    private static void checkLength(String value, int max, String field, String prefix) {
        if (value.codePointCount(0, value.length()) > max) {
            throw new IllegalStateException(prefix + field + " is too long");
        }
    }

    private static void checkChars(String value, String field, String prefix) {
        boolean bad = value.contains("<<<") || value.contains(">>>")
            || value.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x2028 || c == 0x2029);
        if (bad) {
            throw new IllegalStateException(prefix + field + " contains forbidden characters");
        }
    }
}
