package com.scripty.service;

import com.scripty.commandmodel.invitation.AcceptInvitationCommandModel;
import com.scripty.commandmodel.invitation.SendInvitationCommandModel;
import com.scripty.dto.Invitation;
import com.scripty.dto.User;
import com.scripty.viewmodel.invitation.AcceptInvitationViewModel;
import com.scripty.viewmodel.invitation.PendingInvitationViewModel;
import java.util.List;

public interface InvitationService {

    Invitation sendInvitation(SendInvitationCommandModel command, User invitedBy);

    List<PendingInvitationViewModel> getPendingInvitationsForProject(Integer projectId);

    AcceptInvitationViewModel getAcceptViewModel(String token);

    User acceptInvitation(AcceptInvitationCommandModel command);

    void revoke(Integer invitationId);
}
