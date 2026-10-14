package org.kevinkib.cardgames.config;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.sessionmanagement.presence.application.PresenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The e2e (test) profile shortens the forfeit grace so a forfeit spec does not wait a real minute. */
@SpringBootTest
@ActiveProfiles("test")
class ForfeitGraceTestProfileTest {

    @Autowired PresenceService presence;

    @Test
    void e2eProfileShortensTheGraceToFiveSeconds() {
        assertEquals(Duration.ofSeconds(5), presence.forfeitGrace());
    }
}
