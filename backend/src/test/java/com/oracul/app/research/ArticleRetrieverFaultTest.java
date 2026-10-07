package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

/**
 * FR-54 "Publisher article retrieval": the failure paths of {@code ArticleRetriever} that a loopback stub cannot produce
 * (fetcher and decoder answers are scripted: refused schemes / addresses, a throwing fetcher, a decoder that interrupts or throws,
 * a used-up decode budget, an undecodable URL, an unknown charset). No network.
 */
// @trace FR-54
@Timeout(60)
class ArticleRetrieverFaultTest {

    private static final String GOOGLE = "https://news.google.com/rss/articles/CBMiX";
    private static final String PUBLISHER = "https://publisher.example/articles/one";
    private static final String GOOGLE_PAGE = "<html><body><div data-n-a-id=\"CBMiX\" data-n-a-ts=\"1759737600\" data-n-a-sg=\"sig\"></div></body></html>";
    private static final String ARTICLE = "<html><head><meta name=\"description\" content=\"d\"></head><body><p>Café naïve text</p></body></html>";

    private final SafeFetcher fetcher = mock(SafeFetcher.class);
    private final ArticleUrlDecoder decoder = mock(ArticleUrlDecoder.class);
    private final Clock clock = Clock.systemUTC();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void attachLog() {
        logs.start();
        ((Logger) LoggerFactory.getLogger(ArticleRetriever.class)).addAppender(logs);
    }

    @AfterEach
    void detachLog() {
        ((Logger) LoggerFactory.getLogger(ArticleRetriever.class)).detachAppender(logs);
        Thread.interrupted(); // never leak an interrupt into the next test
    }

    private ArticleRetriever retriever(Duration timeout, int concurrency) {
        return new ArticleRetriever(fetcher, decoder, "https://news.google.com", timeout, concurrency, clock);
    }

    private SearchBudget budget() {
        return SearchBudget.retrieval(clock, Instant.now(), Duration.ofSeconds(60), null);
    }

    private static SafeFetcher.Result page(String type, byte[] body) {
        return new SafeFetcher.Result(SafeFetcher.Outcome.OK, 200, type, body, false, URI.create("https://x.example/"), 0);
    }

    private static SafeFetcher.Result page(String type, String body) {
        return page(type, body.getBytes(StandardCharsets.UTF_8));
    }

    private static final String GOOGLE_Y = "https://news.google.com/rss/articles/CBMiY";

    /** Each Google link gets its own Google page (data-n-a-id = the last path segment), so a test can pick a link by id, not by call order. */
    private static SafeFetcher.Result googlePageFor(URI uri) {
        String id = uri.toString().endsWith("CBMiY") ? "CBMiY" : "CBMiX";
        return page("text/html", "<html><body><div data-n-a-id=\"" + id + "\" data-n-a-ts=\"1759737600\" data-n-a-sg=\"sig\"></div></body></html>");
    }

    private static SafeFetcher.Result refusal(SafeFetcher.Outcome outcome) {
        return new SafeFetcher.Result(outcome, 0, null, new byte[0], false, URI.create("https://x.example/"), 0);
    }

    private List<String> warnings() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList();
    }

    private List<ArticleRetriever.Outcome> run(ArticleRetriever r, String... links) throws InterruptedException {
        return r.retrieveAll(List.of(links), budget(), () -> true);
    }

    // ---- refusals of the Google link itself ----------------------------------------------------------------------------

    @ParameterizedTest(name = "the Google page fetch ends {0}")
    @EnumSource(value = SafeFetcher.Outcome.class, names = {"REFUSED_SCHEME", "REFUSED_ADDRESS"})
    void aGoogleLinkRefusedByTheSafeFetcherIsRefusedWithoutAPublisherUrl(SafeFetcher.Outcome refused) throws Exception {
        when(fetcher.fetch(any(URI.class), any(Duration.class), anyBoolean())).thenReturn(refusal(refused));
        ArticleRetriever.Outcome o = run(retriever(Duration.ofSeconds(5), 8), GOOGLE).get(0);
        assertThat(o.status()).isEqualTo(ArticleRetriever.Status.REFUSED);
        assertThat(o.publisherUrl()).as("nothing decoded: the url stays the Google link").isNull();
        assertThat(o.body()).isNull();
        verify(fetcher, times(1)).fetch(any(URI.class), any(Duration.class), anyBoolean());
        verify(decoder, never()).decode(any(), any());
    }

    // ---- the decode budget ----------------------------------------------------------------------------------------------

    @Test
    void aDecodeBudgetThatIsUsedUpBeforeTheDecodePostIsADecodeFailureWithReasonTimeout() throws Exception {
        when(fetcher.fetch(any(URI.class), any(Duration.class), anyBoolean())).thenReturn(page("text/html; charset=utf-8", GOOGLE_PAGE));
        ArticleRetriever.Outcome o = run(retriever(Duration.ofNanos(1), 8), GOOGLE).get(0);
        assertThat(o.status()).isEqualTo(ArticleRetriever.Status.DECODE_FAILED);
        assertThat(o.publisherUrl()).isNull();
        verify(decoder, never()).decode(any(), any());
        assertThat(warnings()).containsExactly("article decode failed: timeout");
    }

    // ---- a decoded URL that cannot be fetched -----------------------------------------------------------------------------

    @Test
    void aDecodedUrlThatIsNotAUriIsRefusedAndKeepsTheDecodedUrl() throws Exception {
        when(fetcher.fetch(any(URI.class), any(Duration.class), anyBoolean())).thenReturn(page("text/html", GOOGLE_PAGE));
        when(decoder.decode(any(), any())).thenReturn(new ArticleUrlDecoder.Result("http://exa mple.com/x", null));
        ArticleRetriever.Outcome o = run(retriever(Duration.ofSeconds(5), 8), GOOGLE).get(0);
        assertThat(o.status()).isEqualTo(ArticleRetriever.Status.REFUSED);
        assertThat(o.publisherUrl()).isEqualTo("http://exa mple.com/x");
        verify(fetcher, times(1)).fetch(any(URI.class), any(Duration.class), anyBoolean()); // only the Google page was requested
    }

    // ---- a fetcher that throws ---------------------------------------------------------------------------------------------

    @Test
    void aFetcherThatThrowsForTheGooglePageIsADecodeFailure() throws Exception {
        when(fetcher.fetch(any(URI.class), any(Duration.class), anyBoolean())).thenThrow(new IllegalStateException("boom"));
        ArticleRetriever.Outcome o = run(retriever(Duration.ofSeconds(5), 8), GOOGLE).get(0);
        assertThat(o.status()).isEqualTo(ArticleRetriever.Status.DECODE_FAILED);
        assertThat(o.publisherUrl()).isNull();
        verify(decoder, never()).decode(any(), any());
        assertThat(warnings()).hasSize(1).allMatch(w -> w.startsWith("article decode failed: "));
    }

    @Test
    void aFetcherThatThrowsForThePublisherPageIsAPageFailureThatKeepsThePublisherUrl() throws Exception {
        when(fetcher.fetch(any(URI.class), any(Duration.class), anyBoolean())).thenAnswer(inv -> {
            if ((Boolean) inv.getArgument(2)) {
                return page("text/html", GOOGLE_PAGE);
            }
            throw new IllegalStateException("boom");
        });
        when(decoder.decode(any(), any())).thenReturn(new ArticleUrlDecoder.Result(PUBLISHER, null));
        ArticleRetriever.Outcome o = run(retriever(Duration.ofSeconds(5), 8), GOOGLE).get(0);
        assertThat(o.status()).isEqualTo(ArticleRetriever.Status.PAGE_FAILED);
        assertThat(o.publisherUrl()).isEqualTo(PUBLISHER);
        assertThat(o.body()).isNull();
        assertThat(warnings()).hasSize(1).allMatch(w -> w.startsWith("article fetch failed: "));
    }

    @Test
    void aFetcherThatThrowsForADirectPublisherLinkIsAPageFailure() throws Exception {
        when(fetcher.fetch(any(URI.class), any(Duration.class), anyBoolean())).thenThrow(new IllegalStateException("boom"));
        ArticleRetriever.Outcome o = run(retriever(Duration.ofSeconds(5), 8), PUBLISHER).get(0);
        assertThat(o.status()).isEqualTo(ArticleRetriever.Status.PAGE_FAILED);
        assertThat(o.publisherUrl()).isEqualTo(PUBLISHER);
        verify(decoder, never()).decode(any(), any());
    }

    // ---- the charset of a page ----------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "Content-Type {0}")
    @ValueSource(strings = {"text/html; charset=x-bogus", "text/html; charset=\"x-bogus\"", "text/html; charset=", "text/html"})
    void anUnknownOrMissingCharsetIsReadAsUtf8AndThePageIsStillRead(String contentType) throws Exception {
        when(fetcher.fetch(any(URI.class), any(Duration.class), anyBoolean())).thenReturn(page(contentType, ARTICLE));
        ArticleRetriever.Outcome o = run(retriever(Duration.ofSeconds(5), 8), PUBLISHER).get(0);
        assertThat(o.status()).isEqualTo(ArticleRetriever.Status.PAGE_READ);
        assertThat(o.body()).contains("Café naïve text");
        assertThat(o.description()).isEqualTo("d");
        assertThat(o.contentType()).isEqualTo(contentType);
    }

    @Test
    void aKnownCharsetIsHonoured() throws Exception {
        when(fetcher.fetch(any(URI.class), any(Duration.class), anyBoolean()))
            .thenReturn(page("text/html; charset=ISO-8859-1", ARTICLE.getBytes(StandardCharsets.ISO_8859_1)));
        ArticleRetriever.Outcome o = run(retriever(Duration.ofSeconds(5), 8), PUBLISHER).get(0);
        assertThat(o.status()).isEqualTo(ArticleRetriever.Status.PAGE_READ);
        assertThat(o.body()).contains("Café naïve text");
    }

    @Test
    void aGooglePageWithAnUnknownCharsetIsStillDecoded() throws Exception {
        when(fetcher.fetch(any(URI.class), any(Duration.class), anyBoolean())).thenAnswer(inv ->
            (Boolean) inv.getArgument(2) ? page("text/html; charset=x-bogus", GOOGLE_PAGE) : page("text/html; charset=utf-8", ARTICLE));
        when(decoder.decode(any(), any())).thenReturn(new ArticleUrlDecoder.Result(PUBLISHER, null));
        ArticleRetriever.Outcome o = run(retriever(Duration.ofSeconds(5), 8), GOOGLE).get(0);
        assertThat(o.status()).isEqualTo(ArticleRetriever.Status.PAGE_READ);
        assertThat(o.publisherUrl()).isEqualTo(PUBLISHER);
        verify(decoder, times(1)).decode(org.mockito.ArgumentMatchers.eq(new ArticleUrlDecoder.Attributes("CBMiX", 1759737600L, "sig")), any());
    }

    // ---- an interrupt or an exception while a permit is held ---------------------------------------------------------------

    @Test
    void anInterruptWhileTheDecodeHoldsAPermitIsNotAttemptedAndThePermitIsReleased() throws Exception {
        when(fetcher.fetch(any(URI.class), any(Duration.class), anyBoolean())).thenAnswer(inv ->
            (Boolean) inv.getArgument(2) ? googlePageFor(inv.getArgument(0)) : page("text/html", ARTICLE));
        when(decoder.decode(any(), any())).thenAnswer(inv -> {
            ArticleUrlDecoder.Attributes attrs = inv.getArgument(0);
            if (attrs.id().equals("CBMiX")) {
                Thread.currentThread().interrupt();
            }
            return new ArticleUrlDecoder.Result(PUBLISHER, null);
        });
        // one permit: the second link can only start when the first one has given its permit back (whichever reaches the decoder first)
        List<ArticleRetriever.Outcome> out = run(retriever(Duration.ofSeconds(5), 1), GOOGLE, GOOGLE_Y);
        assertThat(out.get(0).status()).isEqualTo(ArticleRetriever.Status.NOT_ATTEMPTED);
        assertThat(out.get(0).publisherUrl()).as("the decoded url was not taken over").isNull();
        assertThat(out.get(1).status()).isEqualTo(ArticleRetriever.Status.PAGE_READ);
        assertThat(out.get(1).publisherUrl()).isEqualTo(PUBLISHER);
    }

    @Test
    void anInterruptWhileTheGooglePageIsFetchedIsNotAttemptedAndThePermitIsReleased() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        when(fetcher.fetch(any(URI.class), any(Duration.class), anyBoolean())).thenAnswer(inv -> {
            if ((Boolean) inv.getArgument(2)) {
                if (calls.getAndIncrement() == 0) {
                    Thread.currentThread().interrupt();
                }
                return page("text/html", GOOGLE_PAGE);
            }
            return page("text/html", ARTICLE);
        });
        when(decoder.decode(any(), any())).thenReturn(new ArticleUrlDecoder.Result(PUBLISHER, null));
        List<ArticleRetriever.Outcome> out = run(retriever(Duration.ofSeconds(5), 1), GOOGLE, "https://news.google.com/rss/articles/CBMiY");
        assertThat(out.get(0).status()).isEqualTo(ArticleRetriever.Status.NOT_ATTEMPTED);
        assertThat(out.get(0).publisherUrl()).isNull();
        assertThat(out.get(1).status()).isEqualTo(ArticleRetriever.Status.PAGE_READ);
    }

    @Test
    void anExceptionFromTheDecoderIsNotAttemptedAndThePermitIsReleased() throws Exception {
        when(fetcher.fetch(any(URI.class), any(Duration.class), anyBoolean())).thenAnswer(inv ->
            (Boolean) inv.getArgument(2) ? googlePageFor(inv.getArgument(0)) : page("text/html", ARTICLE));
        when(decoder.decode(any(), any())).thenAnswer(inv -> {
            ArticleUrlDecoder.Attributes attrs = inv.getArgument(0);
            if (attrs.id().equals("CBMiX")) {
                throw new IllegalStateException("boom");
            }
            return new ArticleUrlDecoder.Result(PUBLISHER, null);
        });
        List<ArticleRetriever.Outcome> out = run(retriever(Duration.ofSeconds(5), 1), GOOGLE, GOOGLE_Y);
        assertThat(out.get(0).status()).isEqualTo(ArticleRetriever.Status.NOT_ATTEMPTED);
        assertThat(out.get(0).publisherUrl()).isNull();
        assertThat(out.get(1).status()).isEqualTo(ArticleRetriever.Status.PAGE_READ);
    }

    @Test
    void anExceptionFromTheRunGuardWhileThePermitIsHeldIsNotAttemptedWithTheKnownUrlAndThePermitIsReleased() throws Exception {
        when(fetcher.fetch(any(URI.class), any(Duration.class), anyBoolean())).thenReturn(page("text/html", ARTICLE));
        AtomicInteger asked = new AtomicInteger();
        List<String> links = List.of("https://one.example/a", "https://two.example/b");
        List<ArticleRetriever.Outcome> out = retriever(Duration.ofSeconds(5), 1).retrieveAll(links, budget(), () -> {
            if (asked.getAndIncrement() == 0) {
                throw new IllegalStateException("guard broke");
            }
            return true;
        });
        assertThat(out.get(0).status()).isEqualTo(ArticleRetriever.Status.NOT_ATTEMPTED);
        assertThat(out.get(0).publisherUrl()).isEqualTo("https://one.example/a");
        assertThat(out.get(1).status()).as("the single permit was given back").isEqualTo(ArticleRetriever.Status.PAGE_READ);
        verify(fetcher, times(1)).fetch(any(URI.class), any(Duration.class), anyBoolean());
    }

    // ---- links that are no URL -----------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "\"{0}\" is no Google link")
    @ValueSource(strings = {"ht tp://bad host/x", "not a url", "", "%%", "http://exa mple.com/"})
    void aMalformedLinkHasNoHostAndIsNoGoogleLink(String link) {
        assertThat(retriever(Duration.ofSeconds(5), 8).isGoogleLink(link)).isFalse();
    }

    @Test
    void aNullLinkIsNoGoogleLink() {
        assertThat(retriever(Duration.ofSeconds(5), 8).isGoogleLink(null)).isFalse();
    }

    @Test
    void aMalformedConfiguredBaseUrlLeavesOnlyNewsGoogleComAsGoogleHost() {
        ArticleRetriever r = new ArticleRetriever(fetcher, decoder, "ht tp://bad base", Duration.ofSeconds(5), 8, clock);
        assertThat(r.isGoogleLink("https://news.google.com/rss/articles/X")).isTrue();
        assertThat(r.isGoogleLink("http://127.0.0.1:1/x")).isFalse();
        assertThat(r.isGoogleLink("https://www.reuters.com/x")).isFalse();
    }

    @Test
    void aFeedLinkThatIsNoUriIsRefusedWithoutAnyRequest() throws Exception {
        ArticleRetriever.Outcome o = run(retriever(Duration.ofSeconds(5), 8), "not a url").get(0);
        assertThat(o.status()).isEqualTo(ArticleRetriever.Status.REFUSED);
        verify(fetcher, never()).fetch(any(URI.class), any(Duration.class), anyBoolean());
    }
}
