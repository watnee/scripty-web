package com.scripty.service;

import com.scripty.commandmodel.invitation.SendViewInvitationCommandModel;
import com.scripty.dto.Project;
import com.scripty.dto.ProjectActivity;
import com.scripty.dto.User;
import com.scripty.dto.ViewInvitation;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.ViewInvitationRepository;
import com.scripty.viewmodel.invitation.ScreenplayViewViewModel;
import com.scripty.viewmodel.invitation.ViewInvitationViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;
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
public class ViewInvitationServiceImpl implements ViewInvitationService {

    private static final int VIEW_INVITE_EXPIRY_DAYS = 30;

    private final ViewInvitationRepository viewInvitationRepository;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final EmailService emailService;
    private final PdfExportService pdfExportService;
    private final ProjectActivityService projectActivityService;
    private final String baseUrl;

    @Autowired
    public ViewInvitationServiceImpl(ViewInvitationRepository viewInvitationRepository,
                                     ProjectRepository projectRepository,
                                     ProjectService projectService,
                                     EmailService emailService,
                                     PdfExportService pdfExportService,
                                     ProjectActivityService projectActivityService,
                                     @Value("${app.base-url:}") String baseUrl) {
        this.viewInvitationRepository = viewInvitationRepository;
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.emailService = emailService;
        this.pdfExportService = pdfExportService;
        this.projectActivityService = projectActivityService;
        this.baseUrl = baseUrl;
    }

    @Override
    @Transactional
    public ViewInvitation sendInvitation(SendViewInvitationCommandModel command, User invitedBy) {
        if (invitedBy == null) {
            throw new IllegalStateException("You must be signed in to send invitations.");
        }

        String email = normalizeEmail(command.getEmail());
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (command.getProjectId() == null) {
            throw new IllegalArgumentException("Project is required.");
        }

        Project project = projectRepository.findWithTeamsById(command.getProjectId()).orElse(null);
        if (project == null) {
            throw new IllegalArgumentException("Project not found.");
        }
        if (!projectService.canUserAccessProject(project, invitedBy)) {
            throw new IllegalArgumentException("You do not have access to this project.");
        }

        // A fresh invite replaces any live link the same address already holds.
        viewInvitationRepository.findFirstByEmailIgnoreCaseAndProjectIdAndStatus(
                email, project.getId(), ViewInvitation.STATUS_ACTIVE
        ).ifPresent(existing -> {
            existing.setStatus(ViewInvitation.STATUS_REVOKED);
            viewInvitationRepository.save(existing);
        });

        ViewInvitation invitation = new ViewInvitation();
        invitation.setEmail(email);
        invitation.setToken(UUID.randomUUID().toString().replace("-", ""));
        invitation.setProject(project);
        invitation.setInvitedBy(invitedBy);
        invitation.setStatus(ViewInvitation.STATUS_ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        invitation.setCreatedAt(now);
        invitation.setExpiresAt(now.plusDays(VIEW_INVITE_EXPIRY_DAYS));
        ViewInvitation saved = viewInvitationRepository.save(invitation);

        sendViewInviteEmail(saved, invitedBy, project, command.isAttachPdf());
        projectActivityService.record(
                project.getId(),
                invitedBy.getId(),
                ProjectActivity.ACTION_INVITATION_SENT,
                "invited " + email + " to view the screenplay",
                ProjectActivity.ENTITY_INVITATION,
                saved.getId());
        return saved;
    }

    @Override
    public List<ViewInvitationViewModel> getActiveInvitationsForProject(Integer projectId, User currentUser) {
        if (projectId == null || currentUser == null
                || !projectService.canUserAccessProject(projectId, currentUser)) {
            return List.of();
        }
        List<ViewInvitation> invitations = viewInvitationRepository.findByProjectIdAndStatus(
                projectId, ViewInvitation.STATUS_ACTIVE);
        List<ViewInvitationViewModel> result = new ArrayList<>();
        for (ViewInvitation invitation : invitations) {
            if (invitation.isExpired()) {
                continue;
            }
            ViewInvitationViewModel vm = new ViewInvitationViewModel();
            vm.setId(invitation.getId());
            vm.setEmail(invitation.getEmail());
            vm.setStatusLabel(invitation.getLastViewedAt() != null ? "Viewed" : "Not viewed yet");
            result.add(vm);
        }
        return result;
    }

    @Override
    @Transactional
    public ScreenplayViewViewModel getScreenplayForToken(String token) {
        ScreenplayViewViewModel vm = new ScreenplayViewViewModel();
        ViewInvitation invitation = findUsableInvitation(token);
        if (invitation == null) {
            vm.setValid(false);
            vm.setErrorMessage("This viewing link is invalid, expired, or has been revoked.");
            return vm;
        }

        // Viewers always get the shared edition (canBrowseEditions=false), same as
        // signed-in users without edit rights.
        ProjectProfileViewModel screenplay = projectService.getProjectProfileViewModel(
                invitation.getProject().getId(), null, false);
        if (screenplay == null) {
            vm.setValid(false);
            vm.setErrorMessage("This screenplay is no longer available.");
            return vm;
        }

        invitation.setLastViewedAt(LocalDateTime.now());
        invitation.setViewCount(invitation.getViewCount() + 1);
        viewInvitationRepository.save(invitation);

        vm.setValid(true);
        vm.setScreenplay(screenplay);
        return vm;
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

        ViewInvitation invitation = viewInvitationRepository.findById(invitationId).orElse(null);
        if (invitation == null || !invitation.isActive()) {
            return;
        }
        if (invitation.getProject() == null || !projectId.equals(invitation.getProject().getId())) {
            throw new IllegalArgumentException("You do not have access to this invitation.");
        }

        invitation.setStatus(ViewInvitation.STATUS_REVOKED);
        viewInvitationRepository.save(invitation);
        projectActivityService.record(
                projectId,
                currentUser.getId(),
                ProjectActivity.ACTION_INVITATION_REVOKED,
                "revoked view access for " + invitation.getEmail(),
                ProjectActivity.ENTITY_INVITATION,
                invitationId);
    }

    private ViewInvitation findUsableInvitation(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        ViewInvitation invitation =
                viewInvitationRepository.findWithProjectByToken(token.trim()).orElse(null);
        if (invitation == null || !invitation.isActive() || invitation.isExpired()) {
            return null;
        }
        return invitation;
    }

    private void sendViewInviteEmail(ViewInvitation invitation, User invitedBy, Project project,
                                     boolean attachPdf) {
        String viewUrl = resolveBaseUrl() + "/view?token=" + invitation.getToken();
        String inviterName = formatInviterName(invitedBy);
        String title = project.getTitle() != null && !project.getTitle().isBlank()
                ? project.getTitle()
                : "Untitled Project";

        EmailAttachment attachment = attachPdf ? buildPdfAttachment(project) : null;
        String attachmentNote = attachment != null
                ? "<p style=\"font-size:13px;color:#6b7280;\">A PDF copy of the screenplay is attached to this email.</p>"
                : "";

        String subject = inviterName + " shared a screenplay with you";
        String body = """
                <div style="font-family: Georgia, 'Times New Roman', serif; line-height: 1.5; color: #1f2937;">
                  <p>Hi,</p>
                  <p><strong>%s</strong> invited you to read <strong>%s</strong> on Scripty.</p>
                  <p><a href="%s" style="display:inline-block;padding:10px 16px;background:#1f2937;color:#fff;text-decoration:none;border-radius:6px;">Read the screenplay</a></p>
                  <p style="font-size:13px;color:#6b7280;">Or open this link:<br>%s</p>
                  %s
                  <p style="font-size:13px;color:#6b7280;">This link is view-only and expires in %d days. Please don't forward it.</p>
                </div>
                """.formatted(
                HtmlUtils.htmlEscape(inviterName),
                HtmlUtils.htmlEscape(title),
                viewUrl,
                HtmlUtils.htmlEscape(viewUrl),
                attachmentNote,
                VIEW_INVITE_EXPIRY_DAYS
        );
        emailService.send(invitation.getEmail(), subject, body, attachment);
    }

    private EmailAttachment buildPdfAttachment(Project project) {
        byte[] pdf = pdfExportService.exportProject(project.getId());
        return new EmailAttachment(pdfFilename(project), "application/pdf", pdf);
    }

    private String pdfFilename(Project project) {
        String base = null;
        if (project.getScreenplayTitle() != null && !project.getScreenplayTitle().isBlank()) {
            base = project.getScreenplayTitle();
        } else if (project.getTitle() != null && !project.getTitle().isBlank()) {
            base = project.getTitle();
        }
        if (base == null) {
            return "screenplay.pdf";
        }
        String sanitized = base.trim()
                .replaceAll("[\\\\/:*?\"<>|]+", "-")
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-zA-Z0-9._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[.-]+|[.-]+$", "");
        if (sanitized.isBlank()) {
            return "screenplay.pdf";
        }
        if (sanitized.length() > 80) {
            sanitized = sanitized.substring(0, 80).replaceAll("[.-]+$", "");
        }
        return sanitized + ".pdf";
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
