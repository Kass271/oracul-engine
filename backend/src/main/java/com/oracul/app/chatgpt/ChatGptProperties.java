package com.oracul.app.chatgpt;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "oracul.chatgpt")
public record ChatGptProperties(
    @DefaultValue("https://auth.openai.com/api/accounts/authorize") String authorizeUrl,
    @DefaultValue("https://auth.openai.com/api/accounts/oauth/token") String tokenUrl,
    @DefaultValue("http://127.0.0.1:4200/auth/callback") String redirectUri,
    @DefaultValue("openid profile email offline_access resource.invoke chatgpt.tokens.use.direct") String scopes,
    @DefaultValue("https://api.openai.com/v1") String resource,
    @DefaultValue("dynamic_agent_client") String dynamicClientId,
    @DefaultValue("ORACUL") String agentNameHint,
    @DefaultValue("chatgpt.tokens.use.direct") String requiredScope,
    @DefaultValue("PT10M") Duration pendingTtl,
    @DefaultValue("PT60S") Duration refreshSkew,
    @DefaultValue("PT10S") Duration httpTimeout) {

    private static final String REDIRECT_MESSAGE =
        "oracul.chatgpt.redirect-uri must be http://127.0.0.1:<port>/auth/callback";

    public ChatGptProperties {
        requireLoopbackRedirect(redirectUri);
    }

    public static void requireLoopbackRedirect(String redirectUri) {
        try {
            URI uri = new URI(redirectUri);
            if (uri.isAbsolute() && "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost())
                && uri.getPort() > 0 && "/auth/callback".equals(uri.getPath())) {
                return;
            }
        } catch (Exception e) {
            // falls through to the single failure below
        }
        throw new IllegalStateException(REDIRECT_MESSAGE);
    }

    public List<String> scopeList() {
        return Arrays.stream(scopes.trim().split("\\s+")).filter(s -> !s.isEmpty()).toList();
    }
}
