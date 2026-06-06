package com.cody.web.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Simple authentication filter auth.
 *
 * Checks for X-API-Key header matching the CODY_API_KEY environment variable,
 * or Bearer token in Authorization header.
 */
@Component
public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        String path = httpReq.getRequestURI();
        // Allow health check and OPTIONS
        if ("/health".equals(path) || "OPTIONS".equalsIgnoreCase(httpReq.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String expectedKey = System.getenv("CODY_API_KEY");
        if (expectedKey == null || expectedKey.isEmpty()) {
            // No auth required if CODY_API_KEY is not set
            chain.doFilter(request, response);
            return;
        }

        String apiKey = httpReq.getHeader("X-API-Key");
        if (apiKey == null) {
            String auth = httpReq.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                apiKey = auth.substring(7);
            }
        }

        if (!expectedKey.equals(apiKey)) {
            httpRes.setStatus(401);
            httpRes.setContentType("application/json");
            httpRes.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing API key\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
