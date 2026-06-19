package org.kevinkib.cardgames.bataillecorse.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.kevinkib.cardgames.presentation.api.Response;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class BatailleCorseLifecycleBroadcasterTest {

    /** Records the last broadcast instead of touching a real broker. */
    private static final class RecordingMessaging extends GameMessagingService {
        Response last;
        RecordingMessaging() { super(null, null); }
        @Override public void sendToGame(String gameId, Object payload) { last = (Response) payload; }
    }

    private final RecordingMessaging messaging = new RecordingMessaging();
    private final ForfeitLog reasons = new InMemoryForfeitLog();
    private final BatailleCorseLifecycleBroadcaster broadcaster =
            new BatailleCorseLifecycleBroadcaster(messaging, reasons);

    @Test
    void givenForfeit_whenBroadcast_thenSendsForfeitResponseWithBatailleCorseState() {
        BatailleCorse game = new BatailleCorse(GameId.generate(), 2);
        Seat seat = new Seat(game.getId(), new PlayerId(0));
        reasons.record(seat, ForfeitReason.RESIGNED);

        broadcaster.forfeited(game, new PlayerId(0), ForfeitReason.RESIGNED);

        assertThat(messaging.last.getEventType(), is("FORFEIT"));
        assertThat(messaging.last.getState(), instanceOf(BatailleCorseDto.class));
    }

    @Test
    void givenDisconnect_whenBroadcast_thenSendsDisconnectedResponse() {
        BatailleCorse game = new BatailleCorse(GameId.generate(), 2);

        broadcaster.disconnected(game, new PlayerId(0), 123L);

        assertThat(messaging.last.getEventType(), is("OPPONENT_DISCONNECTED"));
        assertThat(messaging.last.getState(), instanceOf(BatailleCorseDto.class));
    }
}
