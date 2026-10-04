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
    @DefaultValue("http://127.0.0.1:4200/callback") String redirectUri,
    @DefaultValue("openid profile email offline_access resource.invoke chatgpt.tokens.use.direct") String scopes,
    @DefaultValue("https://api.openai.com/v1") String resource,
    @DefaultValue("dynamic_agent_client") String dynamicClientId,
    @DefaultValue("ORACUL") String agentNameHint,
    @DefaultValue("chatgpt.tokens.use.direct") String requiredScope,
    @DefaultValue("PT10M") Duration pendingTtl,
    @DefaultValue("PT5M") Duration refreshSkew,
    @DefaultValue("PT10S") Duration httpTimeout,
    @DefaultValue("https://auth.openai.com") String issuer,
    @DefaultValue("https://auth.openai.com/.well-known/jwks.json") String jwksUrl,
    @DefaultValue("https://auth.openai.com/api/accounts/oauth/revoke") String revocationUrl,
    @DefaultValue("PT1H") Duration jwksCacheTtl) {

    private static final String REDIRECT_MESSAGE =
        "oracul.chatgpt.redirect-uri must be http://127.0.0.1:<port>/callback";

    public ChatGptProperties {
        requireLoopbackRedirect(redirectUri);
    }

    public static void requireLoopbackRedirect(String redirectUri) {
        try {
            URI uri = new URI(redirectUri);
            if (uri.isAbsolute() && "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost())
                && uri.getPort() >= 1 && uri.getPort() <= 65535 && "/callback".equals(uri.getPath())
                && uri.getRawQuery() == null && uri.getRawFragment() == null) {
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
