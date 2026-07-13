package com.scripty.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts a per-request correlation id into the logging MDC (key {@code request_id})
 * and echoes it back as {@code X-Request-Id}. Prefers ids already assigned upstream:
 * Cloudflare's CF-Ray, then X-Request-Id, then a generated UUID. With structured
 * logging enabled, every log line of a request carries the id, so one Railway log
 * search finds the whole request — and CF-Ray links it to the Cloudflare side.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "request_id";
    public static final String RESPONSE_HEADER = "X-Request-Id";

    /** Client-supplied ids are echoed into logs and a response header — keep them tame. */
    private static final java.util.regex.Pattern SAFE_ID =
            java.util.regex.Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = firstNonBlank(
                request.getHeader("CF-Ray"),
                request.getHeader("X-Request-Id"));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(RESPONSE_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && SAFE_ID.matcher(value).matches()) {
                return value;
            }
        }
        return null;
    }
}
