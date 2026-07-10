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
 */
public class CsrfAccessDeniedHandler implements AccessDeniedHandler {

    private final AccessDeniedHandler fallback = new AccessDeniedHandlerImpl();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        if (isCsrfFailure(accessDeniedException) && acceptsHtml(request)) {
            response.sendRedirect(request.getContextPath() + "/login?csrf_error=1");
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
}
