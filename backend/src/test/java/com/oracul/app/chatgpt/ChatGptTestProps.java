package com.oracul.app.chatgpt;

import java.time.Duration;

final class ChatGptTestProps {
    private ChatGptTestProps() {
    }

    static ChatGptProperties of(String tokenUrl, String jwksUrl, String revocationUrl) {
        return new ChatGptProperties("https://auth.example/authorize", tokenUrl, "http://127.0.0.1:4200/callback",
            "openid chatgpt.tokens.use.direct", "https://api.example/v1", "dynamic_agent_client", "ORACUL",
            "chatgpt.tokens.use.direct", Duration.ofMinutes(10), Duration.ofSeconds(60), Duration.ofSeconds(5),
            "https://issuer.example", jwksUrl, revocationUrl, Duration.ofHours(1));
    }
}
