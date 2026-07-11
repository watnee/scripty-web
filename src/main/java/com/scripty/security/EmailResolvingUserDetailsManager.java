package com.scripty.security;

import com.scripty.dto.User;
import com.scripty.repository.UserRepository;
import javax.sql.DataSource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.JdbcUserDetailsManager;

/**
 * Lets people sign in with their email address. The login input is resolved to
 * the account's username before the standard JDBC lookup, so the authenticated
 * principal stays the username — passkey user handles, the authority table, and
 * ForcedPasswordChangeFilter all key on it. Inputs without an email match (e.g.
 * the bootstrap admin account, which has no email) fall through unchanged.
 */
public class EmailResolvingUserDetailsManager extends JdbcUserDetailsManager {

    private final UserRepository userRepository;

    public EmailResolvingUserDetailsManager(DataSource dataSource, UserRepository userRepository) {
        super(dataSource);
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        return super.loadUserByUsername(resolveToUsername(userRepository, login));
    }

    static String resolveToUsername(UserRepository userRepository, String login) {
        if (login == null || !login.contains("@")) {
            return login;
        }
        String email = login.trim();
        return userRepository.findByEmailIgnoreCase(email)
                .map(User::getUsername)
                .orElse(email);
    }
}
