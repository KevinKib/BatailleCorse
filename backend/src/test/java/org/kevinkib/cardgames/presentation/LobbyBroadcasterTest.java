package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.LobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class LobbyBroadcasterTest {

    
    @Test
    void givenAddressedViews_whenSend_thenEachGoesToItsOwnTokenOnly() {
        SessionGame lobby = SessionGame.create(
                GameId.generate(),
                List.of(new PlayerId(0), new PlayerId(1), new PlayerId(2)),
                "bullshit");
        lobby.claimSeat(new PlayerId(0), "Host");
        lobby.claimSeat(new PlayerId(1), "Bob");
        InMemorySessionRepository repository = new InMemorySessionRepository(Clock.systemUTC());
        repository.saveLobby(lobby);
        SessionService sessionService =
                new SessionService(repository, new GameFactories(List.of(new BullshitFactory())));

        RecordingMessaging messaging = new RecordingMessaging();
        messaging.resolvingTokensWith(sessionService);
        LobbyBroadcaster broadcaster = new LobbyBroadcaster(messaging);

        broadcaster.send(lobby.id(), sessionService.lobbyViews(lobby.id()), LifecycleEventType.JOIN.toString(),
                new EmptyEventData(), "Bob joined.");

        assertThat(messaging.seats, is(List.of(new PlayerId(0), new PlayerId(1))));
        Response toHost = messaging.payloads.get(0);
        assertThat(toHost.getEventType(), is("JOIN"));
        assertThat(((LobbyView) toHost.getState()).mySeat(), is(0));
        assertThat(((LobbyView) toHost.getState()).canStart(), is(true));
        assertThat(((LobbyView) messaging.payloads.get(1).getState()).mySeat(), is(1));
    }
}
