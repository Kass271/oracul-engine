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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

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

    // ---- FR-53 feed data: Article.snippet from the item's <description> ------------------------------------------

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String feedWithDescription(String descriptionHtml) {
        return "<rss><channel><item><title>Vaccine approved - Reuters</title><link>https://r.example/a</link>"
            + "<pubDate>Tue, 06 Oct 2026 10:00:00 GMT</pubDate><source url=\"https://r.example\">Reuters</source>"
            + (descriptionHtml == null ? "" : "<description>" + xml(descriptionHtml) + "</description>") + "</item></channel></rss>";
    }

    static Stream<Arguments> descriptions() {
        return Stream.of(
            Arguments.of("<b>Bold</b> text", "Bold text"),
            Arguments.of("Tom &amp; Jerry", "Tom & Jerry"),
            Arguments.of("&lt;not a tag&gt; here", "<not a tag> here"),
            Arguments.of("She said &quot;hi&quot; &#39;x&#39; &apos;y&apos;", "She said \"hi\" 'x' 'y'"),
            Arguments.of("&#65;&#x42;&#x63;", "ABc"),
            Arguments.of("  a \n\t b   c  ", "a b c"),
            Arguments.of("A&nbsp;B", "A B"),
            Arguments.of("<a href=\"https://news.google.com/rss/articles/CBM\" target=\"_blank\">Vaccine approved</a>&nbsp;&nbsp;"
                + "<font color=\"#6f6f6f\">Reuters</font>", "Vaccine approved Reuters"),
            Arguments.of("   ", null),
            Arguments.of("<br/><p></p>", null),
            Arguments.of(null, null));
    }

    // @trace FR-53
    @ParameterizedTest(name = "description {0} -> snippet {1}")
    @MethodSource("descriptions")
    void theSnippetIsTheDescriptionAsPlainText(String descriptionHtml, String expected) {
        NewsProvider.Result result = GoogleNewsProvider.parse(feedWithDescription(descriptionHtml), 25);
        assertThat(result.status()).isEqualTo(SearchQueryStatus.OK);
        assertThat(result.articles()).hasSize(1);
        String snippet = SelectorApi.snippetOf(result.articles().get(0));
        // a no-break space decodes to a space of some kind: compare with it normalised
        assertThat(snippet == null ? null : snippet.replace('\u00a0', ' ').replaceAll("\\s+", " ")).isEqualTo(expected);
        assertThat(result.articles().get(0).title()).as("the other fields are unchanged").isEqualTo("Vaccine approved");
    }
}
