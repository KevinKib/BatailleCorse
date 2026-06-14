package org.kevinkib.cardgames.bullshit.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class BullshitIdTest {

    @Test
    void givenUuidString_whenConstructed_thenRoundTrips() {
        UUID uuid = UUID.randomUUID();
        assertThat(new BullshitId(uuid.toString()).uuid(), is(uuid));
    }

    @Test
    void whenGenerated_thenHasUuid() {
        assertThat(BullshitId.generate().uuid(), notNullValue());
    }
}
