package com.scripty.security;

import com.scripty.service.ApiTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests carrying {@code Authorization: Bearer <token>} against
 * the opaque API tokens minted at passkey sign-in. Requests using HTTP Basic
 * (or no Authorization header) are left untouched for the framework's own
 * filters, so password auth is unaffected.
 */
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiTokenService tokenService;
    private final UserDetailsService userDetailsService;

    public ApiTokenAuthenticationFilter(ApiTokenService tokenService,
            UserDetailsService userDetailsService) {
        this.tokenService = tokenService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            tokenService.authenticate(token).ifPresent(username -> authenticate(username, request));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String username, HttpServletRequest request) {
        try {
            UserDetails user = userDetailsService.loadUserByUsername(username);
            if (user == null || !user.isEnabled()) {
                return;
            }
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    user, null, user.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (UsernameNotFoundException ignored) {
            // Token references a user that no longer exists — treat as unauthenticated.
        }
    }
}
