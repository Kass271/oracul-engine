package com.oracul.app.style;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixtures for the writing style catalogue (FR-62): the nine shipped entries exactly as the spec table lists them,
 * and a tiny JSON writer that can produce deliberately broken style files (raw JSON values, absent keys).
 */
final class StyleFixtures {

    private StyleFixtures() {
    }

    /** One entry of the style file. */
    record Entry(String id, String mode, String label, String characteristics, String instructions) {

        String json() {
            return obj(fields());
        }

        Map<String, String> fields() {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("id", str(id));
            m.put("mode", str(mode));
            m.put("label", str(label));
            m.put("characteristics", str(characteristics));
            m.put("instructions", str(instructions));
            return m;
        }
    }

    /** The shipped style file, in file order (spec "Shipped style file"). */
    static final List<Entry> SHIPPED = List.of(
        new Entry("official-briefing", "OFFICIAL",
            "Official / Briefing",
            "factual, concise, structured, neutral; events, consequences, actors, dates and causal relationships; no literary description",
            "Report the future like an analytical briefing, situation report or intelligence summary written shortly after the events: clear, information-dense sentences about events, consequences, actors, dates and causal relationships."),
        new Entry("neutral-narrative", "NARRATIVE",
            "Neutral Narrative",
            "literary and immersive prose; atmosphere, scene setting, imagery, tension, narrative flow and pacing; no particular author's voice",
            "Tell the future as immersive literary prose with atmosphere, scene setting, imagery, tension and narrative flow, in a neutral contemporary voice that imitates no particular author."),
        new Entry("poe", "NARRATIVE",
            "Narrative — inspired by Edgar Allan Poe",
            "dark, gothic, psychologically intense, atmospheric, ominous; dread and decay",
            "Evoke a dark, gothic, psychologically intense atmosphere of dread and decay, with ominous foreshadowing and a brooding inner perspective."),
        new Entry("kafka", "NARRATIVE",
            "Narrative — inspired by Franz Kafka",
            "bureaucratic absurdity, alienation, impersonal systems, helplessness, surreal institutional logic, understated anxiety",
            "Evoke bureaucratic absurdity and alienation: impersonal institutions, procedures without visible purpose, helpless individuals and understated anxiety, told in a calm, matter-of-fact voice."),
        new Entry("wells", "NARRATIVE",
            "Narrative — inspired by H. G. Wells",
            "speculative and observational; technological and societal transformation and its consequences",
            "Evoke a speculative, observational narrator who watches technological and societal transformation unfold and reflects on its consequences for ordinary people."),
        new Entry("verne", "NARRATIVE",
            "Narrative — inspired by Jules Verne",
            "exploratory, technological, adventurous, detailed; curiosity about future inventions and environments",
            "Evoke exploratory, adventurous storytelling rich in technical detail, curiosity about new inventions and vivid descriptions of new environments."),
        new Entry("orwell", "NARRATIVE",
            "Narrative — inspired by George Orwell",
            "clear, restrained prose; institutions, political power, social control, language, surveillance, systemic consequences",
            "Evoke clear, restrained prose focused on institutions, political power, social control, the language of officials, surveillance and their systemic consequences."),
        new Entry("clarke", "NARRATIVE",
            "Narrative — inspired by Arthur C. Clarke",
            "large-scale scientific and technological perspective, rational tone, sense of discovery, humanity's relationship with transformative technologies",
            "Evoke a large-scale scientific and technological perspective with a rational tone, a sense of discovery and attention to humanity's relationship with transformative technology."),
        new Entry("harrison", "NARRATIVE",
            "Narrative — inspired by Harry Harrison",
            "fast-moving science fiction with irony, social satire, technological absurdity and energetic descriptions of future societies",
            "Evoke fast-moving science fiction with irony, social satire, technological absurdity and energetic descriptions of future society.")
);

    /** The entry the spec appends for the restart case. */
    static final Entry DICKENS = new Entry("dickens", "NARRATIVE",
        "Narrative — inspired by Charles Dickens",
        "Victorian social observation, vivid characters, moral contrast",
        "Evoke Victorian social observation with vivid characters and moral contrast.");

    static Entry shipped(String id) {
        return SHIPPED.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    /** A valid NARRATIVE entry with the given id (other fields fixed, short). */
    static Entry narrative(String id) {
        return new Entry(id, "NARRATIVE", "Narrative " + id, "characteristics of " + id, "instructions of " + id);
    }

    /** JSON string literal; everything that is not printable ASCII is written as a \\uXXXX escape. */
    static String str(String s) {
        StringBuilder b = new StringBuilder("\"");
        s.codePoints().forEach(c -> {
            if (c == '"') b.append("\\\"");
            else if (c == '\\') b.append("\\\\");
            else if (c < 0x20 || c == 0x7F || (c >= 0x80 && c <= 0x9F) || c == 0x2028 || c == 0x2029) b.append(String.format("\\u%04x", c));
            else b.appendCodePoint(c);
        });
        return b.append('"').toString();
    }

    /** JSON object from raw JSON values; a null value omits the key (absent). */
    static String obj(Map<String, String> raw) {
        StringBuilder b = new StringBuilder("{");
        boolean first = true;
        for (var e : raw.entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) b.append(',');
            first = false;
            b.append(str(e.getKey())).append(':').append(e.getValue());
        }
        return b.append('}').toString();
    }

    /** A valid entry as a mutable field map to be broken by one test case. */
    static Map<String, String> valid(String id, String mode) {
        return new LinkedHashMap<>(new Entry(id, mode, "Label " + id, "characteristics " + id, "instructions " + id).fields());
    }

    /** Same, with one field replaced by a raw JSON value (null = key absent). */
    static String with(Map<String, String> entry, String key, String raw) {
        Map<String, String> m = new LinkedHashMap<>(entry);
        m.put(key, raw);
        return obj(m);
    }

    static String validJson(String id, String mode) {
        return obj(valid(id, mode));
    }

    /** {"profiles":[ … ]} from raw entry JSON. */
    static String file(String... rawEntries) {
        return "{\"profiles\":[" + String.join(",", rawEntries) + "]}";
    }

    static String file(List<String> rawEntries) {
        return file(rawEntries.toArray(String[]::new));
    }

    static String fileOf(List<Entry> entries) {
        return file(entries.stream().map(Entry::json).toList());
    }

    /** The two entries every valid file needs. */
    static List<String> base() {
        return new ArrayList<>(List.of(validJson("official-briefing", "OFFICIAL"), validJson("neutral-narrative", "NARRATIVE")));
    }

    /** official-briefing, neutral-narrative, then the given raw entries. */
    static String baseWith(String... more) {
        List<String> l = base();
        l.addAll(List.of(more));
        return file(l);
    }

    /** A valid file with n entries (official-briefing, neutral-narrative, p3 … pn). */
    static String fileWithCount(int n) {
        List<String> l = base();
        for (int i = 3; i <= n; i++) l.add(validJson("p" + i, "NARRATIVE"));
        return file(l);
    }

    static String repeat(String s, int n) {
        return s.repeat(n);
    }

    static Path write(Path dir, String name, String content) {
        try {
            Path p = dir.resolve(name);
            Files.writeString(p, content, StandardCharsets.UTF_8);
            return p;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String location(Path file) {
        return "file:" + file;
    }
}
