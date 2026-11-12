package com.trustdesk.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustdesk.common.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Simple demo-token authentication: every request must send
 * {@code Authorization: Bearer <token>}, where the token comes from the
 * {@code TRUSTDESK_DEMO_TOKEN} environment variable.
 * <p>
 * Exception: the three files of the demo web page ({@link #PUBLIC_PAGE_FILES}) load without a token.
 * They contain no data; every API call the page makes still needs the token.
 */
@Component
public class DemoTokenFilter extends OncePerRequestFilter {

    static final String DEFAULT_DEV_TOKEN = "dev-token";

    private static final Logger log = LoggerFactory.getLogger(DemoTokenFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    /** Exact paths only (an allowlist), so no API endpoint can ever become public by accident. */
    static final Set<String> PUBLIC_PAGE_FILES = Set.of("/", "/index.html", "/app.js", "/styles.css");

    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    public DemoTokenFilter(@Value("${trustdesk.demo-token}") String demoToken, ObjectMapper objectMapper) {
        if (demoToken == null || demoToken.isBlank()) {
            throw new IllegalStateException("trustdesk.demo-token must not be empty (set TRUSTDESK_DEMO_TOKEN)");
        }
        if (DEFAULT_DEV_TOKEN.equals(demoToken)) {
            log.warn("Using the default demo token '{}'. Set TRUSTDESK_DEMO_TOKEN outside local development.",
                    DEFAULT_DEV_TOKEN);
        }
        this.expectedToken = demoToken.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Browsers send OPTIONS "preflight" requests without credentials before cross-origin calls.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        boolean read = HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod());
        return read && PUBLIC_PAGE_FILES.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            reject(response, "Missing demo token. Send 'Authorization: Bearer <token>'.");
            return;
        }

        byte[] givenToken = header.substring(BEARER_PREFIX.length()).strip().getBytes(StandardCharsets.UTF_8);
        // Constant-time comparison: takes the same time whether the first or last character is wrong.
        if (!MessageDigest.isEqual(expectedToken, givenToken)) {
            reject(response, "Invalid demo token.");
            return;
        }

        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new ApiError(HttpServletResponse.SC_UNAUTHORIZED, "unauthorized", message));
    }
}