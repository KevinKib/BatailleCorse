package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.empty;

/** A human whose connection dropped is flagged in the lobby so the host knows who to remove. */
class SessionServiceConnectionTest {

    private SessionService service;
    private GameId id;
    private String hostToken;
    private String bobToken;

    @BeforeEach
    void setUp() {
        service = new SessionService(new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
        RoomCreated room = service.createRoom("bullshit", "Alice");
        id = new GameId(room.gameId());
        hostToken = room.hostToken();
        bobToken = service.joinRoom(id, "Bob").token();
    }

    private boolean bobDisconnectedInHostView() {
        return service.lobbyView(id, hostToken).players().get(1).disconnected();
    }

    @Test
    void givenAConnectedSeat_thenItIsNotFlagged() {
        assertThat(bobDisconnectedInHostView(), is(false));
    }

    @Test
    void givenADroppedConnection_thenTheSeatIsFlaggedForEveryoneWithANewerVersion() {
        long before = service.lobbyView(id, hostToken).lobbyVersion();

        List<AddressedLobbyView> views = service.markSeatConnection(id, bobToken, false);

        assertThat(bobDisconnectedInHostView(), is(true));
        assertThat(views.size(), is(2));
        assertThat(views.get(0).view().lobbyVersion(), greaterThan(before));
        assertThat(views.get(1).view().players().get(1).disconnected(), is(true));
    }

    @Test
    void givenAnUnchangedState_thenNothingIsRebroadcast() {
        assertThat(service.markSeatConnection(id, bobToken, true), is(empty()));
        service.markSeatConnection(id, bobToken, false);
        assertThat(service.markSeatConnection(id, bobToken, false), is(empty()));
    }

    @Test
    void givenAReconnection_thenTheFlagIsCleared() {
        service.markSeatConnection(id, bobToken, false);

        List<AddressedLobbyView> views = service.markSeatConnection(id, bobToken, true);

        assertThat(views.size(), is(2));
        assertThat(bobDisconnectedInHostView(), is(false));
    }

    @Test
    void givenTheFlagHolderSeatShifts_thenTheFlagFollowsThem() {
        String carolToken = service.joinRoom(id, "Carol").token();
        service.markSeatConnection(id, carolToken, false);

        service.removeSeat(id, hostToken, 1);

        assertThat(service.lobbyView(id, hostToken).players().get(1).disconnected(), is(true));
    }

    @Test
    void givenAStartedGameOrUnknownToken_thenNothingHappens() {
        assertThat(service.markSeatConnection(id, "nope", false), is(empty()));
        service.startGame(id, hostToken);
        assertThat(service.markSeatConnection(id, bobToken, false), is(empty()));
    }
}
