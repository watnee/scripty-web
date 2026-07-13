package com.scripty.viewmodel.invitation;

import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;

/** State for the public tokenized screenplay reader page. */
public class ScreenplayViewViewModel {

    private boolean valid;
    private String errorMessage;
    private ProjectProfileViewModel screenplay;

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public ProjectProfileViewModel getScreenplay() {
        return screenplay;
    }

    public void setScreenplay(ProjectProfileViewModel screenplay) {
        this.screenplay = screenplay;
    }
}
