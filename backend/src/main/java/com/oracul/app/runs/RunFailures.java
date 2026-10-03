package com.oracul.app.runs;

import com.oracul.app.api.model.RunFailureCode;

/** The one table of fixed run failure messages (FR-32). */
public final class RunFailures {

    private RunFailures() {
    }

    public static String message(RunFailureCode code) {
        return switch (code) {
            case NEWS_UNAVAILABLE -> "ORACUL could not reach its news sources — try again later";
            case CHATGPT_RATE_LIMITED -> "ChatGPT plan limit reached — try again later";
            case CHATGPT_UNAVAILABLE -> "ChatGPT is unavailable right now — try again later";
            case CHATGPT_SESSION_EXPIRED -> "ChatGPT session expired — please reconnect";
            case RUN_TIMEOUT -> "Generation took too long — try again";
            case INVALID_SCENARIO -> "ORACUL could not construct a valid scenario";
            case SCENARIO_REJECTED -> "ORACUL could not construct a scenario supported by current evidence";
            case ALTERNATIVE_NOT_DISTINCT -> "ORACUL could not find a different future — try changing a setting";
            case RUN_INTERRUPTED -> "Generation was interrupted — try again";
            case INTERNAL_ERROR -> "Something went wrong — try again";
            case INSUFFICIENT_EVIDENCE ->
                throw new IllegalArgumentException("INSUFFICIENT_EVIDENCE message depends on the realism");
        };
    }
}
