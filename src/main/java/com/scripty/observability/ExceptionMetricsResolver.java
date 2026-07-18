package com.scripty.observability;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Counts and logs every exception that escapes a controller.
 *
 * <p>Deliberately a {@link HandlerExceptionResolver} rather than an
 * {@code @ExceptionHandler} advice: an advice would <em>handle</em> the exception
 * and take over rendering, silently replacing the existing error pages. This runs
 * first, records what it saw, and returns {@code null} to decline — the normal
 * resolver chain then handles the exception exactly as it did before. Observation
 * only, no behaviour change.
 *
 * <p>Note that Spring Security's authentication and access-denied exceptions are
 * handled inside the filter chain and never reach the DispatcherServlet, so they
 * are counted by {@link AuthenticationMetricsListener} instead of here.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ExceptionMetricsResolver implements HandlerExceptionResolver {

    private static final Logger log = LoggerFactory.getLogger(ExceptionMetricsResolver.class);

    private final ScriptyMetrics metrics;

    public ExceptionMetricsResolver(ScriptyMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        int status = statusOf(ex);
        metrics.unhandledException(ex.getClass().getSimpleName(), status);

        // 4xx is the client getting it wrong and is normal traffic; logging every one
        // at ERROR would bury the 5xx that actually need attention. The request_id MDC
        // set by RequestIdFilter is on both lines, so either can be traced back.
        if (status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            log.error("Unhandled exception for {} {} -> {}",
                    request.getMethod(), request.getRequestURI(), status, ex);
        } else {
            log.debug("Client error for {} {} -> {}: {}",
                    request.getMethod(), request.getRequestURI(), status, ex.toString());
        }
        return null;
    }

    /**
     * Best-effort status, matching how the real resolvers will classify the
     * exception a moment later: Spring's own {@link ErrorResponse} types carry
     * their status, custom exceptions may declare {@code @ResponseStatus}, and
     * anything else ends up a 500.
     */
    private static int statusOf(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            return errorResponse.getStatusCode().value();
        }
        ResponseStatus annotated = AnnotationUtils.findAnnotation(ex.getClass(), ResponseStatus.class);
        if (annotated != null) {
            return annotated.code().value();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }
}
