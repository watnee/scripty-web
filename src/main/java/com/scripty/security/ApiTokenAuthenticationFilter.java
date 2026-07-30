package com.scripty.security;

import com.scripty.service.ApiTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates {@code Authorization: Bearer} requests against the api_token
 * table — the header a native client sends after a passkey sign-in, which
 * leaves it with no password for Basic.
 *
 * <p>Not scoped to {@code /api} paths, because the API itself points beyond
 * them: the export links it advertises resolve to the web controllers
 * ({@code /project/export}, {@code /project/documents/…}), and a passkey
 * client following one has nothing but this token to offer. Basic
 * authenticates every path; the token must too, or Export and Print in the
 * native app end at a redirect to the login page. Browsers never attach a
 * Bearer header, so web sessions are untouched.
 *
 * <p>Stateless like Basic: each request authenticates itself, nothing is
 * written to the session. An unknown or revoked token simply leaves the
 * request anonymous, so it falls through to the same 401 challenge any
 * signed-out API call gets — which is exactly the signal that sends the
 * client back to the login screen.
 */
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String SCHEME = "Bearer ";

    private final ApiTokenService tokens;
    private final UserDetailsService userDetailsService;

    public ApiTokenAuthenticationFilter(ApiTokenService tokens,
            UserDetailsService userDetailsService) {
        this.tokens = tokens;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(SCHEME)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String username = tokens.resolve(header.substring(SCHEME.length()).trim());
            if (username != null) {
                try {
                    UserDetails user = userDetailsService.loadUserByUsername(username);
                    if (user.isEnabled()) {
                        UsernamePasswordAuthenticationToken authentication =
                                UsernamePasswordAuthenticationToken.authenticated(
                                        user, null, user.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (UsernameNotFoundException ignored) {
                    // A token for a deleted account authenticates nobody.
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
