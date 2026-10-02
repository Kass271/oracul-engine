package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** FR-7: the authorize URL cannot be built — the user is sent back, never a 500 page. */
@TestPropertySource(properties = "oracul.chatgpt.authorize-url=http://[bad")
class ChatGptMisconfiguredIT extends AbstractChatGptIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        StubOpenAi.registerTokenUrl(r);
    }

    // @trace FR-7
    @Test
    void unbuildableAuthorizeUrlRedirectsToNotCompleted() throws Exception {
        String sid = newSid();
        mvc.perform(get("/api/auth/chatgpt/authorize").cookie(new Cookie("ORACUL_SID", sid)))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", NOT_COMPLETED));
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
    }
}
