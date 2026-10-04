package com.oracul.app.chatgpt;

import com.oracul.app.api.model.RunFailureCode;
import com.oracul.app.runs.RunFailures;

/**
 * A ChatGPT call failed; carries the run failure code, its fixed message and, for the codes that show it, the sanitized
 * provider code (FR-39).
 */
public class ChatGptCallException extends RuntimeException {

    private final String code;
    private final String providerCode;

    public ChatGptCallException(String code, String message) {
        this(code, message, null);
    }

    public ChatGptCallException(String code, String message, String providerCode) {
        super(message, null, true, false);
        this.code = code;
        this.providerCode = providerCode;
    }

    public String code() {
        return code;
    }

    /** Sanitized provider code; only set with CHATGPT_REQUEST_REJECTED and CHATGPT_UNEXPECTED_ERROR. */
    public String providerCode() {
        return providerCode;
    }

    static ChatGptCallException of(RunFailureCode code) {
        return new ChatGptCallException(code.getValue(), RunFailures.message(code));
    }

    static ChatGptCallException rateLimited() {
        return of(RunFailureCode.CHATGPT_RATE_LIMITED);
    }

    static ChatGptCallException unavailable() {
        return of(RunFailureCode.CHATGPT_UNAVAILABLE);
    }

    static ChatGptCallException sessionExpired() {
        return of(RunFailureCode.CHATGPT_SESSION_EXPIRED);
    }

    static ChatGptCallException rejected(String providerCode) {
        return new ChatGptCallException(RunFailureCode.CHATGPT_REQUEST_REJECTED.getValue(),
            RunFailures.message(RunFailureCode.CHATGPT_REQUEST_REJECTED), providerCode);
    }

    static ChatGptCallException unexpected(String providerCode) {
        return new ChatGptCallException(RunFailureCode.CHATGPT_UNEXPECTED_ERROR.getValue(),
            RunFailures.unexpected(providerCode), providerCode);
    }
}
