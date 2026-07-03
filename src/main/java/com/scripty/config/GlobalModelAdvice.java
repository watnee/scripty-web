package com.scripty.config;

import com.scripty.dto.Project;
import com.scripty.dto.User;
import com.scripty.service.ProjectService;
import com.scripty.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

@ControllerAdvice
public class GlobalModelAdvice {

    @Autowired
    private UserService userService;

    @Autowired
    private ProjectService projectService;

    @ModelAttribute
    public void addGlobalAttributes(Model model, Principal principal) {
        if (principal != null) {
            User currentUser = userService.readByUsername(principal.getName());
            if (currentUser != null && currentUser.getDefaultProjectId() != null) {
                model.addAttribute("globalDefaultProjectId", currentUser.getDefaultProjectId());
                Project defaultProject = projectService.read(currentUser.getDefaultProjectId());
                if (defaultProject != null) {
                    model.addAttribute("globalDefaultProjectTitle", defaultProject.getTitle());
                }
            }
        }
    }
}
