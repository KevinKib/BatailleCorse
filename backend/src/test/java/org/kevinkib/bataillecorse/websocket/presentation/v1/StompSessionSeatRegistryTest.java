package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class StompSessionSeatRegistryTest {

    private final BatailleCorseId gameId = BatailleCorseId.generate();

    @Test
    void givenBoundSession_whenSeatOf_thenReturnsSeat() {
        var registry = new StompSessionSeatRegistry();
        var seat = new Seat(gameId, new PlayerId(0));
        registry.bind("sess-1", seat);

        assertThat(registry.seatOf("sess-1").orElseThrow(), is(seat));
    }

    @Test
    void givenBoundSession_whenUnbind_thenReturnsSeatAndForgets() {
        var registry = new StompSessionSeatRegistry();
        var seat = new Seat(gameId, new PlayerId(1));
        registry.bind("sess-2", seat);

        assertThat(registry.unbind("sess-2").orElseThrow(), is(seat));
        assertThat(registry.seatOf("sess-2").isEmpty(), is(true));
    }

    @Test
    void whenUnbindUnknownSession_thenEmpty() {
        var registry = new StompSessionSeatRegistry();
        assertThat(registry.unbind("nope").isEmpty(), is(true));
    }

    @Test
    void givenSessionsForGame_whenRemoveGame_thenAllForgotten() {
        var registry = new StompSessionSeatRegistry();
        registry.bind("a", new Seat(gameId, new PlayerId(0)));
        registry.bind("b", new Seat(gameId, new PlayerId(1)));
        var otherGame = BatailleCorseId.generate();
        registry.bind("c", new Seat(otherGame, new PlayerId(0)));

        registry.removeGame(gameId);

        assertThat(registry.seatOf("a").isEmpty(), is(true));
        assertThat(registry.seatOf("b").isEmpty(), is(true));
        assertThat(registry.seatOf("c").isPresent(), is(true));
    }
}
