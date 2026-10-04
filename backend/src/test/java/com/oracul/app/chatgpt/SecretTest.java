package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.json.JsonMapper;

// @trace FR-36
class SecretTest {

    @org.junit.jupiter.api.Test
    void toStringIsRedactedAndValueIsKept() {
        Secret s = new Secret("abc");
        assertThat(s.toString()).isEqualTo("[REDACTED]").doesNotContain("abc");
        assertThat(s.value()).isEqualTo("abc");
    }

    @org.junit.jupiter.api.Test
    void serialisingASecretIsRefused() {
        JsonMapper mapper = JsonMapper.builder().build();
        assertThatThrownBy(() -> mapper.writeValueAsString(new Secret("topsecret")))
            .hasMessageContaining("a Secret must never be serialised")
            .satisfies(e -> assertThat(e.getMessage()).doesNotContain("topsecret"));
    }
}
