package com.scripty.observability;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Counts sign-in outcomes from Spring Security's own events, so no login code path
 * has to remember to report.
 *
 * <p>Listens for {@code InteractiveAuthenticationSuccessEvent} rather than the
 * broader {@code AuthenticationSuccessEvent}: the latter also fires for
 * per-request re-authentication, which would inflate the count well past the number
 * of actual sign-ins and make a failure ratio meaningless.
 *
 * <p>Only the outcome is recorded. Usernames stay out of metrics — they are
 * unbounded, and a failed-login metric tagged by username is a credential-stuffing
 * target list sitting on an endpoint that scrapers read.
 */
@Component
public class AuthenticationMetricsListener {

    private static final String EVENT_SUCCESS = "success";
    private static final String EVENT_FAILURE = "failure";

    private final ScriptyMetrics metrics;

    public AuthenticationMetricsListener(ScriptyMetrics metrics) {
        this.metrics = metrics;
    }

    @EventListener
    public void onSuccess(InteractiveAuthenticationSuccessEvent event) {
        metrics.authEvent(EVENT_SUCCESS);
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        metrics.authEvent(EVENT_FAILURE);
    }
}
