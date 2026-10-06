package com.kestrel.commerce.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the {@link Clock} used for every timestamp in the application.
 *
 * <p>Never call {@code Instant.now()} directly in business code: inject the clock instead, so tests can control time
 * (for example with {@code Clock.fixed(...)}, see {@code OrderServiceTest}).
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
