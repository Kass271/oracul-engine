package com.oracul.app.chatgpt;

/** A ChatGPT call failed for transport reasons; carries the run failure code and its fixed message. */
public class ChatGptCallException extends RuntimeException {

    private final String code;

    public ChatGptCallException(String code, String message) {
        super(message, null, true, false);
        this.code = code;
    }

    public String code() {
        return code;
    }

    static ChatGptCallException rateLimited() {
        return new ChatGptCallException("CHATGPT_RATE_LIMITED", com.oracul.app.runs.RunFailures.message(com.oracul.app.api.model.RunFailureCode.CHATGPT_RATE_LIMITED));
    }

    static ChatGptCallException unavailable() {
        return new ChatGptCallException("CHATGPT_UNAVAILABLE", com.oracul.app.runs.RunFailures.message(com.oracul.app.api.model.RunFailureCode.CHATGPT_UNAVAILABLE));
    }

    static ChatGptCallException sessionExpired() {
        return new ChatGptCallException("CHATGPT_SESSION_EXPIRED", com.oracul.app.runs.RunFailures.message(com.oracul.app.api.model.RunFailureCode.CHATGPT_SESSION_EXPIRED));
    }
}
