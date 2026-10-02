package com.oracul.app.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/** One INFO line per /api request; secrets are masked by the logback redacting converters. */
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            String query = request.getQueryString();
            String auth = request.getHeader("Authorization");
            log.info("api request method={} path={} query={} status={} authorization={}",
                request.getMethod(), request.getRequestURI(),
                query == null || query.isEmpty() ? "-" : query, response.getStatus(),
                auth == null ? "-" : auth);
        }
    }
}
