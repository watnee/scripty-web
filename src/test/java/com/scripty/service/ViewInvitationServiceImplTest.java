package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.commandmodel.invitation.SendViewInvitationCommandModel;
import com.scripty.dto.Project;
import com.scripty.dto.User;
import com.scripty.dto.ViewInvitation;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.ViewInvitationRepository;
import com.scripty.viewmodel.invitation.ScreenplayViewViewModel;
import com.scripty.viewmodel.invitation.ViewInvitationViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ViewInvitationServiceImplTest {

    private ViewInvitationRepository viewInvitationRepository;
    private ProjectRepository projectRepository;
    private ProjectService projectService;
    private EmailService emailService;
    private ProjectActivityService projectActivityService;
    private ViewInvitationServiceImpl service;

    private Project project;
    private User inviter;

    @BeforeEach
    void setUp() {
        viewInvitationRepository = mock(ViewInvitationRepository.class);
        projectRepository = mock(ProjectRepository.class);
        projectService = mock(ProjectService.class);
        emailService = mock(EmailService.class);
        projectActivityService = mock(ProjectActivityService.class);
        service = new ViewInvitationServiceImpl(
                viewInvitationRepository, projectRepository, projectService,
                emailService, projectActivityService, "https://scripty.example");

        project = new Project();
        project.setId(7);
        project.setTitle("The Big Script");

        inviter = new User();
        inviter.setId(3);
        inviter.setUsername("director");
        inviter.setFirstName("Dana");
        inviter.setLastName("Director");

        when(viewInvitationRepository.save(any(ViewInvitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private SendViewInvitationCommandModel command(String email) {
        SendViewInvitationCommandModel command = new SendViewInvitationCommandModel();
        command.setProjectId(7);
        command.setEmail(email);
        return command;
    }

    @Test
    void sendInvitationCreatesTokenEmailsLinkAndRecordsActivity() {
        when(projectRepository.findWithTeamsById(7)).thenReturn(Optional.of(project));
        when(projectService.canUserAccessProject(project, inviter)).thenReturn(true);

        ViewInvitation saved = service.sendInvitation(command("Reader@Example.com "), inviter);

        assertNotNull(saved);
        assertEquals("reader@example.com", saved.getEmail());
        assertNotNull(saved.getToken());
        assertEquals(32, saved.getToken().length());
        assertEquals(ViewInvitation.STATUS_ACTIVE, saved.getStatus());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now().plusDays(29)));

        verify(emailService).send(eq("reader@example.com"), anyString(),
                contains("/view?token=" + saved.getToken()));
        verify(projectActivityService).record(eq(7), eq(3), anyString(),
                contains("view the screenplay"), anyString(), any());
    }

    @Test
    void sendInvitationRevokesExistingActiveInviteForSameEmail() {
        when(projectRepository.findWithTeamsById(7)).thenReturn(Optional.of(project));
        when(projectService.canUserAccessProject(project, inviter)).thenReturn(true);

        ViewInvitation existing = new ViewInvitation();
        existing.setId(11);
        existing.setStatus(ViewInvitation.STATUS_ACTIVE);
        when(viewInvitationRepository.findFirstByEmailIgnoreCaseAndProjectIdAndStatus(
                "reader@example.com", 7, ViewInvitation.STATUS_ACTIVE))
                .thenReturn(Optional.of(existing));

        service.sendInvitation(command("reader@example.com"), inviter);

        assertEquals(ViewInvitation.STATUS_REVOKED, existing.getStatus());
        verify(viewInvitationRepository).save(existing);
    }

    @Test
    void sendInvitationRejectsUsersWithoutProjectAccess() {
        when(projectRepository.findWithTeamsById(7)).thenReturn(Optional.of(project));
        when(projectService.canUserAccessProject(project, inviter)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.sendInvitation(command("reader@example.com"), inviter));
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void sendInvitationRequiresSignedInUser() {
        assertThrows(IllegalStateException.class,
                () -> service.sendInvitation(command("reader@example.com"), null));
    }

    @Test
    void getScreenplayForTokenReturnsInvalidForUnknownToken() {
        when(viewInvitationRepository.findWithProjectByToken("nope")).thenReturn(Optional.empty());

        ScreenplayViewViewModel vm = service.getScreenplayForToken("nope");

        assertFalse(vm.isValid());
        assertNotNull(vm.getErrorMessage());
    }

    @Test
    void getScreenplayForTokenReturnsInvalidForExpiredInvite() {
        ViewInvitation invitation = activeInvitation();
        invitation.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(viewInvitationRepository.findWithProjectByToken("tok")).thenReturn(Optional.of(invitation));

        assertFalse(service.getScreenplayForToken("tok").isValid());
    }

    @Test
    void getScreenplayForTokenReturnsInvalidForRevokedInvite() {
        ViewInvitation invitation = activeInvitation();
        invitation.setStatus(ViewInvitation.STATUS_REVOKED);
        when(viewInvitationRepository.findWithProjectByToken("tok")).thenReturn(Optional.of(invitation));

        assertFalse(service.getScreenplayForToken("tok").isValid());
    }

    @Test
    void getScreenplayForTokenReturnsScreenplayAndTracksView() {
        ViewInvitation invitation = activeInvitation();
        when(viewInvitationRepository.findWithProjectByToken("tok")).thenReturn(Optional.of(invitation));
        ProjectProfileViewModel screenplay = new ProjectProfileViewModel();
        when(projectService.getProjectProfileViewModel(7, null, false)).thenReturn(screenplay);

        ScreenplayViewViewModel vm = service.getScreenplayForToken("tok");

        assertTrue(vm.isValid());
        assertEquals(screenplay, vm.getScreenplay());
        assertEquals(1, invitation.getViewCount());
        assertNotNull(invitation.getLastViewedAt());
        verify(viewInvitationRepository).save(invitation);
    }

    @Test
    void getActiveInvitationsHidesExpiredAndLabelsViewed() {
        User user = inviter;
        when(projectService.canUserAccessProject(7, user)).thenReturn(true);

        ViewInvitation viewed = activeInvitation();
        viewed.setId(1);
        viewed.setEmail("viewed@example.com");
        viewed.setLastViewedAt(LocalDateTime.now());
        ViewInvitation expired = activeInvitation();
        expired.setId(2);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(viewInvitationRepository.findByProjectIdAndStatus(7, ViewInvitation.STATUS_ACTIVE))
                .thenReturn(List.of(viewed, expired));

        List<ViewInvitationViewModel> result = service.getActiveInvitationsForProject(7, user);

        assertEquals(1, result.size());
        assertEquals("viewed@example.com", result.get(0).getEmail());
        assertEquals("Viewed", result.get(0).getStatusLabel());
    }

    @Test
    void revokeMarksInvitationRevoked() {
        when(projectService.canUserAccessProject(7, inviter)).thenReturn(true);
        ViewInvitation invitation = activeInvitation();
        invitation.setId(5);
        when(viewInvitationRepository.findById(5)).thenReturn(Optional.of(invitation));

        service.revoke(5, 7, inviter);

        assertEquals(ViewInvitation.STATUS_REVOKED, invitation.getStatus());
        verify(viewInvitationRepository).save(invitation);
    }

    @Test
    void revokeRejectsMismatchedProject() {
        when(projectService.canUserAccessProject(9, inviter)).thenReturn(true);
        ViewInvitation invitation = activeInvitation();
        invitation.setId(5);
        when(viewInvitationRepository.findById(5)).thenReturn(Optional.of(invitation));

        assertThrows(IllegalArgumentException.class, () -> service.revoke(5, 9, inviter));
    }

    private ViewInvitation activeInvitation() {
        ViewInvitation invitation = new ViewInvitation();
        invitation.setEmail("reader@example.com");
        invitation.setToken("tok");
        invitation.setProject(project);
        invitation.setStatus(ViewInvitation.STATUS_ACTIVE);
        invitation.setCreatedAt(LocalDateTime.now());
        invitation.setExpiresAt(LocalDateTime.now().plusDays(30));
        return invitation;
    }
}
