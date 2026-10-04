package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** FR-37: a revocation endpoint slower than http-timeout is ignored — reset and disconnect still answer 204. */
@TestPropertySource(properties = "oracul.chatgpt.http-timeout=PT1S")
@ExtendWith(OutputCaptureExtension.class)
class ChatGptRevocationTimeoutIT extends AbstractChatGptIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        StubOpenAi.registerAll(r);
    }

    /** Reset is installation-wide: sessions connected by earlier tests must not add revocation calls. */
    @org.junit.jupiter.api.BeforeEach
    void emptyCredentialStore() throws Exception {
        clearStore();
    }

    // @trace FR-37
    @Test
    void aSlowRevocationEndpointDoesNotBlockDisconnectOrReset(CapturedOutput output) throws Exception {
        stub.revokeResponder = r -> new StubOpenAi.Reply(200, "", 4000);
        resetRegistration();
        String sid = newSid();
        connect(sid);
        long t0 = System.nanoTime();
        disconnect(sid).andExpect(status().isNoContent());
        assertThat((System.nanoTime() - t0) / 1_000_000).isLessThan(3500);
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
        assertThat(stub.revocations).hasSize(1);

        connect(sid);
        t0 = System.nanoTime();
        mvc.perform(delete("/api/auth/chatgpt/registration")).andExpect(status().isNoContent());
        assertThat((System.nanoTime() - t0) / 1_000_000).isLessThan(3500);
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
        assertThat(jdbc.queryForObject("select client_id from chatgpt_client_registration", String.class)).isNull();
        assertThat(output.getAll()).contains("chatgpt revocation failed: HttpTimeoutException");
    }
}
