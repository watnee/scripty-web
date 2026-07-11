package com.scripty.viewmodel.user.userprofile;

import java.util.ArrayList;
import java.util.List;

public class UserProfileViewModel {

    private int id;
    private String username;
    private String firstName;
    private String lastName;
    private String team;
    private boolean enabled;
    private boolean admin;
    private boolean director;
    private boolean producer;
    private boolean writer;
    private boolean actor;
    private boolean crew;
    private boolean directorOfPhotography;
    private boolean castingDirector;
    private boolean viewCasting;
    private boolean developer;
    private boolean canEditScreenplay;
    private boolean canViewCastingPages;
    private boolean privilegedProjectAccess;
    private List<UserProjectAccessViewModel> projectAccess = new ArrayList<>();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public boolean isDirector() {
        return director;
    }

    public void setDirector(boolean director) {
        this.director = director;
    }

    public boolean isProducer() {
        return producer;
    }

    public void setProducer(boolean producer) {
        this.producer = producer;
    }

    public boolean isWriter() {
        return writer;
    }

    public void setWriter(boolean writer) {
        this.writer = writer;
    }

    public boolean isActor() {
        return actor;
    }

    public void setActor(boolean actor) {
        this.actor = actor;
    }

    public boolean isCrew() {
        return crew;
    }

    public void setCrew(boolean crew) {
        this.crew = crew;
    }

    public boolean isDirectorOfPhotography() {
        return directorOfPhotography;
    }

    public void setDirectorOfPhotography(boolean directorOfPhotography) {
        this.directorOfPhotography = directorOfPhotography;
    }

    public boolean isCastingDirector() {
        return castingDirector;
    }

    public void setCastingDirector(boolean castingDirector) {
        this.castingDirector = castingDirector;
    }

    public boolean isViewCasting() {
        return viewCasting;
    }

    public void setViewCasting(boolean viewCasting) {
        this.viewCasting = viewCasting;
    }

    public boolean isDeveloper() {
        return developer;
    }

    public void setDeveloper(boolean developer) {
        this.developer = developer;
    }

    public boolean isCanEditScreenplay() {
        return canEditScreenplay;
    }

    public void setCanEditScreenplay(boolean canEditScreenplay) {
        this.canEditScreenplay = canEditScreenplay;
    }

    public boolean isCanViewCastingPages() {
        return canViewCastingPages;
    }

    public void setCanViewCastingPages(boolean canViewCastingPages) {
        this.canViewCastingPages = canViewCastingPages;
    }

    public boolean isPrivilegedProjectAccess() {
        return privilegedProjectAccess;
    }

    public void setPrivilegedProjectAccess(boolean privilegedProjectAccess) {
        this.privilegedProjectAccess = privilegedProjectAccess;
    }

    public List<UserProjectAccessViewModel> getProjectAccess() {
        return projectAccess;
    }

    public void setProjectAccess(List<UserProjectAccessViewModel> projectAccess) {
        this.projectAccess = projectAccess != null ? projectAccess : new ArrayList<>();
    }

    public String getRolesLabel() {
        List<String> roles = new ArrayList<>();
        if (admin) {
            roles.add("Admin");
        }
        if (director) {
            roles.add("Director");
        }
        if (producer) {
            roles.add("Producer");
        }
        if (writer) {
            roles.add("Writer");
        }
        if (actor) {
            roles.add("Actor");
        }
        if (crew) {
            roles.add("Crew");
        }
        if (directorOfPhotography) {
            roles.add("Director of Photography");
        }
        if (castingDirector) {
            roles.add("Casting Director");
        }
        if (viewCasting) {
            roles.add("View Casting");
        }
        if (developer) {
            roles.add("Developer");
        }
        if (roles.isEmpty()) {
            return "User";
        }
        return String.join(", ", roles);
    }
}
