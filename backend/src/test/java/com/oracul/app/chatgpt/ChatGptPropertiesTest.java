package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// @trace FR-35
class ChatGptPropertiesTest {

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1:99999 bad", "not a uri", "", "/callback", "http://127.0.0.1:abc/callback"})
    void unparseableOrRelativeUriIsRejected(String uri) {
        assertThatThrownBy(() -> ChatGptProperties.requireLoopbackRedirect(uri))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("oracul.chatgpt.redirect-uri must be http://127.0.0.1:<port>/callback");
    }

    @org.junit.jupiter.api.Test
    void nullUriIsRejected() {
        assertThatThrownBy(() -> ChatGptProperties.requireLoopbackRedirect(null))
            .isInstanceOf(IllegalStateException.class);
    }

    @org.junit.jupiter.api.Test
    void scopeListSplitsOnWhitespace() {
        assertThat(ChatGptTestProps.of("http://x", "http://x", "http://x").scopeList())
            .containsExactly("openid", "chatgpt.tokens.use.direct");
    }

    // @trace FR-38
    @org.junit.jupiter.api.Test
    void theRefreshSkewDefaultIsFiveMinutes() {
        var binder = new org.springframework.boot.context.properties.bind.Binder(
            new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(
                java.util.Map.of("oracul.chatgpt.resource", "https://api.openai.com/v1"))); // one value forces binding
        ChatGptProperties defaults = binder.bind("oracul.chatgpt", ChatGptProperties.class).get();
        assertThat(defaults.refreshSkew()).isEqualTo(java.time.Duration.ofMinutes(5));
    }

    // @trace FR-38
    @org.junit.jupiter.api.Test
    void anExplicitRefreshSkewIsBoundAsGiven() {
        var binder = new org.springframework.boot.context.properties.bind.Binder(
            new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(
                java.util.Map.of("oracul.chatgpt.refresh-skew", "PT90S")));
        assertThat(binder.bind("oracul.chatgpt", ChatGptProperties.class).get().refreshSkew())
            .isEqualTo(java.time.Duration.ofSeconds(90));
    }
}
