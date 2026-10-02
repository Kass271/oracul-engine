package com.oracul.app.session;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/** The browser session of the current request (set by {@link SessionFilter}). */
@Component
@RequestScope
public class CurrentSession {

    private final HttpServletRequest request;

    CurrentSession(HttpServletRequest request) {
        this.request = request;
    }

    public UUID id() {
        Object id = request.getAttribute(SessionFilter.ATTRIBUTE);
        if (id instanceof UUID uuid) {
            return uuid;
        }
        throw new IllegalStateException("no browser session on this request");
    }
}
