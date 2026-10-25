package org.kevinkib.cardgames.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

/** The real application context counts what happens in the session service and keeps the actuator off the app port. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UsageStatisticsWiringIT {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private MeterRegistry registry;

    @Test
    void creatingARoomIncrementsTheGameCreatedCounterOfItsGame() {
        double before = registry.counter("cardgames.games.created", "game", "kobo").count();

        sessionService.createRoom("kobo", "Alice");

        assertThat(registry.counter("cardgames.games.created", "game", "kobo").count(), is(before + 1));
    }

    @Test
    void theOpenRoomsGaugeCountsTheRoomsInMemory() {
        double before = registry.get("cardgames.rooms.open").gauge().value();

        sessionService.createRoom("kobo", "Alice");

        assertThat(registry.get("cardgames.rooms.open").gauge().value(), is(before + 1));
    }

    @Test
    void theConnectedPlayersGaugeIsRegistered() {
        assertThat(registry.get("cardgames.players.connected").gauge().value() >= 0, is(true));
    }

    @Test
    void theActuatorLivesOnASeparatePortAndExposesOnlyMetricsAndHealth() throws IOException {
        Properties properties = new Properties();
        try (var in = new ClassPathResource("application.properties").getInputStream()) {
            properties.load(in);
        }

        assertThat(properties.getProperty("management.server.port"), startsWith("${MANAGEMENT_PORT:8081"));
        assertThat(properties.getProperty("management.endpoints.web.exposure.include"), is("health,metrics"));
    }
}
