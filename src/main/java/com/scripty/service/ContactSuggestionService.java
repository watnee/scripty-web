package com.scripty.service;

import com.scripty.dto.User;
import com.scripty.viewmodel.contact.ContactSuggestionViewModel;
import java.util.List;

public interface ContactSuggestionService {

    /**
     * Contacts matching a typed name (or partial email) that the current user may
     * already see on the project: actors cast on it plus users with access.
     * Returns an empty list when the user cannot access the project.
     */
    List<ContactSuggestionViewModel> suggest(Integer projectId, User currentUser, String query);
}
