package com.scripty.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.User;
import com.scripty.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ForcedPasswordChangeFilterTest {

    private UserRepository userRepository;
    private ForcedPasswordChangeFilter filter;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        filter = new ForcedPasswordChangeFilter(userRepository);
        session = new MockHttpSession();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void redirectsFlaggedUserToChangePassword() throws Exception {
        authenticate("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(flaggedUser()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/project/1"), response, new MockFilterChain());

        assertEquals(302, response.getStatus());
        assertEquals("/account/password", response.getRedirectedUrl());
    }

    @Test
    void sendsHxRedirectForHtmxRequests() throws Exception {
        authenticate("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(flaggedUser()));
        MockHttpServletRequest request = request("/project/1");
        request.addHeader("HX-Request", "true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertEquals("/account/password", response.getHeader("HX-Redirect"));
    }

    @Test
    void allowsChangePasswordAndLogoutForFlaggedUser() throws Exception {
        authenticate("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(flaggedUser()));

        for (String path : List.of("/account/password", "/logout", "/css/scripty.css",
                "/webauthn/register", "/webauthn/register/options", "/login/webauthn.js")) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request(path), response, new MockFilterChain());
            assertNull(response.getRedirectedUrl(), path);
        }
    }

    @Test
    void lockLiftsWithoutNewSessionOnceFlagClears() throws Exception {
        authenticate("admin");
        User user = flaggedUser();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(request("/project/1"), first, new MockFilterChain());
        assertEquals("/account/password", first.getRedirectedUrl());

        // Passkey registration (or a password change) clears the flag in the DB.
        user.setPasswordChangeRequired(false);

        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(request("/project/1"), second, new MockFilterChain());
        assertNull(second.getRedirectedUrl());
    }

    @Test
    void passesThroughUnflaggedUserAndCachesLookupInSession() throws Exception {
        authenticate("admin");
        User user = flaggedUser();
        user.setPasswordChangeRequired(false);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("/project/1"), response, new MockFilterChain());
            assertNull(response.getRedirectedUrl());
        }

        verify(userRepository, times(1)).findByUsername(anyString());
    }

    @Test
    void ignoresUnauthenticatedRequests() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/login"), response, new MockFilterChain());

        assertNull(response.getRedirectedUrl());
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setSession(session);
        return request;
    }

    private static void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private static User flaggedUser() {
        User user = new User();
        user.setUsername("admin");
        user.setEnabled(true);
        user.setPasswordChangeRequired(true);
        return user;
    }
}
