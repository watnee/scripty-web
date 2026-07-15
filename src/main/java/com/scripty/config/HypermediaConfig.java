package com.scripty.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType;

/**
 * Serves both HAL and HAL-FORMS. HAL stays the default representation, so
 * existing clients (and the SwiftUI app, which sends {@code Accept:
 * application/json}) are unaffected. Clients that ask for
 * {@code application/prs.hal-forms+json} additionally get {@code _templates}
 * describing each affordance's HTTP method and request-body schema, so a native
 * client can drive updates without hard-coding verbs or field names.
 */
@Configuration
@EnableHypermediaSupport(type = {HypermediaType.HAL, HypermediaType.HAL_FORMS})
public class HypermediaConfig {
}
