package org.kevinkib.cardgames.sessionmanagement.presence.infrastructure;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class InMemoryForfeitLogTest {

    private final ForfeitLog registry = new InMemoryForfeitLog();

    @Test
    void givenReasonRecorded_thenReasonsBySeatContainsItKeyedBySeatIndex() {
        GameId gameId = GameId.generate();
        registry.record(new Seat(gameId, new PlayerId(1)), ForfeitReason.DISCONNECTED);

        Map<Integer, ForfeitReason> reasons = registry.reasonsBySeat(gameId);

        assertThat(reasons.get(1), is(ForfeitReason.DISCONNECTED));
        assertThat(reasons.size(), is(1));
    }

    @Test
    void givenReasonForAnotherGame_thenNotReturned() {
        GameId gameId = GameId.generate();
        GameId otherGame = GameId.generate();
        registry.record(new Seat(otherGame, new PlayerId(0)), ForfeitReason.RESIGNED);

        assertThat(registry.reasonsBySeat(gameId).isEmpty(), is(true));
    }

    @Test
    void givenRemoveGame_thenItsReasonsCleared() {
        GameId gameId = GameId.generate();
        registry.record(new Seat(gameId, new PlayerId(0)), ForfeitReason.RESIGNED);

        registry.removeGame(gameId);

        assertThat(registry.reasonsBySeat(gameId).isEmpty(), is(true));
    }
}
