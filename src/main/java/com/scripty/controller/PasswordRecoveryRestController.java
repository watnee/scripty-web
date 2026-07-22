package com.scripty.controller;

import com.scripty.api.ApiRel;
import com.scripty.api.ResetPasswordRequest;
import com.scripty.api.RestErrors;
import com.scripty.dto.PasswordRecoveryToken;
import com.scripty.service.PasswordRecoveryService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Password recovery over the API — the counterpart of
 * {@link ForgotPasswordController}, for a client that has no browser to send
 * someone to.
 *
 * <p>Everything here runs for a caller who is, by definition, signed out, which
 * is the one place the usual "advertise it and let the client follow it" rule
 * needs help: an anonymous caller cannot read a root full of authenticated
 * links. {@link com.scripty.api.ApiRootController} answers such a caller with a
 * root holding just {@code forgotPassword}, so this is still reached by
 * following a link rather than by a client knowing the path.
 *
 * <p>The enumeration rule from the web flow carries over exactly: asking for a
 * reset always succeeds, whether or not the address belongs to anyone. A
 * different answer for a real address would turn this into a way of testing who
 * has an account.
 */
@RestController
@RequestMapping("/api/forgot-password")
public class PasswordRecoveryRestController {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryRestController.class);

    private static final String GENERIC_SENT =
            "If that address is registered, instructions to reset your password have been sent.";

    private final PasswordRecoveryService recoveryService;

    @Autowired
    public PasswordRecoveryRestController(PasswordRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    /**
     * Sends a recovery email. Always 202, always the same body — see the class
     * comment. A failure to send is an operator's problem, not the caller's, so
     * it is logged rather than reported.
     */
    @PostMapping(consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<EntityModel<Map<String, Object>>> request(
            @RequestBody Map<String, String> body) {
        String email = body == null ? null : body.get("email");
        if (email != null && !email.isBlank()) {
            try {
                recoveryService.sendRecoveryEmail(email);
            } catch (Exception e) {
                log.error("Password recovery email failed", e);
            }
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("message", GENERIC_SENT);
        return ResponseEntity.accepted().body(
                EntityModel.of(answer,
                        linkTo(methodOn(PasswordRecoveryRestController.class).reset(null))
                                .withRel(ApiRel.RESET_PASSWORD)));
    }

    /**
     * Says whether a token from a recovery email is still good, and whose
     * account it belongs to — so a client can show who it is about to reset
     * before asking anyone to type a new password.
     *
     * <p>An expired or unknown token is a 200 saying so rather than a 404: it is
     * a perfectly ordinary outcome of a link sat in an inbox for a week, and the
     * caller needs the reason to show.
     */
    @GetMapping(value = "/reset", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<EntityModel<Map<String, Object>>> checkToken(@RequestParam String token) {
        Map<String, Object> answer = new LinkedHashMap<>();
        try {
            PasswordRecoveryToken recoveryToken = recoveryService.validateToken(token);
            answer.put("valid", true);
            answer.put("email", recoveryToken.getUser().getEmail());
            return ResponseEntity.ok(
                    EntityModel.of(answer,
                            linkTo(methodOn(PasswordRecoveryRestController.class).reset(null))
                                    .withRel(ApiRel.RESET_PASSWORD)));
        } catch (IllegalArgumentException e) {
            answer.put("valid", false);
            answer.put("message", e.getMessage());
            // No `resetPassword` link: there is nothing this token can do.
            return ResponseEntity.ok(EntityModel.of(answer));
        }
    }

    /**
     * Sets the new password. The service enforces the password policy, and its
     * message names the rule that was broken, so it is passed on as-is.
     *
     * <p>No confirm-password field, unlike the web form: retyping guards against
     * a typo in a field nobody can see, and a client that shows what was typed
     * has no such problem. Whether to ask twice is the client's call.
     */
    @PostMapping(value = "/reset", consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> reset(@RequestBody ResetPasswordRequest request) {
        if (request == null || request.token() == null || request.token().isBlank()) {
            return new ResponseEntity<>(
                    RestErrors.of("token", "A recovery token is required."), HttpStatus.BAD_REQUEST);
        }
        if (request.password() == null || request.password().isEmpty()) {
            return new ResponseEntity<>(
                    RestErrors.of("password", "Password is required."), HttpStatus.BAD_REQUEST);
        }
        try {
            recoveryService.resetPassword(request.token(), request.password());
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(
                    RestErrors.of("password", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("message", "Your password has been reset. Sign in with your new password.");
        return ResponseEntity.ok(EntityModel.of(answer));
    }
}
