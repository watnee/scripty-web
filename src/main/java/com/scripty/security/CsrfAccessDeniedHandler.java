package com.scripty.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

/**
 * CSRF failures on login (and other browser form posts) otherwise fall through to
 * Spring Boot's Whitelabel {@code /error} with a bare 403. Send the user back to
 * sign-in with a clear message instead.
 *
 * <p>For HTMX requests, use {@code HX-Redirect} instead of a 302. A normal redirect
 * is followed by the XHR and swapped into the current page (e.g. the project editor),
 * which leaves the URL on {@code /project/show} while showing the login form.
 */
public class CsrfAccessDeniedHandler implements AccessDeniedHandler {

    private static final String LOGIN_CSRF_ERROR = "/login?csrf_error=1";

    private final AccessDeniedHandler fallback = new AccessDeniedHandlerImpl();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        // API responses are always JSON: a native client that is denied needs a
        // decodable body telling it what to fix, not an empty 403, an error page,
        // or a login redirect.
        if (ApiRequests.isApiRequest(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(isCsrfFailure(accessDeniedException)
                    ? "{\"error\":\"csrf_required\",\"message\":\"Send an Authorization header"
                            + " (native clients) or a CSRF token (browser sessions).\"}"
                    : "{\"error\":\"forbidden\",\"message\":\"You do not have access"
                            + " to this resource.\"}");
            return;
        }
        if (isCsrfFailure(accessDeniedException) && acceptsHtml(request)) {
            String loginUrl = request.getContextPath() + LOGIN_CSRF_ERROR;
            if (isHtmxRequest(request)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setHeader("HX-Redirect", loginUrl);
                return;
            }
            response.sendRedirect(loginUrl);
            return;
        }
        fallback.handle(request, response, accessDeniedException);
    }

    private static boolean isCsrfFailure(AccessDeniedException ex) {
        return ex instanceof InvalidCsrfTokenException || ex instanceof MissingCsrfTokenException;
    }

    private static boolean acceptsHtml(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept == null || accept.contains("text/html") || accept.contains("*/*");
    }

    private static boolean isHtmxRequest(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader("HX-Request"));
    }
}
