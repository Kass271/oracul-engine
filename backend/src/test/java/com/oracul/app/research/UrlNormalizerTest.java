package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// @trace FR-13
class UrlNormalizerTest {

    @Test
    void normalizesSchemeHostPortFragmentAndTrackingParameters() {
        assertThat(UrlNormalizer.normalize(" HTTPS://User:p@Example.COM:443/a/b?x=1&utm_source=z&UTM_x&flag&&y=2#frag"))
            .isEqualTo("https://User:p@example.com/a/b?x=1&flag&y=2");
        assertThat(UrlNormalizer.normalize("http://example.com:80/")).isEqualTo("http://example.com/");
        assertThat(UrlNormalizer.normalize("http://example.com:8080/p?utm_a=1")).isEqualTo("http://example.com:8080/p");
    }

    @Test
    void rejectsUnusableInput() {
        assertThat(UrlNormalizer.normalize(null)).isNull();
        assertThat(UrlNormalizer.normalize("  ")).isNull();
        assertThat(UrlNormalizer.normalize("ftp://example.com/")).isNull();
        assertThat(UrlNormalizer.normalize("/relative/path")).isNull();
        assertThat(UrlNormalizer.normalize("http:///nohost")).isNull();
        assertThat(UrlNormalizer.normalize("http://exa mple.com/")).isNull();
    }

    @Test
    void hostStripsWwwAndToleratesGarbage() {
        assertThat(UrlNormalizer.host("https://WWW.Example.com/x")).isEqualTo("example.com");
        assertThat(UrlNormalizer.host("https://news.example.com/x")).isEqualTo("news.example.com");
        assertThat(UrlNormalizer.host("/relative")).isEmpty();
        assertThat(UrlNormalizer.host("http://exa mple.com")).isEmpty();
    }
}
