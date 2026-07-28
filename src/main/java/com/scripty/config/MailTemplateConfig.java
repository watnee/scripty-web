package com.scripty.config;

import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Lets the template engine render the plain-text half of an email.
 *
 * <p>Boot configures one resolver, for HTML under {@code classpath:/templates/}.
 * A message worth sending carries both an HTML body and a plain-text
 * alternative, and the text body is a template like any other — it just cannot
 * be parsed as markup, so it needs a resolver in {@link TemplateMode#TEXT}.
 *
 * <p>Scoped to {@code email/*.txt} on purpose. Boot's resolver is happy to
 * claim any name handed to it and only discovers the file is missing when it
 * tries to read it, so a narrow pattern here — plus an order that puts this
 * one first — keeps the two from arguing over which template is whose.
 */
@Configuration
public class MailTemplateConfig {

    @Bean
    public ClassLoaderTemplateResolver mailTextTemplateResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        // The caller names the file outright, extension and all, so that the
        // resolvable pattern below can key off it.
        resolver.setSuffix("");
        resolver.setResolvablePatterns(Set.of("email/*.txt"));
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setOrder(0);
        return resolver;
    }
}
