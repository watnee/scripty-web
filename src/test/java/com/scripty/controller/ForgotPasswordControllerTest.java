package com.scripty.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.PasswordRecoveryToken;
import com.scripty.dto.User;
import com.scripty.service.PasswordRecoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class ForgotPasswordControllerTest {

    private PasswordRecoveryService recoveryService;
    private ForgotPasswordController controller;

    @BeforeEach
    void setUp() {
        recoveryService = mock(PasswordRecoveryService.class);
        controller = new ForgotPasswordController(recoveryService);
    }

    @Test
    void requestFormReturnsCorrectView() {
        assertEquals("forgot-password/request", controller.requestForm());
    }

    @Test
    void processRequestCallsServiceAndReturnsSuccessMessage() {
        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.processRequest("user@example.com", model);

        verify(recoveryService).sendRecoveryEmail("user@example.com");
        assertEquals("forgot-password/request", view);
        assertTrue(((String) model.getAttribute("successMessage")).contains("instructions to reset your password have been sent"));
    }

    @Test
    void resetFormWithValidTokenPreparesModel() {
        PasswordRecoveryToken token = new PasswordRecoveryToken();
        User user = new User();
        user.setUsername("testuser");
        token.setUser(user);

        when(recoveryService.validateToken("good-token")).thenReturn(token);

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.resetForm("good-token", model);

        assertEquals("forgot-password/reset", view);
        assertEquals(true, model.getAttribute("valid"));
        assertEquals("good-token", model.getAttribute("token"));
        assertEquals("testuser", model.getAttribute("username"));
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
