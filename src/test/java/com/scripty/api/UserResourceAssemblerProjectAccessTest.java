package com.scripty.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scripty.viewmodel.user.userlist.UserViewModel;
import com.scripty.viewmodel.user.userprofile.UserProfileViewModel;
import com.scripty.viewmodel.user.userprofile.UserProjectAccessViewModel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.EntityModel;

/**
 * The per-project access breakdown carried on the single-user profile resource.
 * The service already computes it; this pins that the assembler surfaces it on
 * the {@code GET /api/user/{id}} (profile) path and, crucially, NOT on the list
 * path — matching the web, where only the profile page shows access.
 */
class UserResourceAssemblerProjectAccessTest {

    private final UserResourceAssembler assembler = new UserResourceAssembler();

    private UserProjectAccessViewModel access(int id, String name, boolean canEdit,
                                              String label, String reason) {
        UserProjectAccessViewModel vm = new UserProjectAccessViewModel();
        vm.setProjectId(id);
        vm.setProjectName(name);
        vm.setCanEdit(canEdit);
        vm.setPermissionLabel(label);
        vm.setAccessReason(reason);
        return vm;
    }

    @Test
    void profileCarriesTheProjectAccessRows() {
        UserProfileViewModel profile = new UserProfileViewModel();
        profile.setId(7);
        profile.setUsername("wes");
        profile.setEnabled(true);
        profile.setWriter(true);
        profile.setProjectAccess(List.of(
                access(1, "The Last Take", true, "Can edit", "Writer"),
                access(2, "Dust & Neon", true, "Can edit", "Team A")));

        UserResource resource = assembler.toModel(profile).getContent();

        List<UserProjectAccessResource> rows = resource.getProjectAccess();
        assertEquals(2, rows.size(), "both accessible projects are carried");
        assertEquals(1, rows.get(0).getProjectId());
        assertEquals("The Last Take", rows.get(0).getProjectName());
        assertTrue(rows.get(0).getCanEdit());
        assertEquals("Can edit", rows.get(0).getPermissionLabel());
        assertEquals("Writer", rows.get(0).getAccessReason());
        assertEquals("Team A", rows.get(1).getAccessReason());
    }

    @Test
    void aDisabledUsersEmptyAccessStaysAnEmptyList() {
        UserProfileViewModel profile = new UserProfileViewModel();
        profile.setId(3);
        profile.setEnabled(false);
        profile.setProjectAccess(List.of());

        EntityModel<UserResource> model = assembler.toModel(profile);

        assertTrue(model.getContent().getProjectAccess().isEmpty(),
                "an enabled-but-no-access or disabled user is an empty list, not null");
    }

    @Test
    void theListPathOmitsProjectAccess() {
        UserViewModel user = new UserViewModel();
        user.setId(5);
        user.setUsername("member");
        user.setEnabled(true);

        UserResource resource = assembler.toModel(user).getContent();

        assertNull(resource.getProjectAccess(),
                "the collection must not carry the access breakdown — NON_NULL omits it, "
                        + "so the list stays lean and only the profile shows access");
    }
}
