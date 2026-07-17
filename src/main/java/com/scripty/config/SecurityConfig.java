package com.scripty.config;

import com.scripty.repository.UserRepository;
import com.scripty.security.CsrfAccessDeniedHandler;
import com.scripty.security.CsrfTokenEagerLoadingFilter;
import com.scripty.security.EmailResolvingUserDetailsManager;
import com.scripty.security.ForcedPasswordChangeFilter;
import com.scripty.security.HtmxLoginUrlAuthenticationEntryPoint;
import com.scripty.security.LoginSuccessHandler;
import com.scripty.security.LogoutIgnoringRequestCache;
import com.scripty.security.MetricsTokenAuthorizationManager;
import com.scripty.security.PasswordDiscardingUserCredentialRepository;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

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
                    + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                    + "img-src 'self' data: blob:; "
                    + "font-src 'self' data: https://fonts.gstatic.com; "
                    + "connect-src 'self'; "
                    + "object-src 'none'; "
                    + "base-uri 'self'; "
                    + "form-action 'self'; "
                    + "frame-ancestors 'none'";

    private static final String PERMISSIONS_POLICY =
            "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

    @Bean
    public RequestCache requestCache() {
        return new LogoutIgnoringRequestCache();
    }

    @Bean
    public LoginSuccessHandler loginSuccessHandler(RequestCache requestCache) {
        return new LoginSuccessHandler(requestCache);
    }

    @Bean
    public HtmxLoginUrlAuthenticationEntryPoint authenticationEntryPoint() {
        return new HtmxLoginUrlAuthenticationEntryPoint("/login");
    }

    @Bean
    public MetricsTokenAuthorizationManager metricsTokenAuthorizationManager(
            @Value("${METRICS_TOKEN:}") String metricsToken) {
        return new MetricsTokenAuthorizationManager(metricsToken);
    }

    /** Remember-me tokens stored in the persistent_logins table (V36). */
    @Bean
    public PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        return repository;
    }

    @Bean
    @Profile("!dev")
    public SecurityFilterChain filterChain(HttpSecurity http,
            RequestCache requestCache,
            LoginSuccessHandler loginSuccessHandler,
            HtmxLoginUrlAuthenticationEntryPoint authenticationEntryPoint,
            MetricsTokenAuthorizationManager metricsTokenAuthorizationManager,
            UserRepository userRepository,
            UserDetailsManager userDetailsManager,
            PersistentTokenRepository persistentTokenRepository,
            PasskeySettings passkeySettings) throws Exception {
        applyWebAuthn(http, passkeySettings);
        http
            .requestCache(cache -> cache.requestCache(requestCache))
            // Accounts still on seeded/generated deploy credentials are locked to
            // the change-password page until they choose a real password.
            .addFilterAfter(new ForcedPasswordChangeFilter(userRepository),
                    UsernamePasswordAuthenticationFilter.class)
            // Resolve the deferred CSRF token before rendering: reading it for the
            // first time mid-render (nav fragment) fails once the response buffer
            // has committed and the token's session can't be created.
            .addFilterAfter(new CsrfTokenEagerLoadingFilter(), CsrfFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/prometheus")
                    .access(metricsTokenAuthorizationManager)
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers(
                        "/",
                        "/health",
                        "/error",
                        "/favicon.ico",
                        "/css/**",
                        "/js/**",
                        "/dictionaries/**",
                        "/fonts/**",
                        "/login",
                        // Pre-login passkey endpoints: the sign-in script, the
                        // challenge endpoint, and the assertion endpoint all run
                        // before a user is authenticated.
                        "/login/webauthn.js",
                        "/login/webauthn",
                        "/webauthn/authenticate/options",
                        "/manifest.json",
                        "/sw.js",
                        "/offline.html",
                        "/offline-project.html",
                        "/icons/**",
                        "/help",
                        "/shortcuts",
                        "/invitation/accept",
                        // Tokenized read-only screenplay links from view invites.
                        "/view",
                        "/forgot-password",
                        "/forgot-password/**")
                    .permitAll()
                .requestMatchers("/account/password")
                    .hasRole("USER")
                .requestMatchers(
                        "/config/**",
                        "/user/**",
                        "/api/user/**",
                        "/team/**",
                        "/api/team/**",
                        "/api/account/**",
                        "/account/**",
                        // Must precede the /project/** USER rule below.
                        "/project/trash",
                        "/project/restore",
                        "/project/restoreAll",
                        "/project/purge")
                    .hasRole("ADMIN")
                .requestMatchers(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .hasRole("DEVELOPER")
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
                .successHandler(loginSuccessHandler)
                .failureUrl("/login?login_error=1")
                .permitAll()
            )
            .httpBasic(org.springframework.security.config.Customizer.withDefaults())
            // Native API clients (e.g. the SwiftUI app) authenticate each request
            // with an Authorization header, which browsers cannot attach cross-site,
            // so CSRF tokens add nothing for them. Cookie-authenticated /api calls
            // (HTMX) keep full CSRF protection.
            .csrf(csrf -> csrf.ignoringRequestMatchers(new AndRequestMatcher(
                    new AntPathRequestMatcher("/api/**"),
                    request -> request.getHeader(HttpHeaders.AUTHORIZATION) != null)))
            // Keep users signed in across server restarts and session expiry:
            // a DB-backed remember-me token silently re-authenticates for 30 days
            // (sliding — each auto-login refreshes the token). alwaysRemember means
            // no checkbox on the login form; every password login gets the cookie.
            .rememberMe(remember -> remember
                .userDetailsService(userDetailsManager)
                .tokenRepository(persistentTokenRepository)
                .tokenValiditySeconds(30 * 24 * 60 * 60)
                .alwaysRemember(true)
            )
            .logout(logout -> logout
                // Accept GET too: cached pages / bookmarks still hit GET /logout and
                // otherwise fall through to a Spring Whitelabel 404.
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(new CsrfAccessDeniedHandler())
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
    public SecurityFilterChain devFilterChain(HttpSecurity http,
            RequestCache requestCache,
            LoginSuccessHandler loginSuccessHandler,
            PasskeySettings passkeySettings) throws Exception {
        applyWebAuthn(http, passkeySettings);
        http
            .requestCache(cache -> cache.requestCache(requestCache))
            .addFilterBefore(new DevAutoLoginFilter(), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login").permitAll()
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(loginSuccessHandler)
                .failureUrl("/login?login_error=1")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
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

    /**
     * Passkey (WebAuthn) sign-in, bound to the app.base-url domain. Skipped when
     * no base URL is configured — password login keeps working either way.
     */
    private static void applyWebAuthn(HttpSecurity http, PasskeySettings settings)
            throws Exception {
        if (!settings.isEnabled()) {
            return;
        }
        http.webAuthn(webAuthn -> webAuthn
                .rpName("Scripty")
                .rpId(settings.getRpId())
                .allowedOrigins(settings.getOrigin())
                // Scripty ships its own registration page (PasskeyController); the
                // framework default also NPEs when CSRF is disabled (dev profile).
                .disableDefaultRegistrationPage(true));
    }

    /** Persist passkey user handles across restarts (table: user_entities, V33). */
    @Bean
    public JdbcPublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository(
            JdbcOperations jdbcOperations) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbcOperations);
    }

    /**
     * Persist registered passkeys across restarts (table: user_credentials, V33).
     * Wrapped so that registering a passkey on an account still flagged
     * password_change_required (fresh-deploy bootstrap credentials) automatically
     * replaces the password with a random value nobody knows.
     */
    @Bean
    public UserCredentialRepository userCredentialRepository(JdbcOperations jdbcOperations,
            JdbcPublicKeyCredentialUserEntityRepository userEntityRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        return new PasswordDiscardingUserCredentialRepository(
                new JdbcUserCredentialRepository(jdbcOperations),
                userEntityRepository, userRepository, passwordEncoder);
    }

    @Bean
    public UserDetailsManager userDetailsManager(DataSource dataSource,
            UserRepository userRepository) {
        JdbcUserDetailsManager manager =
                new EmailResolvingUserDetailsManager(dataSource, userRepository);
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
