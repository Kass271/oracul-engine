package com.oracul.app.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/** Ties every /api request (except the OAuth callback) to an anonymous browser session cookie. */
public class SessionFilter extends OncePerRequestFilter {

    public static final String COOKIE = "ORACUL_SID";
    static final String ATTRIBUTE = SessionFilter.class.getName() + ".SESSION_ID";
    private static final String CALLBACK = "/api/auth/chatgpt/callback";

    private final SessionService sessions;

    private static final String AUTHORIZE = "/api/auth/chatgpt/authorize";
    private static final Logger log = LoggerFactory.getLogger(SessionFilter.class);

    private final String frontendBaseUrl;

    public SessionFilter(SessionService sessions, String frontendBaseUrl) {
        this.sessions = sessions;
        String base = frontendBaseUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.frontendBaseUrl = base;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.equals(CALLBACK);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String cookie = null;
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (COOKIE.equals(c.getName())) {
                    cookie = c.getValue();
                    break;
                }
            }
        }
        SessionService.Resolved resolved;
        try {
            resolved = sessions.resolve(cookie);
        } catch (RuntimeException e) {
            log.error("Session lookup failed", e);
            if (AUTHORIZE.equals(request.getRequestURI())) {
                response.setStatus(HttpServletResponse.SC_FOUND);
                response.setHeader("Location", frontendBaseUrl + "/?chatgpt=not_completed");
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                    "{\"code\":\"INTERNAL_ERROR\",\"message\":\"Something went wrong — try again\"}");
            }
            return;
        }
        request.setAttribute(ATTRIBUTE, resolved.id());
        if (resolved.created()) {
            response.addHeader("Set-Cookie", COOKIE + "=" + resolved.id() + "; Path=/; HttpOnly; SameSite=Lax");
        }
        chain.doFilter(request, response);
    }
}
