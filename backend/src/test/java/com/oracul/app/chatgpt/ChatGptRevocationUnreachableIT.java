package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** FR-37: a revocation endpoint that refuses connections (port 1) is ignored. */
@ExtendWith(OutputCaptureExtension.class)
class ChatGptRevocationUnreachableIT extends AbstractChatGptIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        StubOpenAi.registerAll(r);
        r.add("oracul.chatgpt.revocation-url", () -> "http://127.0.0.1:1/revoke");
    }

    /** Reset is installation-wide: sessions connected by earlier tests must not add revocation calls. */
    @org.junit.jupiter.api.BeforeEach
    void emptyCredentialStore() throws Exception {
        clearStore();
    }

    // @trace FR-37
    @Test
    void anUnreachableRevocationEndpointStillAnswers204(CapturedOutput output) throws Exception {
        resetRegistration();
        String sid = newSid();
        connect(sid);
        String rt = stub.issued.stream().filter(v -> v.startsWith("rt-")).findFirst().orElseThrow();
        disconnect(sid).andExpect(status().isNoContent());
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
        connect(sid);
        mvc.perform(delete("/api/auth/chatgpt/registration")).andExpect(status().isNoContent());
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
        assertThat(jdbc.queryForObject("select client_id from chatgpt_client_registration", String.class)).isNull();
        assertThat(output.getAll()).contains("chatgpt revocation failed: ConnectException").doesNotContain(rt);
    }
}
