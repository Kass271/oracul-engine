package com.oracul.app.chatgpt;

/** A ChatGPT call failed for transport reasons; carries the run failure code and its fixed message. */
public class ChatGptCallException extends RuntimeException {

    private final String code;

    public ChatGptCallException(String code, String message) {
        super(message, null, false, false);
        this.code = code;
    }

    public String code() {
        return code;
    }

    static ChatGptCallException rateLimited() {
        return new ChatGptCallException("CHATGPT_RATE_LIMITED", "ChatGPT plan limit reached — try again later");
    }

    static ChatGptCallException unavailable() {
        return new ChatGptCallException("CHATGPT_UNAVAILABLE", "ChatGPT is unavailable right now — try again later");
    }

    static ChatGptCallException sessionExpired() {
        return new ChatGptCallException("CHATGPT_SESSION_EXPIRED", "ChatGPT session expired — please reconnect");
    }
}
