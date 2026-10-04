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
}
