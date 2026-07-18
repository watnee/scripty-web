package com.scripty.viewmodel.contact;

import org.springframework.hateoas.server.core.Relation;

/**
 * The explicit collection relation keeps the HAL embed key stable: without it
 * the name is derived from the class ({@code contactSuggestionViewModelList}),
 * so renaming this type would silently break clients reading {@code _embedded}.
 */
@Relation(collectionRelation = "contactSuggestions")
public class ContactSuggestionViewModel {

    private String name;
    private String email;
    private String sourceLabel;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }
}
