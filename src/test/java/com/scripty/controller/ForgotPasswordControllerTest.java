package com.scripty.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.PasswordRecoveryToken;
import com.scripty.dto.User;
import com.scripty.security.PasswordResetRateLimiter;
import com.scripty.service.PasswordRecoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class ForgotPasswordControllerTest {

    private PasswordRecoveryService recoveryService;
    private PasswordResetRateLimiter rateLimiter;
    private ForgotPasswordController controller;

    @BeforeEach
    void setUp() {
        recoveryService = mock(PasswordRecoveryService.class);
        rateLimiter = new PasswordResetRateLimiter();
        controller = new ForgotPasswordController(recoveryService, rateLimiter);
    }

    private static MockHttpServletRequest requestFrom(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    void requestFormReturnsCorrectView() {
        assertEquals("forgot-password/request", controller.requestForm());
    }

    @Test
    void processRequestCallsServiceAndReturnsSuccessMessage() {
        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.processRequest("user@example.com", model, requestFrom("10.0.0.1"));

        verify(recoveryService).sendRecoveryEmail("user@example.com");
        assertEquals("forgot-password/request", view);
        assertTrue(((String) model.getAttribute("successMessage")).contains("instructions to reset your password have been sent"));
    }

    /**
     * The limit stops the sending, not the answering. A page that said "too many
     * attempts" would be telling a stranger they had found a real address — the
     * one thing this flow refuses to say — so a refused request has to be
     * indistinguishable from a sent one.
     */
    @Test
    void processRequestLooksIdenticalOnceRateLimited() {
        ExtendedModelMap model = null;
        for (int i = 0; i < 10; i++) {
            model = new ExtendedModelMap();
            String view = controller.processRequest("flooded@example.com", model,
                    requestFrom("10.0.0.2"));
            assertEquals("forgot-password/request", view);
            assertTrue(((String) model.getAttribute("successMessage"))
                    .contains("instructions to reset your password have been sent"));
        }
        assertNull(model.getAttribute("errorMessage"));
        // Three of the ten got through; the service never heard about the rest.
        verify(recoveryService, times(3)).sendRecoveryEmail("flooded@example.com");
    }

    @Test
    void processRequestStopsOneAddressWalkingAListOfEmails() {
        for (int i = 0; i < 20; i++) {
            controller.processRequest("person" + i + "@example.com", new ExtendedModelMap(),
                    requestFrom("10.0.0.3"));
        }
        // Each address is under its own limit, so only the per-IP window can
        // refuse these — and past fifteen it does.
        verify(recoveryService, never()).sendRecoveryEmail("person15@example.com");
        verify(recoveryService, never()).sendRecoveryEmail("person19@example.com");
    }

    @Test
    void processRequestStillSucceedsWhenTheServiceThrows() {
        doThrow(new IllegalStateException("SMTP down"))
                .when(recoveryService).sendRecoveryEmail(any());

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.processRequest("user@example.com", model, requestFrom("10.0.0.4"));

        assertEquals("forgot-password/request", view);
        assertTrue(((String) model.getAttribute("successMessage"))
                .contains("instructions to reset your password have been sent"));
    }

    @Test
    void resetFormWithValidTokenPreparesModel() {
        PasswordRecoveryToken token = new PasswordRecoveryToken();
        User user = new User();
        user.setUsername("testuser");
        user.setEmail("testuser@example.com");
        token.setUser(user);

        when(recoveryService.validateToken("good-token")).thenReturn(token);

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.resetForm("good-token", model);

        assertEquals("forgot-password/reset", view);
        assertEquals(true, model.getAttribute("valid"));
        assertEquals("good-token", model.getAttribute("token"));
        assertEquals("testuser@example.com", model.getAttribute("email"));
    }

    @Test
    void resetFormWithInvalidTokenSetsError() {
        when(recoveryService.validateToken("bad-token")).thenThrow(new IllegalArgumentException("Expired or invalid"));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.resetForm("bad-token", model);

        assertEquals("forgot-password/reset", view);
        assertEquals(false, model.getAttribute("valid"));
        assertEquals("Expired or invalid", model.getAttribute("errorMessage"));
    }

    @Test
    void processResetRejectsMismatchingPasswords() {
        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.processReset("token", "pass1", "pass2", model, redirect);

        assertEquals("forgot-password/reset", view);
        assertEquals(true, model.getAttribute("valid"));
        assertEquals("token", model.getAttribute("token"));
        assertEquals("Passwords do not match.", model.getAttribute("errorMessage"));
    }

    @Test
    void processResetCallsServiceOnValidRequestAndRedirectsToLogin() {
        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.processReset("token", "strong-pass", "strong-pass", model, redirect);

        verify(recoveryService).resetPassword("token", "strong-pass");
        assertEquals("redirect:/login", view);
        assertTrue(redirect.getFlashAttributes().containsKey("passwordResetSuccess"));
    }

    @Test
    void processResetHandlesWeakPasswordServiceException() {
        doThrow(new IllegalArgumentException("Password too weak"))
                .when(recoveryService).resetPassword("token", "weak");

        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.processReset("token", "weak", "weak", model, redirect);

        assertEquals("forgot-password/reset", view);
        assertEquals(true, model.getAttribute("valid"));
        assertEquals("Password too weak", model.getAttribute("errorMessage"));
    }
}
