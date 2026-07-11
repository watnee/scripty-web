package com.scripty.security;

import com.scripty.dto.User;
import com.scripty.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Locks accounts flagged with {@code password_change_required} (seeded/default or
 * generated deploy credentials) to the change-password page until they set a real
 * password. The flag is looked up once per session and cached as a session
 * attribute; AccountController clears the attribute after a successful change.
 */
public class ForcedPasswordChangeFilter extends OncePerRequestFilter {

    public static final String SESSION_ATTR = "scripty.passwordChangeRequired";

    private static final String CHANGE_PASSWORD_PATH = "/account/password";

    private static final String[] EXEMPT_PREFIXES = {
            "/css/", "/js/", "/icons/", "/fonts/", "/dictionaries/", "/actuator/"
    };

    private static final String[] EXEMPT_PATHS = {
            CHANGE_PASSWORD_PATH, "/login", "/logout", "/error",
            "/favicon.ico", "/manifest.json", "/sw.js", "/offline.html", "/offline-project.html"
    };

    private final UserRepository userRepository;

    public ForcedPasswordChangeFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
                || isExempt(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isPasswordChangeRequired(request.getSession(), auth.getName())) {
            String target = request.getContextPath() + CHANGE_PASSWORD_PATH;
            if ("true".equals(request.getHeader("HX-Request"))) {
                // Full-page redirect for HTMX callers instead of swapping in a fragment.
                response.setHeader("HX-Redirect", target);
                response.setStatus(HttpServletResponse.SC_OK);
            } else {
                response.sendRedirect(target);
            }
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPasswordChangeRequired(HttpSession session, String username) {
        Boolean cached = (Boolean) session.getAttribute(SESSION_ATTR);
        if (cached != null) {
            return cached;
        }
        boolean required = userRepository.findByUsername(username)
                .map(User::isPasswordChangeRequired)
                .orElse(false);
        session.setAttribute(SESSION_ATTR, required);
        return required;
    }

    private boolean isExempt(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (String exempt : EXEMPT_PATHS) {
            if (path.equals(exempt)) {
                return true;
            }
        }
        for (String prefix : EXEMPT_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
