package com.oracul.app.runs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A style file with the nine shipped profile ids (same modes) plus the spec's {@code dickens} NARRATIVE entry, written
 * once to a temp file. Tests point {@code oracul.writing-styles.location} at {@link #LOCATION} to prove FR-62
 * "a profile added to the file is usable without any other code change". Only ids and modes matter for the
 * validation of {@code startRun}; the texts are short valid placeholders.
 */
final class DickensStyleFile {

    static final List<String> SHIPPED_IDS = List.of(
        "official-briefing", "neutral-narrative", "poe", "kafka", "wells", "verne", "orwell", "clarke", "harrison");

    /** {@code file:/tmp/...json} — the property value, verbatim. */
    static final String LOCATION = create();

    private DickensStyleFile() {
    }

    private static String entry(String id, String mode, String label) {
        return "{\"id\":\"" + id + "\",\"mode\":\"" + mode + "\",\"label\":\"" + label
            + "\",\"characteristics\":\"characteristics of " + id + "\",\"instructions\":\"instructions of " + id + "\"}";
    }

    private static String create() {
        List<String> entries = new ArrayList<>();
        for (String id : SHIPPED_IDS) {
            entries.add(entry(id, id.equals("official-briefing") ? "OFFICIAL" : "NARRATIVE", "Label " + id));
        }
        entries.add(entry("dickens", "NARRATIVE", "Narrative — inspired by Charles Dickens"));
        try {
            Path file = Files.createTempFile("writing-styles-dickens-", ".json");
            file.toFile().deleteOnExit();
            Files.writeString(file, "{\"profiles\":[" + String.join(",", entries) + "]}", StandardCharsets.UTF_8);
            return "file:" + file;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
