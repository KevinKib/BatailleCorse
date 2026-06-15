package org.kevinkib.cardgames.bullshit.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.presentation.dto.BullshitDto;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.bullshit.domain.BullshitBuilder.aBullshit;
import static org.kevinkib.cardgames.bullshit.domain.BullshitFixtures.playerWithRanks;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.ACE;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.KING;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.QUEEN;

class BullshitStateBroadcasterTest {

    static final class RecordingMessaging extends GameMessagingService {
        final List<PlayerId> seats = new ArrayList<>();
        final List<Object> payloads = new ArrayList<>();

        RecordingMessaging() {
            super(null);
        }

        @Override
        public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
            seats.add(seat);
            payloads.add(payload);
        }
    }

    @Test
    void givenTwoPlayers_whenBroadcast_thenEachSeatReceivesItsOwnHand() {
        Bullshit game = aBullshit()
                .withPlayers(playerWithRanks(0, ACE), playerWithRanks(1, KING, QUEEN))
                .build();
        RecordingMessaging messaging = new RecordingMessaging();
        BullshitStateBroadcaster broadcaster = new BullshitStateBroadcaster(messaging);

        broadcaster.broadcast(game, "DISCARD", new EmptyEventData(), "msg");

        assertThat(messaging.seats.size(), is(2));
        BullshitDto state0 = (BullshitDto) ((Response) messaging.payloads.get(0)).getState();
        BullshitDto state1 = (BullshitDto) ((Response) messaging.payloads.get(1)).getState();
        assertThat(state0.myHand().size(), is(1));
        assertThat(state1.myHand().size(), is(2));
    }
}
