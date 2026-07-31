package com.scripty.config;

import com.scripty.repository.UserRepository;
import com.scripty.security.ApiTokenAuthenticationFilter;
import com.scripty.security.CsrfAccessDeniedHandler;
import com.scripty.security.CsrfTokenEagerLoadingFilter;
import com.scripty.security.EmailResolvingUserDetailsManager;
import com.scripty.security.ForcedPasswordChangeFilter;
import com.scripty.security.HtmxLoginUrlAuthenticationEntryPoint;
import com.scripty.security.LoginSuccessHandler;
import com.scripty.security.LogoutIgnoringRequestCache;
import com.scripty.security.MetricsTokenAuthorizationManager;
import com.scripty.security.PasswordDiscardingUserCredentialRepository;
import com.scripty.service.ApiTokenService;
import java.util.Set;
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
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;

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
    public HtmxLoginUrlAuthenticationEntryPoint authenticationEntryPoint(
            PasskeySettings passkeySettings) {
        // The 401 challenge advertises passkey sign-in only when it exists.
        return new HtmxLoginUrlAuthenticationEntryPoint("/login", passkeySettings.isEnabled());
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
            PasskeySettings passkeySettings,
            ApiTokenService apiTokenService) throws Exception {
        applyWebAuthn(http, passkeySettings);
        http
            .requestCache(cache -> cache.requestCache(requestCache))
            // Native clients signed in with a passkey have no password for
            // Basic; their bearer tokens authenticate here instead.
            .addFilterBefore(
                    new ApiTokenAuthenticationFilter(apiTokenService, userDetailsManager),
                    BasicAuthenticationFilter.class)
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
                        // Static API link-relation docs pointed at by HAL curies.
                        "/docs/**",
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
                        "/forgot-password/**",
                        // The same recovery flow over the API. Deliberately not
                        // /api itself: an anonymous root would answer 200 where
                        // a native client needs the 401 Basic challenge to know
                        // it must ask for credentials. The recovery link rides
                        // on that challenge instead — see
                        // HtmxLoginUrlAuthenticationEntryPoint.
                        "/api/forgot-password",
                        "/api/forgot-password/**",
                        // The native passkey sign-in ceremony: like the browser's
                        // /webauthn/authenticate/options + /login/webauthn above,
                        // both halves run before a user is authenticated.
                        "/api/login/passkey/options",
                        "/api/login/passkey",
                        // iOS fetches this unauthenticated to validate the app's
                        // webcredentials associated domain for passkeys.
                        "/.well-known/apple-app-site-association")
                    .permitAll()
                // Your own account is yours: changing your password and managing
                // your own passkeys are not admin actions, so these sit ahead of
                // the admin block rather than inside it.
                .requestMatchers("/account/password", "/api/account/**")
                    .hasRole("USER")
                .requestMatchers(
                        "/config/**",
                        "/user/**",
                        "/api/user/**",
                        "/team/**",
                        "/api/team/**",
                        "/account/**")
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
            // so CSRF tokens add nothing for them. Cookie-authenticated calls
            // (HTMX) keep full CSRF protection: forgery rides on the cookie a
            // browser sends by itself, and a request that carries credentials of
            // its own is not that request.
            //
            // Deliberately not scoped to /api: the API's own hypermedia sends
            // native clients outside it. The screenplay editor's undo and redo
            // are advertised as /project/undo and /project/redo — the same
            // handlers the web UI posts to — and while this exemption was
            // /api-scoped every undo from the app was rejected as a forgery, so
            // taking back a deleted element answered "You don't have permission
            // to do that". Same shape as the export links, which live outside
            // /api and needed the bearer filter widened to match.
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                    request -> request.getHeader(HttpHeaders.AUTHORIZATION) != null,
                    // Password recovery has no session to carry a token and no
                    // credentials to send — the caller is signed out, which is
                    // the whole point of it. Nothing here acts on behalf of a
                    // signed-in user, so there is no authority to ride on.
                    new AntPathRequestMatcher("/api/forgot-password/**"),
                    new AntPathRequestMatcher("/api/forgot-password"),
                    // The native passkey sign-in is anonymous for the same
                    // reason; the ceremony's own challenge is its protection.
                    new AntPathRequestMatcher("/api/login/passkey/options"),
                    new AntPathRequestMatcher("/api/login/passkey")))
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
     *
     * <p>The relying party itself (rp id, origin, credential storage) lives in
     * the {@link #relyingPartyOperations} bean, which this DSL picks up — the
     * same instance PasskeyRestController runs the native ceremonies through,
     * so both flows verify against one relying party.
     */
    private static void applyWebAuthn(HttpSecurity http, PasskeySettings settings)
            throws Exception {
        if (!settings.isEnabled()) {
            return;
        }
        http.webAuthn(webAuthn -> webAuthn
                // Scripty ships its own registration page (PasskeyController); the
                // framework default also NPEs when CSRF is disabled (dev profile).
                .disableDefaultRegistrationPage(true));
    }

    /**
     * The one relying party both passkey flows share: Spring Security's
     * browser filters and the native API ceremonies. When passkeys are
     * disabled the bean still exists (its consumers inject it), but every
     * caller gates on {@link PasskeySettings#isEnabled} first, so the
     * placeholder relying party is never asked to verify anything.
     */
    @Bean
    public WebAuthnRelyingPartyOperations relyingPartyOperations(PasskeySettings settings,
            JdbcPublicKeyCredentialUserEntityRepository userEntityRepository,
            UserCredentialRepository userCredentialRepository) {
        boolean enabled = settings.isEnabled();
        PublicKeyCredentialRpEntity rpEntity = PublicKeyCredentialRpEntity.builder()
                .id(enabled ? settings.getRpId() : "passkeys-disabled.invalid")
                .name("Scripty")
                .build();
        Set<String> origins = enabled ? Set.of(settings.getOrigin()) : Set.of();
        return new Webauthn4JRelyingPartyOperations(
                userEntityRepository, userCredentialRepository, rpEntity, origins);
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
