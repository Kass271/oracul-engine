package com.oracul.app.common.logging;

import java.util.regex.Pattern;

/** Masks bearer tokens and OAuth secrets in free text (logs, request lines). */
public final class SecretRedactor {

    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[^\\s\"',;]+");
    private static final Pattern PAIR = Pattern.compile(
        "(?<![A-Za-z0-9_])(access_token|refresh_token|id_token|code|code_verifier|state)=[^&\\s\"',;]+");
    private static final Pattern JSON = Pattern.compile(
        "\"(access_token|refresh_token|id_token|code_verifier|code|state)\"\\s*:\\s*\"[^\"]*\"");

    private SecretRedactor() {
    }

    public static String redact(String text) {
        if (text == null) {
            return null;
        }
        String out = BEARER.matcher(text).replaceAll("Bearer [REDACTED]");
        out = PAIR.matcher(out).replaceAll("$1=[REDACTED]");
        return JSON.matcher(out).replaceAll("\"$1\":\"[REDACTED]\"");
    }
}
