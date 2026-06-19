package org.kevinkib.cardgames.bullshit.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.bullshit.domain.BullshitBuilder.aBullshit;
import static org.kevinkib.cardgames.bullshit.domain.BullshitFixtures.playerWithRanks;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.ACE;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.KING;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.QUEEN;

class BullshitLifecycleBroadcasterTest {

    static final class RecordingMessaging extends GameMessagingService {
        final List<PlayerId> seats = new ArrayList<>();

        RecordingMessaging() {
            super(null, null);
        }

        @Override
        public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
            seats.add(seat);
        }
    }

    @Test
    void givenBullshit_whenSupports_thenTrue() {
        Bullshit game = aBullshit().withPlayers(playerWithRanks(0, ACE), playerWithRanks(1, KING)).build();
        BullshitLifecycleBroadcaster broadcaster =
                new BullshitLifecycleBroadcaster(new BullshitStateBroadcaster(new RecordingMessaging()));

        assertThat(broadcaster.supports(game), is(true));
    }

    @Test
    void givenForfeit_whenForfeited_thenRemainingSeatsEachReceiveState() {
        Bullshit game = aBullshit()
                .withPlayers(playerWithRanks(0, ACE), playerWithRanks(1, KING), playerWithRanks(2, QUEEN))
                .build();
        game.forfeit(new PlayerId(1));
        RecordingMessaging messaging = new RecordingMessaging();
        BullshitLifecycleBroadcaster broadcaster =
                new BullshitLifecycleBroadcaster(new BullshitStateBroadcaster(messaging));

        broadcaster.forfeited(game, new PlayerId(1), ForfeitReason.RESIGNED);

        List<Integer> seatIds = messaging.seats.stream().map(PlayerId::id).toList();
        assertThat(seatIds.size(), is(2));
        assertThat(seatIds, containsInAnyOrder(0, 2));
    }
}
