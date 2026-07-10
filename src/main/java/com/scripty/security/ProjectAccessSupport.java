package com.scripty.security;

import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.User;
import com.scripty.service.BlockService;
import com.scripty.service.PersonService;
import com.scripty.service.ProjectService;
import com.scripty.service.UserService;
import java.security.Principal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Shared project/block/person access checks for MVC and REST controllers.
 */
@Component
public class ProjectAccessSupport {

    private final ProjectService projectService;
    private final UserService userService;
    private final BlockService blockService;
    private final PersonService personService;

    @Autowired
    public ProjectAccessSupport(ProjectService projectService,
                                UserService userService,
                                BlockService blockService,
                                PersonService personService) {
        this.projectService = projectService;
        this.userService = userService;
        this.blockService = blockService;
        this.personService = personService;
    }

    public User currentUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userService.readByUsername(principal.getName());
    }

    public boolean canAccessProject(Integer projectId, User user) {
        return projectService.canUserAccessProject(projectId, user);
    }

    public boolean canAccessProject(Integer projectId, Principal principal) {
        return canAccessProject(projectId, currentUser(principal));
    }

    public boolean canAccessBlock(Integer blockId, User user) {
        if (blockId == null || user == null) {
            return false;
        }
        Block block = blockService.read(blockId);
        if (block == null || block.getProject() == null) {
            return false;
        }
        return projectService.canUserAccessProject(block.getProject().getId(), user);
    }

    public boolean canAccessBlock(Integer blockId, Principal principal) {
        return canAccessBlock(blockId, currentUser(principal));
    }

    /**
     * Screenplay edits require project access and writer (or admin) permission.
     */
    public boolean canEditScript(Integer projectId, User user) {
        return canAccessProject(projectId, user) && canEditScreenplay(user);
    }

    public boolean canEditScript(Integer projectId, Principal principal) {
        return canEditScript(projectId, currentUser(principal));
    }

    public boolean canEditBlock(Integer blockId, User user) {
        if (blockId == null || !canEditScreenplay(user)) {
            return false;
        }
        return canAccessBlock(blockId, user);
    }

    public boolean canEditBlock(Integer blockId, Principal principal) {
        return canEditBlock(blockId, currentUser(principal));
    }

    public boolean canAccessPerson(Integer personId, User user) {
        if (personId == null || user == null) {
            return false;
        }
        Person person = personService.read(personId);
        if (person == null || person.getProject() == null) {
            return false;
        }
        return projectService.canUserAccessProject(person.getProject().getId(), user);
    }

    public boolean canAccessPerson(Integer personId, Principal principal) {
        return canAccessPerson(personId, currentUser(principal));
    }

    /**
     * Ensures every block belongs to the same project and the user can access it.
     * When {@code expectedProjectId} is non-null, all blocks must belong to that project.
     */
    public boolean canAccessBlocks(List<Integer> blockIds, Integer expectedProjectId, User user) {
        if (user == null || blockIds == null || blockIds.isEmpty()) {
            return false;
        }
        Integer projectId = expectedProjectId;
        for (Integer blockId : blockIds) {
            Block block = blockService.read(blockId);
            if (block == null || block.getProject() == null) {
                return false;
            }
            Integer blockProjectId = block.getProject().getId();
            if (projectId == null) {
                projectId = blockProjectId;
            } else if (!projectId.equals(blockProjectId)) {
                return false;
            }
        }
        return projectService.canUserAccessProject(projectId, user);
    }

    /**
     * Same project validation as {@link #canAccessBlocks}, plus writer/admin edit permission.
     */
    public boolean canEditBlocks(List<Integer> blockIds, Integer expectedProjectId, User user) {
        return canAccessBlocks(blockIds, expectedProjectId, user) && canEditScreenplay(user);
    }

    public Integer projectIdForBlock(Integer blockId) {
        if (blockId == null) {
            return null;
        }
        Block block = blockService.read(blockId);
        return block != null && block.getProject() != null ? block.getProject().getId() : null;
    }

    private static boolean canEditScreenplay(User user) {
        return user != null && (user.isWriter() || user.isAdmin());
    }

    /**
     * Casting pages (actors, auditions, casting character management) require
     * explicit view-casting permission, casting director, or admin.
     */
    public boolean canViewCasting(User user) {
        return user != null && user.isEnabled()
                && (user.isAdmin() || user.isCastingDirector() || user.isViewCasting());
    }

    public boolean canViewCasting(Principal principal) {
        return canViewCasting(currentUser(principal));
    }
}
