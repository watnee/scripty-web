package com.scripty.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/favicon.ico", "/css/**", "/js/**", "/fonts/**", "/login", "/manifest.json", "/sw.js", "/offline.html", "/icons/**").permitAll()
                .requestMatchers("/api/account/**", "/account/**").hasRole("ADMIN")
                // Writers may only edit the screenplay (scenes and blocks); all other
                // create/edit/delete actions require a non-writer account.
                .requestMatchers("/project/create", "/project/edit", "/project/delete",
                                 "/project/version/create", "/project/version/restore", "/project/version/delete",
                                 "/actor/create", "/actor/edit", "/actor/delete",
                                 "/character/create", "/character/edit", "/character/delete",
                                 "/team/create", "/team/edit", "/team/delete")
                    .access(new WebExpressionAuthorizationManager("hasRole('USER') and !hasRole('WRITER')"))
                .requestMatchers("/project/**", "/actor/**", "/scene/**", "/block/**", "/character/**", "/team/**").hasRole("USER")
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", false)
                .failureUrl("/login?login_error=1")
                .permitAll()
            )
            .logout(logout -> logout
                .permitAll()
            )
            .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    public UserDetailsManager userDetailsManager(DataSource dataSource) {
        JdbcUserDetailsManager manager = new JdbcUserDetailsManager(dataSource);
        manager.setUsersByUsernameQuery(
            "SELECT username, `password`, enabled FROM `user` WHERE username = ?");
        manager.setAuthoritiesByUsernameQuery(
            "SELECT username, authority FROM authority WHERE username = ?");
        return manager;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
