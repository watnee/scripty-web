package com.scripty.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.mediatype.hal.DefaultCurieProvider;

/**
 * Hypermedia rendering for the {@code /api} surface.
 *
 * <p>Two things beyond Spring Boot's HAL default:
 * <ul>
 *   <li><b>HAL-FORMS</b> is enabled alongside HAL, so a client that sends
 *       {@code Accept: application/prs.hal-forms+json} gets {@code _templates}
 *       describing each mutation's HTTP method and expected fields — the
 *       affordances the assemblers attach to their self links.</li>
 *   <li>A <b>curie provider</b> namespaces the API's custom link relations
 *       ({@code update}, {@code createBelow}, {@code syncStatus}, …) as
 *       {@code scripty:*} and advertises where they are documented, so the
 *       vocabulary is discoverable rather than opaque. IANA rels ({@code self},
 *       {@code next}, …) are left untouched.</li>
 * </ul>
 *
 * <p>Declaring {@link EnableHypermediaSupport} here is what registers HAL-FORMS;
 * Spring Boot's auto-configuration (HAL only) backs off once this is present.
 */
@Configuration
@EnableHypermediaSupport(type = { HypermediaType.HAL, HypermediaType.HAL_FORMS })
public class HypermediaConfig {

    /** Where the {@code scripty:} rels are documented, one anchor per relation. */
    static final String CURIE_TEMPLATE = "/docs/api-rels.html#{rel}";

    @Bean
    public CurieProvider scriptyCurieProvider() {
        return new DefaultCurieProvider("scripty", UriTemplate.of(CURIE_TEMPLATE));
    }
}
