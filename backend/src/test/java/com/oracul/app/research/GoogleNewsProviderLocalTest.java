package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.SearchQueryStatus;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Google News provider rules that need no Spring and no internet: base URL, zero budget, title cleaning (FR-48). */
// @trace FR-48
@Timeout(30)
class GoogleNewsProviderLocalTest {

    private HttpServer server;
    private final List<String> paths = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            paths.add(ex.getRequestURI().getPath());
            byte[] body = ("<rss><channel><item><title>Vaccine approved - Reuters</title><link>https://r.example/a</link>"
                + "<pubDate>Tue, 06 Oct 2026 10:00:00 GMT</pubDate>"
                + "<source url=\"https://r.example\">Reuters</source></item></channel></rss>")
                .getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void trailingSlashesOfTheBaseUrlAreRemovedBeforeTheSearchPathIsAppended() {
        GoogleNewsProvider provider = new GoogleNewsProvider(base() + "///");
        NewsProvider.Result result = provider.search("vaccine", 25, Duration.ofSeconds(5));
        assertThat(paths).containsExactly("/rss/search");
        assertThat(result.status()).isEqualTo(SearchQueryStatus.OK);
        assertThat(result.articles()).hasSize(1);
        assertThat(result.articles().get(0).title()).isEqualTo("Vaccine approved");
        assertThat(result.articles().get(0).sourceName()).isEqualTo("Reuters");
    }

    @Test
    void aZeroOrNegativeTimeoutIsFailedWithoutAnyRequest() {
        GoogleNewsProvider provider = new GoogleNewsProvider(base());
        assertThat(provider.search("vaccine", 25, Duration.ZERO).status()).isEqualTo(SearchQueryStatus.FAILED);
        assertThat(provider.search("vaccine", 25, Duration.ofMillis(-5)).status()).isEqualTo(SearchQueryStatus.FAILED);
        assertThat(paths).isEmpty();
    }

    @Test
    void aMissingTitleStaysMissingAndABlankSourceDoesNotCutTheTitle() {
        assertThat(GoogleNewsProvider.cleanTitle(null, "Reuters")).isNull();
        assertThat(GoogleNewsProvider.cleanTitle(null, null)).isNull();
        assertThat(GoogleNewsProvider.cleanTitle("  Vaccine - Reuters ", " ")).isEqualTo("Vaccine - Reuters");
        assertThat(GoogleNewsProvider.cleanTitle("Vaccine - Reuters", null)).isEqualTo("Vaccine - Reuters");
    }
}
