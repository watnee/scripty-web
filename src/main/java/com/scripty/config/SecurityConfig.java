package com.scripty.config;

import com.scripty.security.HtmxLoginUrlAuthenticationEntryPoint;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
@Lazy(false)
public class SecurityConfig {

    /**
     * CSP allows inline scripts used by Thymeleaf templates and HTMX, while blocking
     * unexpected script sources. Combined with th:text escaping and PlainTextSanitizer.
     */
    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; "
                    + "script-src 'self' 'unsafe-inline'; "
                    + "style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data: blob:; "
                    + "font-src 'self' data:; "
                    + "connect-src 'self'; "
                    + "object-src 'none'; "
                    + "base-uri 'self'; "
                    + "form-action 'self'; "
                    + "frame-ancestors 'none'";

    private static final String PERMISSIONS_POLICY =
            "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

    @Bean
    @Profile("!dev")
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/",
                        "/health",
                        "/favicon.ico",
                        "/css/**",
                        "/js/**",
                        "/dictionaries/**",
                        "/fonts/**",
                        "/login",
                        "/perform-login",
                        "/manifest.json",
                        "/sw.js",
                        "/offline.html",
                        "/offline-project.html",
                        "/icons/**",
                        "/help",
                        "/shortcuts",
                        "/invitation/accept")
                    .permitAll()
                .requestMatchers(
                        "/user/**",
                        "/api/user/**",
                        "/team/**",
                        "/api/team/**",
                        "/api/account/**",
                        "/account/**")
                    .hasRole("ADMIN")
                .requestMatchers(
                        "/project/**",
                        "/actor/**",
                        "/block/**",
                        "/character/**",
                        "/invitation/**",
                        "/audition/**",
                        "/api/**")
                    .hasRole("USER")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", false)
                .failureUrl("/login?login_error=1")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HtmxLoginUrlAuthenticationEntryPoint("/login"))
            )
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                .referrerPolicy(referrer -> referrer.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31536000))
                .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY))
            );

        return http.build();
    }

    @Bean
    @Profile("dev")
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        http
            .addFilterBefore(new DevAutoLoginFilter(), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/perform-login").permitAll()
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", false)
                .failureUrl("/login?login_error=1")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                .referrerPolicy(referrer -> referrer.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .frameOptions(frame -> frame.deny())
                .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY))
            )
            // Dev keeps CSRF off: DevTools restarts and auto-login make token sync brittle locally.
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
