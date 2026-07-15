package com.scripty.api;

import com.scripty.controller.TeamRestController;
import com.scripty.controller.UserRestController;
import com.scripty.dto.User;
import com.scripty.service.UserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * The signed-in user's own account. Unlike {@code /api/user} (admin-only
 * directory), this is readable by any authenticated user, so native clients
 * have a "me" resource for the user menu and a self-service password change.
 *
 * <p>Admin-only rels ({@code users}, {@code teams}) are advertised here only
 * when the caller is an admin, so clients gate those affordances on link
 * presence rather than re-deriving authorization.
 */
@RestController
@RequestMapping(value = "/api/account")
public class AccountRestController {

    @Autowired
    UserService userService;

    @GetMapping(produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<AccountResource> account(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userService.readByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AccountResource resource = new AccountResource();
        resource.setUsername(user.getUsername());
        resource.setFirstName(user.getFirstName());
        resource.setLastName(user.getLastName());
        resource.setAdmin(user.isAdmin());

        resource.add(linkTo(methodOn(AccountRestController.class).account(null)).withSelfRel());
        resource.add(linkTo(methodOn(AccountRestController.class)
                .changePassword(null, null, null)).withRel(ApiRel.CHANGE_PASSWORD));
        if (user.isAdmin()) {
            resource.add(linkTo(methodOn(UserRestController.class).list()).withRel(ApiRel.USERS));
            resource.add(linkTo(methodOn(TeamRestController.class).list()).withRel(ApiRel.TEAMS));
        }
        return ResponseEntity.ok(resource);
    }

    @PutMapping(value = "/password", consumes = "application/json", produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordApiRequest request,
            BindingResult bindingResult,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(RestErrors.from(bindingResult));
        }
        try {
            userService.changePassword(
                    principal.getName(),
                    request.getCurrentPassword(),
                    request.getNewPassword());
        } catch (IllegalArgumentException e) {
            // Surface the service's message on the field it most likely concerns,
            // so clients (and the existing 400 handling) can present it inline.
            String message = e.getMessage() == null ? "Unable to change password." : e.getMessage();
            String field = message.toLowerCase().contains("current") ? "currentPassword" : "newPassword";
            return ResponseEntity.badRequest().body(Map.of(field, message));
        }
        return ResponseEntity.noContent().build();
    }
}
