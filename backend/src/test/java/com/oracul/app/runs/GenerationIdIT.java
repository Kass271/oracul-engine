package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/** startRun row #3: generationId = ORC-YYYY-MM-DD-HHmm (UTC), collisions get -2, -3, also across sessions. */
// @trace FR-10
@Import(GenerationIdIT.FixedClock.class)
@TestPropertySource(properties = "oracul.run.placeholder-stage-delay=PT0S")
class GenerationIdIT extends AbstractRunIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-02T18:42:31Z"), ZoneOffset.UTC);
        }
    }

    @Test
    void collidingMinutesGetNumericSuffixesAcrossSessions() throws Exception {
        String x = connectedSid();
        String y = connectedSid();
        var r1 = startOk(x, B);
        assertThat(r1.get("generationId")).isEqualTo("ORC-2026-10-02-1842");
        awaitTerminal(x, (String) r1.get("id"));
        var r2 = startOk(y, B);
        assertThat(r2.get("generationId")).isEqualTo("ORC-2026-10-02-1842-2");
        awaitTerminal(y, (String) r2.get("id"));
        var r3 = startOk(x, B);
        assertThat(r3.get("generationId")).isEqualTo("ORC-2026-10-02-1842-3");
    }
}
