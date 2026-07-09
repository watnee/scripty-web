package com.scripty.service;

import com.scripty.commandmodel.invitation.AcceptInvitationCommandModel;
import com.scripty.commandmodel.invitation.SendInvitationCommandModel;
import com.scripty.dto.Invitation;
import com.scripty.dto.Project;
import com.scripty.dto.ProjectActivity;
import com.scripty.dto.Team;
import com.scripty.dto.User;
import com.scripty.repository.InvitationRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.TeamRepository;
import com.scripty.repository.UserRepository;
import com.scripty.viewmodel.invitation.AcceptInvitationViewModel;
import com.scripty.viewmodel.invitation.PendingInvitationViewModel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

@Service
public class InvitationServiceImpl implements InvitationService {

    private static final int INVITE_EXPIRY_DAYS = 14;

    private final InvitationRepository invitationRepository;
    private final TeamRepository teamRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final EmailService emailService;
    private final ProjectService projectService;
    private final ProjectActivityService projectActivityService;
    private final String baseUrl;

    @Autowired
    public InvitationServiceImpl(InvitationRepository invitationRepository,
                                 TeamRepository teamRepository,
                                 ProjectRepository projectRepository,
                                 UserRepository userRepository,
                                 UserService userService,
                                 EmailService emailService,
                                 ProjectService projectService,
                                 ProjectActivityService projectActivityService,
                                 @Value("${app.base-url:}") String baseUrl) {
        this.invitationRepository = invitationRepository;
        this.teamRepository = teamRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.emailService = emailService;
        this.projectService = projectService;
        this.projectActivityService = projectActivityService;
        this.baseUrl = baseUrl;
    }

    @Override
    @Transactional
    public Invitation sendInvitation(SendInvitationCommandModel command, User invitedBy) {
        if (invitedBy == null) {
            throw new IllegalStateException("You must be signed in to send invitations.");
        }

        String email = normalizeEmail(command.getEmail());
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Email is required.");
        }

        Team team = teamRepository.findById(command.getTeamId()).orElse(null);
        if (team == null) {
            throw new IllegalArgumentException("Team not found.");
        }

        Project project = null;
        if (command.getProjectId() != null) {
            project = projectRepository.findWithTeamsById(command.getProjectId()).orElse(null);
            if (project == null) {
                throw new IllegalArgumentException("Project not found.");
            }
            if (!projectService.canUserAccessProject(project, invitedBy)) {
                throw new IllegalArgumentException("You do not have access to this project.");
            }
            if (!project.isAssignedToTeam(team)) {
                throw new IllegalArgumentException("That team is not assigned to this project.");
            }
        } else {
            throw new IllegalArgumentException("Project is required.");
        }

        // Avoid revealing whether the email already has an account.
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return null;
        }

        invitationRepository.findFirstByEmailIgnoreCaseAndTeamIdAndStatus(
                email, team.getId(), Invitation.STATUS_PENDING
        ).ifPresent(existing -> {
            existing.setStatus(Invitation.STATUS_REVOKED);
            invitationRepository.save(existing);
        });

        Invitation invitation = new Invitation();
        invitation.setEmail(email);
        invitation.setToken(UUID.randomUUID().toString().replace("-", ""));
        invitation.setTeam(team);
        invitation.setProject(project);
        invitation.setInvitedBy(invitedBy);
        invitation.setStatus(Invitation.STATUS_PENDING);
        LocalDateTime now = LocalDateTime.now();
        invitation.setCreatedAt(now);
        invitation.setExpiresAt(now.plusDays(INVITE_EXPIRY_DAYS));
        Invitation saved = invitationRepository.save(invitation);

        sendInviteEmail(saved, invitedBy);
        projectActivityService.record(
                project.getId(),
                invitedBy.getId(),
                ProjectActivity.ACTION_INVITATION_SENT,
                "invited " + email + " to " + team.getName(),
                ProjectActivity.ENTITY_INVITATION,
                saved.getId());
        return saved;
    }

    @Override
    public List<PendingInvitationViewModel> getPendingInvitationsForProject(Integer projectId, User currentUser) {
        if (projectId == null || currentUser == null
                || !projectService.canUserAccessProject(projectId, currentUser)) {
            return List.of();
        }
        List<Invitation> invitations = invitationRepository.findByProjectIdAndStatus(
                projectId, Invitation.STATUS_PENDING);
        List<PendingInvitationViewModel> result = new ArrayList<>();
        for (Invitation invitation : invitations) {
            if (invitation.isExpired()) {
                continue;
            }
            PendingInvitationViewModel vm = new PendingInvitationViewModel();
            vm.setId(invitation.getId());
            vm.setEmail(invitation.getEmail());
            vm.setTeamName(invitation.getTeam() != null ? invitation.getTeam().getName() : "");
            vm.setStatusLabel("Pending");
            result.add(vm);
        }
        return result;
    }

    @Override
    public AcceptInvitationViewModel getAcceptViewModel(String token) {
        AcceptInvitationViewModel vm = new AcceptInvitationViewModel();
        vm.setToken(token);
        Invitation invitation = findUsableInvitation(token);
        if (invitation == null) {
            vm.setValid(false);
            vm.setErrorMessage("This invitation link is invalid or has expired.");
            return vm;
        }
        vm.setValid(true);
        vm.setEmail(invitation.getEmail());
        vm.setTeamName(invitation.getTeam().getName());
        if (invitation.getProject() != null) {
            vm.setProjectTitle(invitation.getProject().getTitle());
        }
        return vm;
    }

    @Override
    @Transactional
    public User acceptInvitation(AcceptInvitationCommandModel command) {
        Invitation invitation = findUsableInvitation(command.getToken());
        if (invitation == null) {
            throw new IllegalArgumentException("This invitation link is invalid or has expired.");
        }

        // Claim the invite atomically before creating the user (prevents double-accept races).
        int claimed = invitationRepository.claimPendingInvitation(
                invitation.getId(), Invitation.STATUS_ACCEPTED, LocalDateTime.now());
        if (claimed != 1) {
            throw new IllegalArgumentException("This invitation link is invalid or has expired.");
        }

        String username = command.getUsername() != null ? command.getUsername().trim() : "";
        if (userRepository.findByUsername(username).isPresent()) {
            restorePendingInvitation(invitation);
            throw new IllegalArgumentException("That username is already taken.");
        }

        if (userRepository.findByEmailIgnoreCase(invitation.getEmail()).isPresent()) {
            invitation.setStatus(Invitation.STATUS_REVOKED);
            invitation.setAcceptedAt(null);
            invitationRepository.save(invitation);
            throw new IllegalArgumentException("This invitation link is invalid or has expired.");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(command.getPassword());
        user.setFirstName(command.getFirstName().trim());
        user.setLastName(command.getLastName().trim());
        user.setEmail(invitation.getEmail());
        user.setTeam(invitation.getTeam().getName());
        user.setEnabled(true);
        if (invitation.getProject() != null) {
            user.setDefaultProjectId(invitation.getProject().getId());
        }

        try {
            User created = userService.create(user);
            if (invitation.getProject() != null) {
                projectActivityService.record(
                        invitation.getProject().getId(),
                        created.getId(),
                        ProjectActivity.ACTION_INVITATION_ACCEPTED,
                        "joined the project",
                        ProjectActivity.ENTITY_INVITATION,
                        invitation.getId());
            }
            return created;
        } catch (RuntimeException e) {
            restorePendingInvitation(invitation);
            throw e;
        }
    }

    private void restorePendingInvitation(Invitation invitation) {
        invitation.setStatus(Invitation.STATUS_PENDING);
        invitation.setAcceptedAt(null);
        invitationRepository.save(invitation);
    }

    @Override
    @Transactional
    public void revoke(Integer invitationId, Integer projectId, User currentUser) {
        if (currentUser == null || invitationId == null || projectId == null) {
            throw new IllegalArgumentException("You do not have access to this invitation.");
        }
        if (!projectService.canUserAccessProject(projectId, currentUser)) {
            throw new IllegalArgumentException("You do not have access to this project.");
        }

        Invitation invitation = invitationRepository.findById(invitationId).orElse(null);
        if (invitation == null || !invitation.isPending()) {
            return;
        }
        if (invitation.getProject() == null || !projectId.equals(invitation.getProject().getId())) {
            throw new IllegalArgumentException("You do not have access to this invitation.");
        }

        invitation.setStatus(Invitation.STATUS_REVOKED);
        invitationRepository.save(invitation);
        projectActivityService.record(
                projectId,
                currentUser.getId(),
                ProjectActivity.ACTION_INVITATION_REVOKED,
                "revoked invitation for " + invitation.getEmail(),
                ProjectActivity.ENTITY_INVITATION,
                invitationId);
    }

    private Invitation findUsableInvitation(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        Invitation invitation = invitationRepository.findWithDetailsByToken(token.trim()).orElse(null);
        if (invitation == null || !invitation.isPending() || invitation.isExpired()) {
            return null;
        }
        return invitation;
    }

    private void sendInviteEmail(Invitation invitation, User invitedBy) {
        String acceptUrl = resolveBaseUrl() + "/invitation/accept?token=" + invitation.getToken();
        String inviterName = formatInviterName(invitedBy);
        String teamName = invitation.getTeam().getName();
        String projectLine = invitation.getProject() != null
                ? "<p>You've been invited to collaborate on <strong>"
                    + HtmlUtils.htmlEscape(invitation.getProject().getTitle())
                    + "</strong>.</p>"
                : "";

        String subject = "You're invited to Scripty";
        String body = """
                <div style="font-family: Georgia, 'Times New Roman', serif; line-height: 1.5; color: #1f2937;">
                  <p>Hi,</p>
                  <p><strong>%s</strong> invited you to join the <strong>%s</strong> team on Scripty.</p>
                  %s
                  <p><a href="%s" style="display:inline-block;padding:10px 16px;background:#1f2937;color:#fff;text-decoration:none;border-radius:6px;">Accept invitation</a></p>
                  <p style="font-size:13px;color:#6b7280;">Or open this link:<br>%s</p>
                  <p style="font-size:13px;color:#6b7280;">This invite expires in %d days.</p>
                </div>
                """.formatted(
                HtmlUtils.htmlEscape(inviterName),
                HtmlUtils.htmlEscape(teamName),
                projectLine,
                acceptUrl,
                HtmlUtils.htmlEscape(acceptUrl),
                INVITE_EXPIRY_DAYS
        );
        emailService.send(invitation.getEmail(), subject, body);
    }

    private String resolveBaseUrl() {
        if (StringUtils.hasText(baseUrl)) {
            String trimmed = baseUrl.trim();
            if (trimmed.endsWith("/")) {
                return trimmed.substring(0, trimmed.length() - 1);
            }
            return trimmed;
        }
        return "http://localhost:8080";
    }

    private String formatInviterName(User invitedBy) {
        if (invitedBy == null) {
            return "Someone";
        }
        String first = invitedBy.getFirstName() != null ? invitedBy.getFirstName().trim() : "";
        String last = invitedBy.getLastName() != null ? invitedBy.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) {
            return full;
        }
        return invitedBy.getUsername() != null ? invitedBy.getUsername() : "Someone";
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
