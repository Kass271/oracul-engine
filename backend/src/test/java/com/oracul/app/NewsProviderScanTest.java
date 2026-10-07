package com.oracul.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * phase-03 FR-49 "the former news provider is gone": a plain test without Spring context (it only reads files) that walks
 * the whole scan scope from the app root ({@code ..} of the backend working directory) and fails listing every
 * {@code relative/path:line} that holds the search word in any letter case; plus the removed-property check over
 * {@code backend/src/main/**}. This file is excluded from the scan by its own path (it holds the search word).
 */
// @trace FR-49, FR-52
class NewsProviderScanTest {

    /** The search word, matched case-insensitively. */
    private static final String WORD = "gdelt";

    private static final Path ROOT = Paths.get("..").toAbsolutePath().normalize();
    private static final String SELF = "backend/src/test/java/com/oracul/app/NewsProviderScanTest.java";
    private static final Set<String> SKIPPED_DIRS = Set.of("node_modules", "build", "dist", ".angular", ".gradle", ".git");

    /** Scope: roots (a directory is walked, a file is read) relative to the app root. */
    private static final List<String> SCOPE = List.of(
        "backend/src/main", "backend/src/test", "backend/build.gradle.kts", "backend/settings.gradle.kts",
        "docker-compose.yml", "docker-compose.e2e.yml", "e2e/stubs", "e2e/tests", "e2e/playwright.config.ts",
        "frontend/src", "README.md", ".oracul");

    private static String rel(Path p) {
        return ROOT.relativize(p).toString().replace('\\', '/');
    }

    private static boolean excluded(String rel) {
        return rel.equals(SELF)
            || rel.startsWith("backend/src/main/resources/db/migration/")
            || rel.startsWith("frontend/src/app/api/")
            || rel.startsWith("e2e/report") || rel.startsWith("e2e/test-results");
    }

    private static boolean skippedDir(Path p) {
        return Files.isDirectory(p) && SKIPPED_DIRS.contains(p.getFileName().toString());
    }

    private static boolean binary(byte[] bytes) {
        for (int i = 0; i < Math.min(bytes.length, 8000); i++) {
            if (bytes[i] == 0) return true;
        }
        return false;
    }

    /** Every regular file of the scope (relative path, forward slashes), exclusions applied. */
    private static List<Path> filesOfScope() throws IOException {
        List<Path> out = new ArrayList<>();
        for (String entry : SCOPE) {
            Path start = ROOT.resolve(entry);
            if (!Files.exists(start)) continue;
            try (Stream<Path> walk = Files.walk(start)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> !excluded(rel(p)))
                    .filter(p -> {
                        for (Path part : ROOT.relativize(p)) {
                            if (SKIPPED_DIRS.contains(part.toString())) return false;
                        }
                        return true;
                    })
                    .forEach(out::add);
            }
        }
        return out;
    }

    /** Lines of a text file; null for a binary file. */
    private static List<String> linesOf(Path p) throws IOException {
        byte[] bytes = Files.readAllBytes(p);
        if (binary(bytes)) return null;
        return List.of(new String(bytes, StandardCharsets.UTF_8).split("\r?\n", -1));
    }

    // ---- range (1): the whole scan scope has no match in any letter case --------------------------------------------

    @Test
    void theScanScopeIsFoundAndNotEmpty() throws Exception {
        assertThat(ROOT.resolve("backend")).as("the app root is ..  of the backend working directory: " + ROOT).isDirectory();
        List<String> rels = filesOfScope().stream().map(NewsProviderScanTest::rel).toList();
        assertThat(rels).as("the scope has main and test sources of the backend").anyMatch(r -> r.startsWith("backend/src/main/java/"))
            .anyMatch(r -> r.startsWith("backend/src/test/java/")).contains("README.md", "docker-compose.e2e.yml");
        assertThat(rels).as("exclusions").doesNotContain(SELF).noneMatch(r -> r.startsWith("backend/src/main/resources/db/migration/"));
    }

    @Test
    void noFileOfTheScanScopeNamesTheFormerProviderInAnyLetterCase() throws Exception {
        List<String> hits = new ArrayList<>();
        int files = 0;
        for (Path p : filesOfScope()) {
            List<String> lines = linesOf(p);
            if (lines == null) continue;
            files++;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).toLowerCase(Locale.ROOT).contains(WORD)) hits.add(rel(p) + ":" + (i + 1));
            }
        }
        assertThat(files).as("files scanned").isGreaterThan(100);
        assertThat(hits).as("every file:line that matches (FR-49 scan scope, case-insensitive)").isEmpty();
    }

    // ---- range (3): the removed properties of slice 01 are not read by production code ------------------------------

    static Stream<String> removedKeys() {
        return Stream.of(
            "oracul.news.gdelt.base-url", "oracul.news.request-spacing", "oracul.news.query-timeout",
            "oracul.news.rate-limit-wait", "oracul.news.max-requests", "oracul.news.max-records-per-query",
            "oracul.news.provider");
    }

    private static String envName(String key) {
        return key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }

    private static List<Path> mainFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(ROOT.resolve("backend/src/main"))) {
            return walk.filter(Files::isRegularFile).filter(p -> !rel(p).startsWith("backend/src/main/resources/db/migration/")).toList();
        }
    }

    @ParameterizedTest(name = "removed property {0} (and its env var) is not read in backend/src/main")
    @MethodSource("removedKeys")
    void aRemovedPropertyAppearsNowhereInProductionCode(String key) throws Exception {
        List<String> hits = new ArrayList<>();
        for (Path p : mainFiles()) {
            List<String> lines = linesOf(p);
            if (lines == null) continue;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains(key) || lines.get(i).contains(envName(key))) hits.add(rel(p) + ":" + (i + 1));
            }
        }
        assertThat(hits).as("files:lines of backend/src/main that name " + key).isEmpty();
    }

    @Test
    void noConfigurationPropertiesClassRejectsUnknownKeys() throws Exception {
        List<String> hits = new ArrayList<>();
        for (Path p : mainFiles()) {
            List<String> lines = linesOf(p);
            if (lines == null) continue;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).replaceAll("\\s+", "").contains("ignoreUnknownFields=false")) hits.add(rel(p) + ":" + (i + 1));
            }
        }
        assertThat(hits).as("a removed property that is still set must be ignored, so no ignoreUnknownFields = false").isEmpty();
    }
}
