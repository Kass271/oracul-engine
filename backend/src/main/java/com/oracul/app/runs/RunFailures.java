package com.oracul.app.runs;

import com.oracul.app.api.model.RunFailureCode;

/** The one table of fixed run failure messages (FR-32). */
public final class RunFailures {

    private RunFailures() {
    }

    public static String message(RunFailureCode code) {
        return switch (code) {
            case NEWS_UNAVAILABLE -> "ORACUL could not reach its news sources — try again later";
            case CHATGPT_RATE_LIMITED -> "ChatGPT usage limit reached — try again later";
            case CHATGPT_UNAVAILABLE -> "ChatGPT is temporarily unavailable — try again in a few minutes";
            case CHATGPT_SESSION_EXPIRED -> "ChatGPT session expired — please reconnect";
            case CHATGPT_PLAN_NOT_ELIGIBLE ->
                "Your ChatGPT plan is not eligible for ORACUL — a personal Plus or Pro plan is needed";
            case CHATGPT_REGISTRATION_INVALID ->
                "ChatGPT registration is no longer valid — use Reset ChatGPT connection, then reconnect";
            case CHATGPT_INCOMPLETE -> "ChatGPT did not finish the answer — please try again";
            case CHATGPT_REQUEST_REJECTED -> "ChatGPT rejected ORACUL's request — please report this";
            case CHATGPT_NO_MODEL -> "ChatGPT offers no model for this account — check your plan, then try again";
            case RUN_TIMEOUT -> "Generation took too long — try again";
            case INVALID_SCENARIO -> "ORACUL could not construct a valid scenario";
            case SCENARIO_REJECTED -> "ORACUL could not construct a scenario supported by current evidence";
            case ALTERNATIVE_NOT_DISTINCT -> "ORACUL could not find a different future — try changing a setting";
            case RUN_INTERRUPTED -> "Generation was interrupted — try again";
            case INTERNAL_ERROR -> "Something went wrong — try again";
            case INSUFFICIENT_EVIDENCE ->
                throw new IllegalArgumentException("INSUFFICIENT_EVIDENCE message depends on the realism");
            default -> "Something went wrong — try again";
        };
    }

    public static String unexpected(String providerCode) {
        return "ChatGPT returned an unexpected error (" + providerCode + ") — please try again";
    }

    public static String insufficientEvidence(int realism) {
        if (realism < 1 || realism > 10) {
            throw new IllegalArgumentException("realism must be between 1 and 10");
        }
        return "ORACUL found insufficient current evidence to construct this scenario at Realism " + realism + ".";
    }
}
