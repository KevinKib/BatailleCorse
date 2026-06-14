package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseId;
import org.kevinkib.cardgames.bataillecorse.domain.PlayerId;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ForfeitReasonRegistryTest {

    private final ForfeitReasonRegistry registry = new ForfeitReasonRegistry();

    @Test
    void givenReasonRecorded_thenReasonsBySeatContainsItKeyedBySeatIndex() {
        BatailleCorseId gameId = BatailleCorseId.generate();
        registry.record(new Seat(gameId, new PlayerId(1)), ForfeitReason.DISCONNECTED);

        Map<Integer, ForfeitReason> reasons = registry.reasonsBySeat(gameId);

        assertThat(reasons.get(1), is(ForfeitReason.DISCONNECTED));
        assertThat(reasons.size(), is(1));
    }

    @Test
    void givenReasonForAnotherGame_thenNotReturned() {
        BatailleCorseId gameId = BatailleCorseId.generate();
        BatailleCorseId otherGame = BatailleCorseId.generate();
        registry.record(new Seat(otherGame, new PlayerId(0)), ForfeitReason.RESIGNED);

        assertThat(registry.reasonsBySeat(gameId).isEmpty(), is(true));
    }

    @Test
    void givenRemoveGame_thenItsReasonsCleared() {
        BatailleCorseId gameId = BatailleCorseId.generate();
        registry.record(new Seat(gameId, new PlayerId(0)), ForfeitReason.RESIGNED);

        registry.removeGame(gameId);

        assertThat(registry.reasonsBySeat(gameId).isEmpty(), is(true));
    }
}
