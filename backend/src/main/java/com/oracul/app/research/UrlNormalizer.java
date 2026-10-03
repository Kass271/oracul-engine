package com.oracul.app.research;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Normalized article URL: lower-case scheme/host, default port and fragment removed, no utm_* parameters. */
public final class UrlNormalizer {

    private UrlNormalizer() {
    }

    /** Returns the normalized URL, or null when the input is not a usable http/https URL. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(raw.trim());
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            int port = uri.getPort();
            if (port == ("http".equals(scheme) ? 80 : 443)) {
                port = -1;
            }
            StringBuilder out = new StringBuilder(scheme).append("://");
            if (uri.getRawUserInfo() != null) {
                out.append(uri.getRawUserInfo()).append('@');
            }
            out.append(host.toLowerCase(Locale.ROOT));
            if (port > 0) {
                out.append(':').append(port);
            }
            if (uri.getRawPath() != null) {
                out.append(uri.getRawPath());
            }
            String query = uri.getRawQuery();
            if (query != null) {
                List<String> kept = new ArrayList<>();
                for (String param : query.split("&")) {
                    String name = param.contains("=") ? param.substring(0, param.indexOf('=')) : param;
                    if (!name.toLowerCase(Locale.ROOT).startsWith("utm_")) {
                        kept.add(param);
                    }
                }
                kept.removeIf(String::isEmpty);
                if (!kept.isEmpty()) {
                    out.append('?').append(String.join("&", kept));
                }
            }
            return out.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Host of a URL without a leading "www.", or empty. */
    public static String host(String url) {
        try {
            String h = new URI(url).getHost();
            if (h == null) {
                return "";
            }
            h = h.toLowerCase(Locale.ROOT);
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (Exception e) {
            return "";
        }
    }
}
