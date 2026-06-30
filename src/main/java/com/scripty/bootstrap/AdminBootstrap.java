package com.scripty.bootstrap;

import com.scripty.dto.User;
import com.scripty.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements CommandLineRunner {

    @Autowired
    private UserService userService;

    @Value("${ADMIN_USERNAME:}")
    private String username;

    @Value("${ADMIN_PASSWORD:}")
    private String password;

    @Value("${ADMIN_FIRST_NAME:Admin}")
    private String firstName;

    @Value("${ADMIN_LAST_NAME:User}")
    private String lastName;

    @Override
    public void run(String... args) {
        if (!userService.list().isEmpty()) {
            return;
        }

        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return;
        }

        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(password);
        admin.setEnabled(true);
        admin.setAdmin(true);
        admin.setFirstName(firstName);
        admin.setLastName(lastName);

        userService.create(admin);
    }
}
