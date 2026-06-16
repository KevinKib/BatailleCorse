package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.LobbyDto;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.sessionmanagement.application.GameFactories;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionGame;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class LobbyBroadcasterTest {

    static final class RecordingMessaging extends GameMessagingService {
        final List<PlayerId> seats = new ArrayList<>();
        final List<Response> payloads = new ArrayList<>();
        RecordingMessaging() { super(null); }
        @Override public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
            seats.add(seat);
            payloads.add((Response) payload);
        }
    }

    @Test
    void givenTwoClaimedSeats_whenBroadcast_thenOnlyClaimedReceiveLobbyView() {
        SessionGame lobby = SessionGame.create(
                GameId.generate(),
                List.of(new PlayerId(0), new PlayerId(1), new PlayerId(2)),
                "bullshit");
        lobby.claim(new PlayerId(0), "Host");
        lobby.claim(new PlayerId(1), "Bob");

        RecordingMessaging messaging = new RecordingMessaging();
        LobbyBroadcaster broadcaster = new LobbyBroadcaster(
                messaging, new GameFactories(List.of(new BullshitFactory())));

        broadcaster.broadcast(lobby, LifecycleEventType.JOIN.toString(), new EmptyEventData(), "Bob joined.");

        assertThat(messaging.seats, is(List.of(new PlayerId(0), new PlayerId(1))));
        Response toHost = messaging.payloads.get(0);
        assertThat(toHost.getEventType(), is("JOIN"));
        assertThat(((LobbyDto) toHost.getState()).mySeat(), is(0));
        assertThat(((LobbyDto) toHost.getState()).canStart(), is(true));
        assertThat(((LobbyDto) messaging.payloads.get(1).getState()).mySeat(), is(1));
    }
}
