package org.kevinkib.cardgames.config;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.sessionmanagement.presence.application.PresenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Without the test profile (production), the forfeit grace stays at 60 seconds. */
@SpringBootTest
class ForfeitGraceConfigTest {

    @Autowired PresenceService presence;

    @Test
    void productionKeepsTheSixtySecondGrace() {
        assertEquals(Duration.ofSeconds(60), presence.forfeitGrace());
    }
}
