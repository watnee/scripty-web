package com.scripty.bootstrap;

import com.scripty.dto.User;
import com.scripty.service.UserService;
import javax.inject.Inject;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationListener<ContextRefreshedEvent> {

    @Inject
    UserService userService;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!userService.list().isEmpty()) {
            return;
        }

        String username = System.getenv("ADMIN_USERNAME");
        String password = System.getenv("ADMIN_PASSWORD");

        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return;
        }

        String firstName = System.getenv("ADMIN_FIRST_NAME");
        String lastName = System.getenv("ADMIN_LAST_NAME");

        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(password);
        admin.setEnabled(true);
        admin.setAdmin(true);
        admin.setFirstName(firstName != null && !firstName.isEmpty() ? firstName : "Admin");
        admin.setLastName(lastName != null && !lastName.isEmpty() ? lastName : "User");

        userService.create(admin);
    }
}
