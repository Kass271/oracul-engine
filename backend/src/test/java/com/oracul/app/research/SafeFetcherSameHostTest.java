package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.research.SafeFetcherSupport.Fx;
import com.oracul.app.research.SafeFetcherSupport.Res;
import com.oracul.app.research.SafeFetcherSupport.Resolver;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * article-retrieval.md slice 08 "SafeFetcher (additive)": {@code fetch(URI, Duration, boolean sameHostOnly)} follows a redirect only while its
 * resolved Location keeps the host and the effective port (the Google page of FR-54 step 2), returns the 3xx answer itself otherwise
 * (status as answered, {@code finalUri} = the answering hop), and every hop sends the User-Agent of the Google News client. Against the
 * in-process stub, through {@link SafeFetcherSupport} (the three-argument method does not exist before the slice is built).
 */
// @trace FR-54
@Timeout(60)
class SafeFetcherSameHostTest {

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; ORACUL/1.0)";
    private static final Duration T5 = Duration.ofSeconds(5);

    private final StubNews news = StubNews.INSTANCE;

    @BeforeEach
    void reset() {
        news.reset();
    }

    /** Both host names reach the stub and are exempt from the address check, so only the same-host rule decides what is followed. */
    private Fx fetcher() {
        Resolver resolver = new Resolver().map("localhost", "127.0.0.1");
        return SafeFetcherSupport.fetcher(resolver, Set.of("127.0.0.1", "localhost"), T5, 2 * 1024 * 1024, 5);
    }

    private String viaRedirect(String location) {
        return news.baseUrl() + "/redirect-to?location=" + URLEncoder.encode(location, StandardCharsets.UTF_8);
    }

    static Stream<Arguments> offHostLocations() {
        // another host name, another port, a protocol-relative location of another host
        return Stream.of(
            Arguments.of("another host", "http://localhost:{port}/articles/x"),
            Arguments.of("another port", "http://127.0.0.1:1/articles/x"),
            Arguments.of("protocol-relative, another host", "//localhost:{port}/articles/x"));
    }

    @ParameterizedTest(name = "sameHostOnly, Location with {0}: the 3xx answer itself, nothing is requested at the other address")
    @MethodSource("offHostLocations")
    void aRedirectToAnotherHostOrPortIsNotFollowed(String name, String location) {
        String url = viaRedirect(location.replace("{port}", String.valueOf(news.port())));
        Res r = fetcher().fetch(url, T5, true);
        assertThat(r.outcome()).as(name).isEqualTo("OK");
        assertThat(r.status()).as("the redirect answer itself").isEqualTo(302);
        assertThat(r.finalUri().toString()).as("finalUri = the answering hop").isEqualTo(url);
        assertThat(news.paths).as("only the first hop reached the stub").containsExactly("/redirect-to");
    }

    @Test
    void sameHostRedirectsAreFollowedUpToFiveAndAnswerOk() {
        Res r = fetcher().fetch(news.baseUrl() + "/redirect/3", T5, true);
        assertThat(r.outcome()).isEqualTo("OK");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.finalUri().getPath()).isEqualTo("/redirect/0");
        assertThat(r.redirects()).isEqualTo(3);
        Res tooMany = fetcher().fetch(news.baseUrl() + "/redirect/6", T5, true);
        assertThat(tooMany.outcome()).as("more than 5 redirects").isEqualTo("TOO_MANY_REDIRECTS");
    }

    @Test
    void withoutTheFlagARedirectIsFollowedWhateverItsHost() {
        // sameHostOnly = false for the existing methods: the redirect to the other host name of the stub is followed to its page
        String target = "http://localhost:" + news.port() + "/articles/followed";
        Res r = fetcher().fetch(viaRedirect(target), T5);
        assertThat(r.outcome()).isEqualTo("OK");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.finalUri().getHost()).isEqualTo("localhost");
        assertThat(r.finalUri().getPath()).isEqualTo("/articles/followed");
        Res explicit = fetcher().fetch(viaRedirect(target), T5, false);
        assertThat(explicit.status()).isEqualTo(200);
        assertThat(explicit.finalUri().getPath()).isEqualTo("/articles/followed");
        Res sameHost = fetcher().fetch(viaRedirect(target), T5, true);
        assertThat(sameHost.status()).as("with the flag the same redirect is the answer").isEqualTo(302);
    }

    @Test
    void everyHopSendsTheUserAgentOfTheGoogleNewsClient() {
        Res r = fetcher().fetch(news.baseUrl() + "/redirect/2", T5, true);
        assertThat(r.outcome()).isEqualTo("OK");
        assertThat(news.userAgents).hasSize(3);
        assertThat(news.userAgents).allSatisfy(l -> assertThat(l).endsWith(" " + USER_AGENT));
    }
}
