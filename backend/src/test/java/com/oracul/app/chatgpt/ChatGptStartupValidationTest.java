package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oracul.app.BackendApplication;
import com.oracul.app.TestcontainersConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;

/** FR-7 / FR-35 (NFR-9): the redirect URI must be the loopback /callback form OpenAI accepts. */
class ChatGptStartupValidationTest {

    private static final String MESSAGE = "oracul.chatgpt.redirect-uri must be http://127.0.0.1:<port>/callback";

    private static String chain(Throwable e) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) all.append(t.getMessage()).append('\n');
        return all.toString();
    }

    // @trace FR-7, FR-35
    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost:4200/auth/callback",
        "https://127.0.0.1:4200/auth/callback",
        "http://127.0.0.1/auth/callback",
        "http://127.0.0.1:4200/other",
        "http://localhost:4200/callback",
    })
    void nonLoopbackRedirectUriFailsStartup(String uri) {
        assertThatThrownBy(() -> {
            ConfigurableApplicationContext c = new SpringApplicationBuilder(BackendApplication.class, TestcontainersConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties("oracul.chatgpt.redirect-uri=" + uri)
                .run();
            c.close();
        }).satisfies(e -> assertThat(chain(e)).contains(MESSAGE));
    }

    // @trace FR-35
    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1:4200/callback", "http://127.0.0.1:1455/callback"})
    void validRedirectUriStartsTheContext(String uri) {
        new ApplicationContextRunner()
            .withUserConfiguration(PropsConfig.class)
            .withPropertyValues("oracul.chatgpt.redirect-uri=" + uri)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(ChatGptProperties.class).redirectUri()).isEqualTo(uri);
            });
    }

    @EnableConfigurationProperties(ChatGptProperties.class)
    static class PropsConfig {
    }

    /** Every class of the spec's "Ranges & invariants": valid ones are accepted. */
    // @trace FR-35
    @ParameterizedTest(name = "valid {0}")
    @ValueSource(strings = {
        "http://127.0.0.1:4200/callback",
        "http://127.0.0.1:1/callback",
        "http://127.0.0.1:65535/callback",
        "http://127.0.0.1:1455/callback",
    })
    void validRedirectUriClassesAreAccepted(String uri) {
        assertThatCode(() -> ChatGptProperties.requireLoopbackRedirect(uri)).doesNotThrowAnyException();
    }

    /** Every invalid class of the spec: scheme, host, port, path, query, fragment. */
    // @trace FR-35
    @ParameterizedTest(name = "invalid {0}")
    @ValueSource(strings = {
        "https://127.0.0.1:4200/callback",
        "http://localhost:4200/callback",
        "http://127.0.0.2:4200/callback",
        "http://[::1]:4200/callback",
        "http://127.0.0.1/callback",
        "http://127.0.0.1:0/callback",
        "http://127.0.0.1:65536/callback",
        "http://127.0.0.1:4200/auth/callback",
        "http://127.0.0.1:4200/callback/",
        "http://127.0.0.1:4200/other",
        "http://127.0.0.1:4200",
        "http://127.0.0.1:4200/callback?x=1",
        "http://127.0.0.1:4200/callback#f",
    })
    void invalidRedirectUriClassesAreRejectedWithTheExactMessage(String uri) {
        assertThatThrownBy(() -> ChatGptProperties.requireLoopbackRedirect(uri))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(MESSAGE);
    }
}
