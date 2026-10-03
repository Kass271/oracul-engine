package com.oracul.app.runs;

import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Registers the shared {@link MutableClock} as the primary Clock bean of the deadline tests (FR-32). */
@TestConfiguration(proxyBeanMethods = false)
public class MutableClockConfig {

    public static final MutableClock CLOCK = new MutableClock();

    @Bean
    @Primary
    Clock mutableClock() {
        return CLOCK;
    }
}
