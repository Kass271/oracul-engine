package com.oracul.app.style;

import static com.oracul.app.style.StyleFixtures.base;
import static com.oracul.app.style.StyleFixtures.baseWith;
import static com.oracul.app.style.StyleFixtures.file;
import static com.oracul.app.style.StyleFixtures.fileWithCount;
import static com.oracul.app.style.StyleFixtures.location;
import static com.oracul.app.style.StyleFixtures.repeat;
import static com.oracul.app.style.StyleFixtures.str;
import static com.oracul.app.style.StyleFixtures.valid;
import static com.oracul.app.style.StyleFixtures.validJson;
import static com.oracul.app.style.StyleFixtures.with;
import static com.oracul.app.style.StyleFixtures.write;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * FR-62: the style file is validated once at startup; the first problem (in the order of the spec table) stops the
 * backend with an IllegalStateException carrying exactly one message. Every class of the spec's "Ranges & invariants"
 * is walked: counts, id pattern, mode, the three strings (presence, length, forbidden characters), precedence and the
 * two file-level rules. No database, no web server: only the WritingStyles bean in an ApplicationContextRunner.
 */
class WritingStylesStartupTest {

    @TempDir
    Path dir;

    private static final String ENTRY = "Writing style entry #";

    private static Function<String, String> entry(int n, String id, String problem) {
        return loc -> ENTRY + n + " (" + id + "): " + problem;
    }

    private static Function<String, String> missingId(int n) {
        return loc -> ENTRY + n + ": missing id";
    }

    private static Function<String, String> cannotRead() {
        return loc -> "Writing style file " + loc + " cannot be read";
    }

    private static Function<String, String> count() {
        return loc -> "Writing style file " + loc + " must have 2 to 50 profiles";
    }

    private static Function<String, String> noOfficial() {
        return loc -> "Writing style file " + loc + " must have exactly one OFFICIAL profile, official-briefing";
    }

    private static Function<String, String> noNeutral() {
        return loc -> "Writing style file " + loc + " must have the NARRATIVE profile neutral-narrative";
    }

    private void assertFails(String location, String expected) {
        StyleSeam.stylesOnly(location).run(ctx -> {
            Throwable root = StyleSeam.rootCause(ctx);
            assertThat(root).isInstanceOf(IllegalStateException.class);
            assertThat(root.getMessage()).isEqualTo(expected);
        });
    }

    private void assertStarts(String location) {
        StyleSeam.stylesOnly(location).run(ctx -> assertThat(ctx).hasNotFailed());
    }

    private void assertFileFails(String content, Function<String, String> message) {
        String loc = location(write(dir, "styles.json", content));
        assertFails(loc, message.apply(loc));
    }

    // ---- the shipped file -------------------------------------------------------------------------------------

    // @trace FR-62
    @Test
    void shippedFileStartsTheBean() {
        assertStarts(null);
    }

    // @trace FR-62
    @Test
    void shippedFileIsReadFromTheDefaultLocation() {
        assertThat(StyleSeam.DEFAULT_LOCATION).isEqualTo("classpath:writing-styles.json");
        StyleSeam.stylesOnly(StyleSeam.DEFAULT_LOCATION).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(StyleSeam.profiles(ctx.getBean(StyleSeam.stylesClass()))).hasSize(9);
        });
    }

    // ---- file-level: cannot be read ---------------------------------------------------------------------------

    static Stream<Arguments> unreadableFiles() {
        return Stream.of(
            Arguments.of("empty file", ""),
            Arguments.of("whitespace only", "  \n"),
            Arguments.of("not JSON", "this is not json"),
            Arguments.of("truncated JSON", "{\"profiles\":["),
            Arguments.of("top level array", "[]"),
            Arguments.of("top level array of entries", "[" + validJson("official-briefing", "OFFICIAL") + "]"),
            Arguments.of("top level string", "\"profiles\""),
            Arguments.of("top level number", "5"),
            Arguments.of("top level null", "null"),
            Arguments.of("no profiles key", "{}"),
            Arguments.of("other key only", "{\"entries\":[]}"),
            Arguments.of("profiles null", "{\"profiles\":null}"),
            Arguments.of("profiles object", "{\"profiles\":{}}"),
            Arguments.of("profiles string", "{\"profiles\":\"x\"}"),
            Arguments.of("profiles number", "{\"profiles\":5}"));
    }

    // @trace FR-62
    @ParameterizedTest(name = "cannot be read: {0}")
    @MethodSource("unreadableFiles")
    void unreadableFileStopsTheBackend(String name, String content) {
        assertFileFails(content, cannotRead());
    }

    // @trace FR-62
    @Test
    void missingFileUnderFileLocationStopsTheBackend() {
        String loc = location(dir.resolve("does-not-exist.json"));
        assertFails(loc, "Writing style file " + loc + " cannot be read");
    }

    // @trace FR-62
    @Test
    void missingClasspathResourceStopsTheBackend() {
        String loc = "classpath:writing-styles/does-not-exist.json";
        assertFails(loc, "Writing style file " + loc + " cannot be read");
    }

    // @trace FR-62
    @Test
    void extraTopLevelKeysAndUnknownEntryKeysAreIgnored() {
        List<String> entries = new ArrayList<>(List.of(
            with(valid("official-briefing", "OFFICIAL"), "extra", "1"),
            with(valid("neutral-narrative", "NARRATIVE"), "author", "\"x\"")));
        assertStarts(location(write(dir, "styles.json", "{\"version\":2,\"profiles\":[" + String.join(",", entries) + "]}")));
    }

    // ---- count 2..50 --------------------------------------------------------------------------------------------

    static Stream<Arguments> badCounts() {
        return Stream.of(
            Arguments.of("0 entries", "{\"profiles\":[]}"),
            Arguments.of("1 entry", file(validJson("official-briefing", "OFFICIAL"))),
            Arguments.of("1 broken entry (count is checked before any entry)", "{\"profiles\":[5]}"),
            Arguments.of("51 entries", fileWithCount(51)),
            Arguments.of("51 entries, broken first (count before entries)", file(count51WithBrokenFirst())));
    }

    private static List<String> count51WithBrokenFirst() {
        List<String> l = new ArrayList<>(List.of("5"));
        IntStream.rangeClosed(2, 51).forEach(i -> l.add(validJson("p" + i, "NARRATIVE")));
        return l;
    }

    // @trace FR-62
    @ParameterizedTest(name = "profile count: {0}")
    @MethodSource("badCounts")
    void profileCountOutsideTwoToFiftyStopsTheBackend(String name, String content) {
        assertFileFails(content, count());
    }

    // @trace FR-62
    @ParameterizedTest(name = "profile count {0} starts")
    @MethodSource("goodCounts")
    void profileCountWithinTwoToFiftyStarts(int n) {
        assertStarts(location(write(dir, "styles.json", fileWithCount(n))));
    }

    static Stream<Integer> goodCounts() {
        return Stream.of(2, 3, 10, 49, 50);
    }

    // ---- entry: id ---------------------------------------------------------------------------------------------

    static Stream<Arguments> missingIds() {
        Map<String, String> v = valid("x", "NARRATIVE");
        return Stream.of(
            Arguments.of("entry null", "null"),
            Arguments.of("entry number", "5"),
            Arguments.of("entry string", "\"x\""),
            Arguments.of("entry array", "[]"),
            Arguments.of("id absent", with(v, "id", null)),
            Arguments.of("id null", with(v, "id", "null")),
            Arguments.of("id number", with(v, "id", "5")),
            Arguments.of("id boolean", with(v, "id", "true")),
            Arguments.of("id array", with(v, "id", "[\"x\"]")),
            Arguments.of("id empty", with(v, "id", "\"\"")),
            Arguments.of("id blank", with(v, "id", "\"  \"")));
    }

    // @trace FR-62
    @ParameterizedTest(name = "missing id: {0}")
    @MethodSource("missingIds")
    void entryWithoutUsableIdStopsTheBackend(String name, String badEntry) {
        assertFileFails(baseWith(badEntry), missingId(3));
    }

    // @trace FR-62
    @ParameterizedTest(name = "invalid id [{0}]")
    @MethodSource("invalidIds")
    void idOutsideThePatternStopsTheBackend(String id) {
        assertFileFails(baseWith(validJson(id, "NARRATIVE")), entry(3, id, "invalid id"));
    }

    static Stream<String> invalidIds() {
        return Stream.of("Kafka", "k_a", " kafka", "kafka ", "ka fka", "KAFKA", "kafka.1", "kafká", repeat("a", 41), repeat("a", 100));
    }

    // @trace FR-62
    @ParameterizedTest(name = "valid id [{0}]")
    @MethodSource("validIds")
    void idInsideThePatternStarts(String id) {
        assertStarts(location(write(dir, "styles.json", baseWith(validJson(id, "NARRATIVE")))));
    }

    static Stream<String> validIds() {
        return Stream.of("a", "0", "-", "a-b", "k-1", "poe", "dickens", repeat("a", 40), repeat("9", 40), repeat("-", 40));
    }

    // @trace FR-62
    @Test
    void duplicateIdAtPositionNineNamesEntryNine() {
        List<String> l = base();
        for (int i = 3; i <= 8; i++) l.add(validJson("p" + i, "NARRATIVE"));
        l.add(validJson("neutral-narrative", "NARRATIVE"));
        assertFileFails(file(l), entry(9, "neutral-narrative", "duplicate id"));
    }

    // @trace FR-62
    @Test
    void duplicateIdWithADifferentModeIsStillADuplicate() {
        assertFileFails(baseWith(validJson("official-briefing", "NARRATIVE")), entry(3, "official-briefing", "duplicate id"));
    }

    // @trace FR-62
    @Test
    void uppercaseVariantOfAnEarlierIdIsAnInvalidIdNotADuplicate() {
        // "Kafka" is not a valid id at all, so the pattern rule reports it, not "duplicate id"
        assertFileFails(baseWith(validJson("kafka", "NARRATIVE"), validJson("Kafka", "NARRATIVE")), entry(4, "Kafka", "invalid id"));
    }

    // ---- entry: mode -------------------------------------------------------------------------------------------

    static Stream<Arguments> badModes() {
        Map<String, String> v = valid("kafka", "NARRATIVE");
        return Stream.of(
            Arguments.of("absent", with(v, "mode", null)),
            Arguments.of("null", with(v, "mode", "null")),
            Arguments.of("number", with(v, "mode", "1")),
            Arguments.of("boolean", with(v, "mode", "true")),
            Arguments.of("array", with(v, "mode", "[\"NARRATIVE\"]")),
            Arguments.of("lower case official", with(v, "mode", "\"official\"")),
            Arguments.of("lower case narrative", with(v, "mode", "\"narrative\"")),
            Arguments.of("capitalised Narrative", with(v, "mode", "\"Narrative\"")),
            Arguments.of("POETIC", with(v, "mode", "\"POETIC\"")),
            Arguments.of("empty", with(v, "mode", "\"\"")),
            Arguments.of("padded", with(v, "mode", "\" NARRATIVE\"")));
    }

    // @trace FR-62
    @ParameterizedTest(name = "unknown mode: {0}")
    @MethodSource("badModes")
    void modeOutsideOfficialAndNarrativeStopsTheBackend(String name, String badEntry) {
        assertFileFails(baseWith(badEntry), entry(3, "kafka", "unknown mode"));
    }

    // ---- entry: presence of label / characteristics / instructions ----------------------------------------------

    private static final Map<String, String> PROBLEM_NAME = Map.of(
        "label", "missing label", "characteristics", "missing characteristics", "instructions", "missing prompt text");

    static Stream<Arguments> missingStrings() {
        Map<String, String> v = valid("kafka", "NARRATIVE");
        String[] raws = {null, "null", "5", "true", "[\"x\"]", "{}", "\"\"", "\"   \""};
        String[] names = {"absent", "null", "number", "boolean", "array", "object", "empty", "blank"};
        List<Arguments> out = new ArrayList<>();
        for (String field : List.of("label", "characteristics", "instructions")) {
            for (int i = 0; i < raws.length; i++) {
                out.add(Arguments.of(field, names[i], with(v, field, raws[i])));
            }
        }
        return out.stream();
    }

    // @trace FR-62
    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("missingStrings")
    void missingTextFieldStopsTheBackend(String field, String kind, String badEntry) {
        assertFileFails(baseWith(badEntry), entry(3, "kafka", PROBLEM_NAME.get(field)));
    }

    // ---- entry: lengths in code points ---------------------------------------------------------------------------

    private static final Map<String, Integer> MAX = Map.of("label", 80, "characteristics", 300, "instructions", 2000);

    static Stream<Arguments> lengthBoundaries() {
        Map<String, String> v = valid("kafka", "NARRATIVE");
        List<Arguments> out = new ArrayList<>();
        for (String field : List.of("label", "characteristics", "instructions")) {
            int max = MAX.get(field);
            for (int len : new int[] {1, max - 1, max}) {
                out.add(Arguments.of(field, len, "x", with(v, field, str(repeat("x", len)))));
                out.add(Arguments.of(field, len, "emoji (2 UTF-16 units per code point)", with(v, field, str(repeat("😀", len)))));
                out.add(Arguments.of(field, len, "Cyrillic", with(v, field, str(repeat("ж", len)))));
            }
        }
        return out.stream();
    }

    // @trace FR-62
    @ParameterizedTest(name = "{0} of {1} code points ({2}) is accepted")
    @MethodSource("lengthBoundaries")
    void textAtOrBelowTheLimitIsAccepted(String field, int len, String kind, String goodEntry) {
        assertStarts(location(write(dir, "styles.json", baseWith(goodEntry))));
    }

    static Stream<Arguments> tooLong() {
        Map<String, String> v = valid("kafka", "NARRATIVE");
        List<Arguments> out = new ArrayList<>();
        for (String field : List.of("label", "characteristics", "instructions")) {
            int max = MAX.get(field);
            for (int len : new int[] {max + 1, max + 2, max * 2}) {
                out.add(Arguments.of(field, len, "x", with(v, field, str(repeat("x", len)))));
                out.add(Arguments.of(field, len, "emoji", with(v, field, str(repeat("😀", len)))));
            }
        }
        return out.stream();
    }

    // @trace FR-62
    @ParameterizedTest(name = "{0} of {1} code points ({2}) is too long")
    @MethodSource("tooLong")
    void textAboveTheLimitStopsTheBackend(String field, int len, String kind, String badEntry) {
        assertFileFails(baseWith(badEntry), entry(3, "kafka", field + " is too long"));
    }

    // ---- entry: forbidden characters -----------------------------------------------------------------------------

    private static final String[][] FORBIDDEN = {
        {"line feed", "a\nb"}, {"carriage return", "a\rb"}, {"tab", "a\tb"}, {"NUL", "a\u0000b"},
        {"unit separator U+001F", "a\u001fb"}, {"DEL U+007F", "a\u007fb"}, {"NEL U+0085", "a\u0085b"},
        {"U+009F", "a\u009fb"}, {"line separator U+2028", "a b"}, {"paragraph separator U+2029", "a b"},
        {"<<<", "a<<<b"}, {">>>", "a>>>b"}, {"<<<<", "<<<<"}, {"only >>>", ">>>"},
    };

    static Stream<Arguments> forbidden() {
        Map<String, String> v = valid("kafka", "NARRATIVE");
        List<Arguments> out = new ArrayList<>();
        for (String field : List.of("label", "characteristics", "instructions")) {
            for (String[] f : FORBIDDEN) {
                out.add(Arguments.of(field, f[0], with(v, field, str(f[1]))));
            }
        }
        return out.stream();
    }

    // @trace FR-62
    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("forbidden")
    void forbiddenCharactersStopTheBackend(String field, String what, String badEntry) {
        assertFileFails(baseWith(badEntry), entry(3, "kafka", field + " contains forbidden characters"));
    }

    static Stream<Arguments> allowedSpecials() {
        Map<String, String> v = valid("kafka", "NARRATIVE");
        List<Arguments> out = new ArrayList<>();
        for (String field : List.of("label", "characteristics", "instructions")) {
            for (String s : new String[] {"a<<b", "a>>b", "a<>b", "a< <<b", "a>> >b", "<< >>", "— dash", "quote's \"inner\"", "back\\slash", " nbsp", "  padded  "}) {
                out.add(Arguments.of(field, s, with(v, field, str(s))));
            }
        }
        return out.stream();
    }

    // @trace FR-62
    @ParameterizedTest(name = "{0} may contain [{1}]")
    @MethodSource("allowedSpecials")
    void doubleAnglesQuotesAndPaddingAreAllowed(String field, String text, String goodEntry) {
        assertStarts(location(write(dir, "styles.json", baseWith(goodEntry))));
    }

    // ---- precedence inside one entry and across entries ------------------------------------------------------------

    static Stream<Arguments> precedence() {
        String longText = repeat("x", 2001);
        Map<String, String> v = valid("Kafka", "POETIC");
        Map<String, String> ok = valid("kafka", "NARRATIVE");
        Map<String, String> bad = new java.util.LinkedHashMap<>(v);
        bad.put("label", "\"\"");
        bad.put("characteristics", "null");
        bad.put("instructions", str("a<<<b"));
        return Stream.of(
            Arguments.of("id pattern before mode and strings", obj(bad), entry(3, "Kafka", "invalid id")),
            Arguments.of("mode before label",
                obj(withAll(ok, Map.of("mode", "\"x\"", "label", "\"\""))), entry(3, "kafka", "unknown mode")),
            Arguments.of("label before characteristics",
                obj(withAll(ok, Map.of("label", "null", "characteristics", "null"))), entry(3, "kafka", "missing label")),
            Arguments.of("characteristics before instructions",
                obj(withAll(ok, Map.of("characteristics", "\"\"", "instructions", "null"))), entry(3, "kafka", "missing characteristics")),
            Arguments.of("presence before length (label too long, instructions missing)",
                obj(withAll(ok, Map.of("label", str(repeat("x", 81)), "instructions", "\"\""))), entry(3, "kafka", "missing prompt text")),
            Arguments.of("presence before forbidden characters",
                obj(withAll(ok, Map.of("label", str("a\nb"), "characteristics", "null"))), entry(3, "kafka", "missing characteristics")),
            Arguments.of("label length before characteristics length",
                obj(withAll(ok, Map.of("label", str(repeat("x", 81)), "characteristics", str(repeat("x", 301))))), entry(3, "kafka", "label is too long")),
            Arguments.of("characteristics length before instructions length",
                obj(withAll(ok, Map.of("characteristics", str(repeat("x", 301)), "instructions", str(longText)))), entry(3, "kafka", "characteristics is too long")),
            Arguments.of("length before forbidden characters (instructions too long, label has a line break)",
                obj(withAll(ok, Map.of("label", str("a\nb"), "instructions", str(longText)))), entry(3, "kafka", "instructions is too long")),
            Arguments.of("forbidden: label before characteristics before instructions",
                obj(withAll(ok, Map.of("characteristics", str("a>>>b"), "instructions", str("a\nb")))), entry(3, "kafka", "characteristics contains forbidden characters")));
    }

    private static Map<String, String> withAll(Map<String, String> base, Map<String, String> changes) {
        Map<String, String> m = new java.util.LinkedHashMap<>(base);
        m.putAll(changes);
        return m;
    }

    private static String obj(Map<String, String> raw) {
        return StyleFixtures.obj(raw);
    }

    // @trace FR-62
    @ParameterizedTest(name = "{0}")
    @MethodSource("precedence")
    void anEntryWithSeveralProblemsReportsTheFirstInTableOrder(String name, String badEntry, Function<String, String> message) {
        assertFileFails(baseWith(badEntry), message);
    }

    // @trace FR-62
    @Test
    void aBadEntryTwoAndABadEntryFiveReportEntryTwo() {
        List<String> l = new ArrayList<>();
        l.add(validJson("official-briefing", "OFFICIAL"));
        l.add(with(valid("bad-two", "NARRATIVE"), "label", "\"\""));
        l.add(validJson("neutral-narrative", "NARRATIVE"));
        l.add(validJson("p4", "NARRATIVE"));
        l.add(with(valid("bad-five", "NARRATIVE"), "mode", "\"x\""));
        assertFileFails(file(l), entry(2, "bad-two", "missing label"));
    }

    // @trace FR-62
    @Test
    void anEntryProblemWinsOverAFileLevelProblem() {
        // no official-briefing at all AND a broken entry: the entry is reported
        assertFileFails(file(validJson("neutral-narrative", "NARRATIVE"), with(valid("kafka", "NARRATIVE"), "label", "null")),
            entry(2, "kafka", "missing label"));
    }

    // ---- file-level rules -------------------------------------------------------------------------------------------

    static Stream<Arguments> officialRule() {
        return Stream.of(
            Arguments.of("no official-briefing", file(validJson("neutral-narrative", "NARRATIVE"), validJson("kafka", "NARRATIVE"))),
            Arguments.of("official-briefing as NARRATIVE", file(validJson("official-briefing", "NARRATIVE"), validJson("neutral-narrative", "NARRATIVE"))),
            Arguments.of("an OFFICIAL entry with another id instead", file(validJson("briefing", "OFFICIAL"), validJson("neutral-narrative", "NARRATIVE"))),
            Arguments.of("neutral-narrative is the second OFFICIAL entry", file(validJson("official-briefing", "OFFICIAL"), validJson("neutral-narrative", "OFFICIAL"))),
            Arguments.of("dickens is a second OFFICIAL entry", baseWith(validJson("dickens", "OFFICIAL"))),
            Arguments.of("two other OFFICIAL entries, no official-briefing", file(validJson("a", "OFFICIAL"), validJson("b", "OFFICIAL"), validJson("neutral-narrative", "NARRATIVE"))),
            Arguments.of("neither rule satisfied: OFFICIAL is reported first", file(validJson("kafka", "NARRATIVE"), validJson("poe", "NARRATIVE"))),
            Arguments.of("second OFFICIAL entry and no neutral-narrative: OFFICIAL is reported first",
                file(validJson("official-briefing", "OFFICIAL"), validJson("dickens", "OFFICIAL"))));
    }

    // @trace FR-62
    @ParameterizedTest(name = "{0}")
    @MethodSource("officialRule")
    void exactlyOneOfficialProfileIsRequired(String name, String content) {
        assertFileFails(content, noOfficial());
    }

    static Stream<Arguments> narrativeRule() {
        return Stream.of(
            Arguments.of("only official-briefing and an author", file(validJson("official-briefing", "OFFICIAL"), validJson("kafka", "NARRATIVE"))),
            Arguments.of("neutral-narrative is missing among many", file(validJson("official-briefing", "OFFICIAL"), validJson("poe", "NARRATIVE"), validJson("kafka", "NARRATIVE"), validJson("wells", "NARRATIVE"))));
    }

    // @trace FR-62
    @ParameterizedTest(name = "{0}")
    @MethodSource("narrativeRule")
    void theNeutralNarrativeProfileIsRequired(String name, String content) {
        assertFileFails(content, noNeutral());
    }

    // @trace FR-62
    @Test
    void locationInTheMessageIsThePropertyValueVerbatim() {
        // a classpath location: the message repeats it exactly, not a resolved path
        String loc = "classpath:writing-styles/too-few-" + System.nanoTime() + ".json";
        assertFails(loc, "Writing style file " + loc + " cannot be read");
    }

    // @trace FR-62
    @Test
    void fileOrderDoesNotMatterForTheFileLevelRules() {
        assertStarts(location(write(dir, "styles.json", file(
            validJson("kafka", "NARRATIVE"), validJson("neutral-narrative", "NARRATIVE"),
            validJson("official-briefing", "OFFICIAL"), validJson("poe", "NARRATIVE")))));
    }
}
