package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.domain.CannotRemoveHostException;
import org.kevinkib.cardgames.sessionmanagement.core.domain.NotAHumanSeatException;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

class SessionServiceRemoveSeatTest {

    private SessionService service;
    private GameId id;
    private String hostToken;
    private String bobToken;
    private String carolToken;

    @BeforeEach
    void setUp() {
        service = new SessionService(new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
        RoomCreated room = service.createRoom("bullshit", "Alice");
        id = new GameId(room.gameId());
        hostToken = room.hostToken();
        bobToken = service.joinRoom(id, "Bob").token();
        carolToken = service.joinRoom(id, "Carol").token();
    }

    @Test
    void givenAHost_whenRemovingAGuest_thenTheTokenNoLongerResolvesAndLaterSeatsShift() {
        long before = service.lobbyView(id, hostToken).lobbyVersion();

        SeatRemoval removal = service.removeSeat(id, hostToken, 1);

        assertThat(removal.removedToken(), is(bobToken));
        assertThat(service.findPlayerIdByToken(id, bobToken).isPresent(), is(false));
        assertThat(service.findPlayerIdByToken(id, carolToken).orElseThrow(), is(new PlayerId(1)));
        assertThat(service.findPlayerIdByToken(id, hostToken).orElseThrow(), is(new PlayerId(0)));
        assertThat(removal.views().stream().map(AddressedLobbyView::token).toList(), contains(hostToken, carolToken));
        assertThat(removal.views().get(0).view().lobbyVersion(), greaterThan(before));
        assertThat(removal.views().get(0).view().players().stream().filter(p -> p.joined()).count(), is(2L));
    }

    @Test
    void givenARemovedSeat_whenSomeoneJoins_thenTheNewOccupantGetsAFreshToken() {
        service.removeSeat(id, hostToken, 2);

        String newcomer = service.joinRoom(id, "Dave").token();

        assertThat(newcomer, not(carolToken));
        assertThat(service.findPlayerIdByToken(id, carolToken).isPresent(), is(false));
    }

    @Test
    void givenANonHost_whenRemovingASeat_thenRefusedAndNothingChanges() {
        assertThrows(NotHostException.class, () -> service.removeSeat(id, bobToken, 2));

        assertThat(service.findPlayerIdByToken(id, carolToken).isPresent(), is(true));
    }

    @Test
    void givenTheHostSeat_whenRemoving_thenRefused() {
        assertThrows(CannotRemoveHostException.class, () -> service.removeSeat(id, hostToken, 0));
    }

    @Test
    void givenABotOrFreeSeat_whenRemovingAsHuman_thenRefused() {
        service.addBot(id, hostToken);
        assertThrows(NotAHumanSeatException.class, () -> service.removeSeat(id, hostToken, 3));
        assertThrows(NotAHumanSeatException.class, () -> service.removeSeat(id, hostToken, 4));
    }

    @Test
    void givenAStartedGame_whenRemoving_thenRefused() {
        service.startGame(id, hostToken);

        assertThrows(GameAlreadyStartedException.class, () -> service.removeSeat(id, hostToken, 1));
    }

    @Test
    void givenNoViews_thenEmptyIsNotReturnedForTheRemoved() {
        SeatRemoval removal = service.removeSeat(id, hostToken, 1);

        assertThat(removal.views().stream().map(AddressedLobbyView::token).toList(), not(empty()));
        assertThat(removal.views().stream().anyMatch(v -> v.token().equals(bobToken)), is(false));
    }
}
