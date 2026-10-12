package org.kevinkib.cardgames.bataillecorse.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class BatailleCorseLifecycleBroadcasterTest {


    private final RecordingMessaging messaging = new RecordingMessaging();
    private final ForfeitLog reasons = new InMemoryForfeitLog();
    private final BatailleCorseStateBroadcaster stateBroadcaster = new BatailleCorseStateBroadcaster(messaging);
    private final BatailleCorseLifecycleBroadcaster broadcaster =
            new BatailleCorseLifecycleBroadcaster(stateBroadcaster, reasons);

    @Test
    void givenForfeit_whenBroadcast_thenSendsForfeitResponseWithBatailleCorseState() {
        BatailleCorse game = new BatailleCorse(GameId.generate(), 2);
        Seat seat = new Seat(game.getId(), new PlayerId(0));
        reasons.record(seat, ForfeitReason.RESIGNED);

        broadcaster.forfeited(game, new PlayerId(0), ForfeitReason.RESIGNED);

        assertThat(messaging.last().getEventType(), is("FORFEIT"));
        assertThat(messaging.last().getState(), instanceOf(BatailleCorseDto.class));
    }

    @Test
    void givenDisconnect_whenBroadcast_thenSendsDisconnectedResponse() {
        BatailleCorse game = new BatailleCorse(GameId.generate(), 2);

        broadcaster.disconnected(game, new PlayerId(0), 123L);

        assertThat(messaging.last().getEventType(), is("OPPONENT_DISCONNECTED"));
        assertThat(messaging.last().getState(), instanceOf(BatailleCorseDto.class));
    }

    @Test
    void everyLifecycleEventTellsTheStateListeners() {
        BatailleCorse game = new BatailleCorse(GameId.generate(), 2);
        java.util.List<String> events = new java.util.ArrayList<>();
        stateBroadcaster.addListener((g, type, data) -> events.add(type));

        broadcaster.disconnected(game, new PlayerId(0), 123L);
        broadcaster.reconnected(game, new PlayerId(0));
        broadcaster.forfeited(game, new PlayerId(0), ForfeitReason.RESIGNED);
        broadcaster.rematchStarted(game, new PlayerId(1));

        assertThat(events, is(java.util.List.of("OPPONENT_DISCONNECTED", "OPPONENT_RECONNECTED", "FORFEIT", "REMATCH")));
    }
}
