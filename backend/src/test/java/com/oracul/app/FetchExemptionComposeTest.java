package com.oracul.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * FR-56 "only the configured stub host is allowed as an exception": a plain test without Spring context that reads the
 * compose files of the app root ({@code ..} of the backend working directory). The E2E stack exempts exactly the host
 * name {@code stub}; the real stack sets no exemption at all.
 */
// @trace FR-56
class FetchExemptionComposeTest {

    private static final Path ROOT = Paths.get("..").toAbsolutePath().normalize();
    private static final String NAME = "ORACUL_NEWS_FETCH_ALLOWED_PRIVATE_HOSTS";

    private static List<String> lines(String file) throws Exception {
        Path p = ROOT.resolve(file);
        assertThat(p).as(file + " exists").exists();
        return Files.readAllLines(p, StandardCharsets.UTF_8);
    }

    @Test
    void theE2eStackExemptsExactlyTheStubHost() throws Exception {
        List<String> mentions = lines("docker-compose.e2e.yml").stream().filter(l -> l.contains("ALLOWED_PRIVATE_HOSTS")).toList();
        assertThat(mentions).as("exactly one line sets the exemption in docker-compose.e2e.yml").hasSize(1);
        assertThat(mentions.get(0).trim()).matches(NAME + ":\\s*\"?stub\"?\\s*");
    }

    @Test
    void theRealStackSetsNoExemption() throws Exception {
        assertThat(lines("docker-compose.yml")).noneMatch(l -> l.contains("ALLOWED_PRIVATE_HOSTS"));
    }
}
