package com.oracul.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * NFR-10 part 3 / FR-61: the E2E stack shortens the publisher fetch to 3 s so that the stub mode {@code publisher-timeout} (answer after 10 s)
 * ends as PAGE_FAILED; the real stack sets nothing. A plain test without Spring context that reads the compose files of the app root
 * ({@code ..} of the backend working directory).
 */
// @trace FR-54
// @trace FR-61
class ArticleFetchTimeoutComposeTest {

    private static final Path ROOT = Paths.get("..").toAbsolutePath().normalize();
    private static final String NAME = "ORACUL_NEWS_ARTICLE_FETCH_TIMEOUT";

    private static List<String> lines(String file) throws Exception {
        Path p = ROOT.resolve(file);
        assertThat(p).as(file + " exists").exists();
        return Files.readAllLines(p, StandardCharsets.UTF_8);
    }

    @Test
    void theE2eStackSetsTheArticleFetchTimeoutToThreeSeconds() throws Exception {
        List<String> mentions = lines("docker-compose.e2e.yml").stream().filter(l -> l.contains(NAME)).toList();
        assertThat(mentions).as("exactly one line sets " + NAME + " in docker-compose.e2e.yml").hasSize(1);
        assertThat(mentions.get(0).trim()).matches(NAME + ":\\s*\"?PT3S\"?\\s*");
    }

    @Test
    void theRealStackSetsNoArticleFetchTimeout() throws Exception {
        assertThat(lines("docker-compose.yml")).noneMatch(l -> l.contains(NAME));
    }
}
