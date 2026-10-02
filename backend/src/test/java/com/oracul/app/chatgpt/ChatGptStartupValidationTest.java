package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oracul.app.BackendApplication;
import com.oracul.app.TestcontainersConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** FR-7 (NFR-6): the redirect URI must be the loopback form OpenAI accepts. */
class ChatGptStartupValidationTest {

    // @trace FR-7
    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost:4200/auth/callback",
        "https://127.0.0.1:4200/auth/callback",
        "http://127.0.0.1/auth/callback",
        "http://127.0.0.1:4200/other",
    })
    void nonLoopbackRedirectUriFailsStartup(String uri) {
        assertThatThrownBy(() -> {
            ConfigurableApplicationContext c = new SpringApplicationBuilder(BackendApplication.class, TestcontainersConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties("oracul.chatgpt.redirect-uri=" + uri)
                .run();
            c.close();
        }).satisfies(e -> {
            Throwable t = e;
            StringBuilder all = new StringBuilder();
            while (t != null) { all.append(t.getMessage()).append('\n'); t = t.getCause(); }
            assertThat(all.toString())
                .contains("oracul.chatgpt.redirect-uri must be http://127.0.0.1:<port>/auth/callback");
        });
    }
}
