package com.scripty.service;

import com.scripty.commandmodel.invitation.SendViewInvitationCommandModel;
import com.scripty.dto.User;
import com.scripty.dto.ViewInvitation;
import com.scripty.viewmodel.invitation.ScreenplayViewViewModel;
import com.scripty.viewmodel.invitation.ViewInvitationViewModel;
import java.util.List;

public interface ViewInvitationService {

    ViewInvitation sendInvitation(SendViewInvitationCommandModel command, User invitedBy);

    List<ViewInvitationViewModel> getActiveInvitationsForProject(Integer projectId, User currentUser);

    ScreenplayViewViewModel getScreenplayForToken(String token);

    void revoke(Integer invitationId, Integer projectId, User currentUser);
}
