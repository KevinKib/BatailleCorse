package org.kevinkib.cardgames.sessionmanagement.presence.infrastructure;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.SeatClaim;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ConnectionRegistry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class InMemoryConnectionRegistryTest {

    private final GameId gameId = GameId.generate();

    @Test
    void givenBoundSession_whenSeatOf_thenReturnsSeat() {
        ConnectionRegistry registry = new InMemoryConnectionRegistry();
        var claim = new SeatClaim(gameId, "token-0");
        registry.bind("sess-1", claim);

        assertThat(registry.claimOf("sess-1").orElseThrow(), is(claim));
    }

    @Test
    void givenBoundSession_whenUnbind_thenReturnsSeatAndForgets() {
        ConnectionRegistry registry = new InMemoryConnectionRegistry();
        var claim = new SeatClaim(gameId, "token-1");
        registry.bind("sess-2", claim);

        assertThat(registry.unbind("sess-2").orElseThrow(), is(claim));
        assertThat(registry.claimOf("sess-2").isEmpty(), is(true));
    }

    @Test
    void whenUnbindUnknownSession_thenEmpty() {
        ConnectionRegistry registry = new InMemoryConnectionRegistry();
        assertThat(registry.unbind("nope").isEmpty(), is(true));
    }

    @Test
    void givenSessionsForGame_whenRemoveGame_thenAllForgotten() {
        ConnectionRegistry registry = new InMemoryConnectionRegistry();
        registry.bind("a", new SeatClaim(gameId, "token-0"));
        registry.bind("b", new SeatClaim(gameId, "token-1"));
        var otherGame = GameId.generate();
        registry.bind("c", new SeatClaim(otherGame, "token-0"));

        registry.removeGame(gameId);

        assertThat(registry.claimOf("a").isEmpty(), is(true));
        assertThat(registry.claimOf("b").isEmpty(), is(true));
        assertThat(registry.claimOf("c").isPresent(), is(true));
    }
}
