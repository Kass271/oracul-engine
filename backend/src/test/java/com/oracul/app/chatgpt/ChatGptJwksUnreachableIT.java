package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** FR-36: a JWKS endpoint that refuses connections (port 1) makes the ID token invalid. */
class ChatGptJwksUnreachableIT extends AbstractChatGptIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        StubOpenAi.registerAll(r);
        r.add("oracul.chatgpt.jwks-url", () -> "http://127.0.0.1:1/jwks");
    }

    // @trace FR-36
    @Test
    void anUnreachableJwksMakesTheSignInNotVerified() throws Exception {
        resetRegistration();
        String sid = newSid();
        Started s = start(sid);
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(NOT_VERIFIED);
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
        assertThat(jdbc.queryForObject("select client_id from chatgpt_client_registration", String.class)).isNull();
    }
}
