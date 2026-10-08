package com.oracul.app.runs;

import com.oracul.app.session.SessionFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;

/** FR-67: an unknown / malformed / foreign run is 404 before the body is looked at (also a broken body). */
class RewriteRunLookupFilter extends OncePerRequestFilter {

    private static final Pattern PATH = Pattern.compile("^/api/runs/([^/]+)/rewrites$");

    private final GenerationRunRepository runs;

    RewriteRunLookupFilter(GenerationRunRepository runs) {
        this.runs = runs;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !PATH.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Matcher m = PATH.matcher(request.getRequestURI());
        m.matches();
        boolean found = false;
        try {
            UUID runId = UUID.fromString(m.group(1));
            Object sid = request.getAttribute(SessionFilter.ATTRIBUTE);
            found = sid instanceof UUID session && runs.find(runId, session).isPresent();
        } catch (IllegalArgumentException e) {
            found = false;
        }
        if (!found) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"code\":\"RUN_NOT_FOUND\",\"message\":\"Future not found\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
