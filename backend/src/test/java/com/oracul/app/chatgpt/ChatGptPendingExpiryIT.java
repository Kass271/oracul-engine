package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** FR-7 errors that need a different configuration: expired pending entries (PT0S). */
@TestPropertySource(properties = "oracul.chatgpt.pending-ttl=PT0S")
class ChatGptPendingExpiryIT extends AbstractChatGptIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        StubOpenAi.registerAll(r);
    }

    // @trace FR-7
    @Test
    void expiredStateIsNotCompletedWithoutTokenRequest() throws Exception {
        String sid = newSid();
        Started s = start(sid);
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(NOT_COMPLETED);
        assertThat(stub.requests).isEmpty();
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
    }
}
