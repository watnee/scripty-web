package com.scripty.observability;

import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the Micrometer annotation aspects. Spring Boot does not auto-configure
 * these, so without them {@code @Timed} and {@code @Counted} are silently inert —
 * the annotation compiles, the meter never appears, and the gap only shows up when
 * a dashboard panel stays empty.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public CountedAspect countedAspect(MeterRegistry registry) {
        return new CountedAspect(registry);
    }
}
