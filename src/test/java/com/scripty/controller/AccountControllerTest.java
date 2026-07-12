package com.scripty.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.commandmodel.account.ChangePasswordCommandModel;
import com.scripty.config.PasskeySettings;
import com.scripty.dto.User;
import com.scripty.security.ForcedPasswordChangeFilter;
import com.scripty.service.UserService;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class AccountControllerTest {

    private final AccountController controller = new AccountController();
    private final UserService userService = mock(UserService.class);
    private final Principal principal = () -> "admin";

    @BeforeEach
    void setUp() {
        controller.userService = userService;
        controller.passkeySettings = new PasskeySettings(true, "http://localhost:8080");
    }

    private ChangePasswordCommandModel command(String current, String next, String confirm) {
        ChangePasswordCommandModel model = new ChangePasswordCommandModel();
        model.setCurrentPassword(current);
        model.setNewPassword(next);
        model.setConfirmPassword(confirm);
        return model;
    }

    private String changePassword(ChangePasswordCommandModel command, MockHttpSession session,
            ExtendedModelMap model) {
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "commandModel");
        return controller.changePassword(command, bindingResult, principal, model, session,
                new RedirectAttributesModelMap());
    }

    @Test
    void forcedChangeRedirectsToProjectListAndClearsSessionFlag() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ForcedPasswordChangeFilter.SESSION_ATTR, Boolean.TRUE);

        String view = changePassword(command("temp", "new-password", "new-password"),
                session, new ExtendedModelMap());

        assertEquals("redirect:/project/list", view);
        assertEquals(Boolean.FALSE, session.getAttribute(ForcedPasswordChangeFilter.SESSION_ATTR));
        verify(userService).changePassword("admin", "temp", "new-password");
    }

    @Test
    void voluntaryChangeStaysOnAccountPage() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ForcedPasswordChangeFilter.SESSION_ATTR, Boolean.FALSE);

        String view = changePassword(command("old", "new-password", "new-password"),
                session, new ExtendedModelMap());

        assertEquals("redirect:/account/password", view);
    }

    @Test
    void formShowsForcedModeFromDatabaseWhenSessionUncached() {
        User flagged = new User();
        flagged.setPasswordChangeRequired(true);
        when(userService.readByUsername("admin")).thenReturn(flagged);

        MockHttpSession session = new MockHttpSession();
        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.changePasswordForm(model, session, principal);

        assertEquals("account/change-password", view);
        assertEquals(Boolean.TRUE, model.getAttribute("forcedChange"));
        assertEquals(Boolean.TRUE, session.getAttribute(ForcedPasswordChangeFilter.SESSION_ATTR));
    }

    @Test
    void formIsNotForcedForUnflaggedUser() {
        User user = new User();
        when(userService.readByUsername("admin")).thenReturn(user);

        ExtendedModelMap model = new ExtendedModelMap();
        controller.changePasswordForm(model, new MockHttpSession(), principal);

        assertEquals(Boolean.FALSE, model.getAttribute("forcedChange"));
    }

    @Test
    void mismatchKeepsForcedModeOnRerender() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ForcedPasswordChangeFilter.SESSION_ATTR, Boolean.TRUE);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = changePassword(command("temp", "new-password", "different"), session, model);

        assertEquals("account/change-password", view);
        assertEquals(Boolean.TRUE, model.getAttribute("forcedChange"));
    }

    @Test
    void rejectedCurrentPasswordKeepsForcedModeOnRerender() {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Current password is incorrect."))
                .when(userService).changePassword(anyString(), anyString(), anyString());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ForcedPasswordChangeFilter.SESSION_ATTR, Boolean.TRUE);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = changePassword(command("wrong", "new-password", "new-password"), session, model);

        assertEquals("account/change-password", view);
        assertEquals(Boolean.TRUE, model.getAttribute("forcedChange"));
        assertEquals("Current password is incorrect.", model.getAttribute("passwordError"));
    }
}
