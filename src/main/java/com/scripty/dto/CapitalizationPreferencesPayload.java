package com.scripty.dto;

/**
 * The POST body of the capitalization preferences endpoint. This exists so the
 * HAL-FORMS affordance can name the four toggles: a {@code Map} body leaves the
 * template with a single anonymous property, which tells a client nothing.
 *
 * <p>The fields are boxed on purpose — an absent one stays null and keeps its
 * stored value, so the toggle can post only the type the user clicked.
 */
public class CapitalizationPreferencesPayload {

    private Boolean scene;
    private Boolean character;
    private Boolean transition;
    private Boolean shot;

    public Boolean getScene() {
        return scene;
    }

    public void setScene(Boolean scene) {
        this.scene = scene;
    }

    public Boolean getCharacter() {
        return character;
    }

    public void setCharacter(Boolean character) {
        this.character = character;
    }

    public Boolean getTransition() {
        return transition;
    }

    public void setTransition(Boolean transition) {
        this.transition = transition;
    }

    public Boolean getShot() {
        return shot;
    }

    public void setShot(Boolean shot) {
        this.shot = shot;
    }
}
