package com.oracul.app.chatgpt;

/** The caller's run guard said stop before a request was sent; the work of the run is abandoned. */
public class CallAbandonedException extends RuntimeException {

    public CallAbandonedException() {
        super("run abandoned", null, false, false);
    }
}
