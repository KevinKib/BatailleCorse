package org.kevinkib.cardgames.game;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class GameIdTest {

    @Test
    void givenUuidString_whenConstructed_thenRoundTrips() {
        UUID uuid = UUID.randomUUID();
        assertThat(new GameId(uuid.toString()).uuid(), is(uuid));
    }

    @Test
    void whenGenerated_thenHasUuid() {
        assertThat(GameId.generate().uuid(), notNullValue());
    }
}
