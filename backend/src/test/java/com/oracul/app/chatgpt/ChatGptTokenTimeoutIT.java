package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** FR-7: a token endpoint that does not answer within http-timeout stores nothing. */
@TestPropertySource(properties = "oracul.chatgpt.http-timeout=PT1S")
class ChatGptTokenTimeoutIT extends AbstractChatGptIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        StubOpenAi.registerAll(r);
    }

    // @trace FR-7
    @Test
    void slowTokenEndpointIsNotCompleted() throws Exception {
        stub.responder = r -> new StubOpenAi.Reply(200, "{\"access_token\":\"at-STUBSECRET-slow\"}", 4000);
        String sid = newSid();
        Started s = start(sid);
        long t0 = System.nanoTime();
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(NOT_COMPLETED);
        assertThat((System.nanoTime() - t0) / 1_000_000).isLessThan(3500);
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
    }
}
