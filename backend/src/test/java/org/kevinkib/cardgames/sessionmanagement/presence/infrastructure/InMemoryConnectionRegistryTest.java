package org.kevinkib.cardgames.sessionmanagement.presence.infrastructure;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ConnectionRegistry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class InMemoryConnectionRegistryTest {

    private final GameId gameId = GameId.generate();

    @Test
    void givenBoundSession_whenSeatOf_thenReturnsSeat() {
        ConnectionRegistry registry = new InMemoryConnectionRegistry();
        var seat = new Seat(gameId, new PlayerId(0));
        registry.bind("sess-1", seat);

        assertThat(registry.seatOf("sess-1").orElseThrow(), is(seat));
    }

    @Test
    void givenBoundSession_whenUnbind_thenReturnsSeatAndForgets() {
        ConnectionRegistry registry = new InMemoryConnectionRegistry();
        var seat = new Seat(gameId, new PlayerId(1));
        registry.bind("sess-2", seat);

        assertThat(registry.unbind("sess-2").orElseThrow(), is(seat));
        assertThat(registry.seatOf("sess-2").isEmpty(), is(true));
    }

    @Test
    void whenUnbindUnknownSession_thenEmpty() {
        ConnectionRegistry registry = new InMemoryConnectionRegistry();
        assertThat(registry.unbind("nope").isEmpty(), is(true));
    }

    @Test
    void givenSeatsAcrossTwoGames_whenSeatsFor_thenOnlyThatGamesPlayerIds() {
        InMemoryConnectionRegistry registry = new InMemoryConnectionRegistry();
        GameId g1 = GameId.generate();
        GameId g2 = GameId.generate();
        registry.bind("c0", new Seat(g1, new PlayerId(0)));
        registry.bind("c1", new Seat(g1, new PlayerId(1)));
        registry.bind("c2", new Seat(g2, new PlayerId(0)));

        assertThat(registry.seatsFor(g1), containsInAnyOrder(new PlayerId(0), new PlayerId(1)));
    }

    @Test
    void givenSessionsForGame_whenRemoveGame_thenAllForgotten() {
        ConnectionRegistry registry = new InMemoryConnectionRegistry();
        registry.bind("a", new Seat(gameId, new PlayerId(0)));
        registry.bind("b", new Seat(gameId, new PlayerId(1)));
        var otherGame = GameId.generate();
        registry.bind("c", new Seat(otherGame, new PlayerId(0)));

        registry.removeGame(gameId);

        assertThat(registry.seatOf("a").isEmpty(), is(true));
        assertThat(registry.seatOf("b").isEmpty(), is(true));
        assertThat(registry.seatOf("c").isPresent(), is(true));
    }
}
