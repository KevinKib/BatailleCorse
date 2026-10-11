package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionServiceLeaveLobbyTest {

    private SessionService service;
    private GameId id;
    private String hostToken;
    private final List<GameId> removed = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
        service.setLobbyRemovalListener(removed::add);
        RoomCreated room = service.createRoom("bullshit", "Alice");
        id = new GameId(room.gameId());
        hostToken = room.hostToken();
    }

    private LobbyView viewOf(LeaveResult result, String token) {
        return result.views().stream().filter(v -> v.token().equals(token)).findFirst().orElseThrow().view();
    }

    @Test
    void givenAGuest_whenLeaves_thenTheSeatIsFreeAndANewcomerCanTakeIt() {
        String bobToken = service.joinRoom(id, "Bob").token();
        String carolToken = service.joinRoom(id, "Carol").token();

        LeaveResult result = service.leaveLobby(id, bobToken);

        assertThat(result.lobbyRemoved(), is(false));
        assertThat(service.seats(id).get(1).name(), is("Carol"));
        assertThat(service.seats(id).get(2).joined(), is(false));
        assertThat(service.findPlayerIdByToken(id, carolToken).get(), is(new PlayerId(1)));
        assertThat(service.findPlayerIdByToken(id, bobToken).isPresent(), is(false));
        JoinResult dave = service.joinRoom(id, "Dave");
        assertThat(dave.playerId(), is(new PlayerId(2)));
        assertThat(dave.token(), is(not(bobToken)));
        assertThat(dave.token(), is(not(carolToken)));
        assertThat(viewOf(result, hostToken).players().get(1).name(), is("Carol"));
        assertThat(viewOf(result, carolToken).mySeat(), is(1));
    }

    @Test
    void givenHostAndAHumanLeft_whenHostLeaves_thenTheHumanIsTheNewHostWithStartAvailable() {
        String bobToken = service.joinRoom(id, "Bob").token();
        service.addBot(id, hostToken);

        LeaveResult result = service.leaveLobby(id, hostToken);

        assertThat(result.lobbyRemoved(), is(false));
        assertThat(result.views().size(), is(1));
        LobbyView bobView = viewOf(result, bobToken);
        assertThat(bobView.mySeat(), is(0));
        assertThat(bobView.hostSeat(), is(0));
        assertThat(bobView.canStart(), is(true));
        assertThat(bobView.removableBotSeats(), contains(1));
        assertThat(service.findPlayerIdByToken(id, hostToken).isPresent(), is(false));
        assertThat(removed.isEmpty(), is(true));
    }

    @Test
    void givenHostAndOnlyBots_whenHostLeaves_thenTheLobbyIsRemovedAndTheListenerNotified() {
        service.addBot(id, hostToken);

        LeaveResult result = service.leaveLobby(id, hostToken);

        assertThat(result.lobbyRemoved(), is(true));
        assertThat(result.views().isEmpty(), is(true));
        assertThat(removed, contains(id));
        assertThrows(IllegalArgumentException.class, () -> service.joinRoom(id, "Bob"));
    }

    @Test
    void givenAloneHost_whenHostLeaves_thenTheLobbyIsRemoved() {
        LeaveResult result = service.leaveLobby(id, hostToken);

        assertThat(result.lobbyRemoved(), is(true));
        assertThat(removed, contains(id));
    }

    @Test
    void givenALeave_thenTheVersionOfTheRemainingViewsIncreases() {
        String bobToken = service.joinRoom(id, "Bob").token();
        long before = service.lobbyView(id, hostToken).lobbyVersion();

        LeaveResult result = service.leaveLobby(id, bobToken);

        assertThat(viewOf(result, hostToken).lobbyVersion(), greaterThan(before));
    }

    @Test
    void givenAStartedGame_whenLeave_thenRefusedAndNothingChanges() {
        String bobToken = service.joinRoom(id, "Bob").token();
        service.startGame(id, hostToken);

        assertThrows(GameAlreadyStartedException.class, () -> service.leaveLobby(id, bobToken));

        assertThat(service.findPlayerIdByToken(id, bobToken).get(), is(new PlayerId(1)));
        assertThat(removed.isEmpty(), is(true));
    }

    @Test
    void givenAnUnknownToken_whenLeave_thenInvalidToken() {
        assertThrows(InvalidTokenException.class, () -> service.leaveLobby(id, UUID.randomUUID().toString()));
    }

    @Test
    void givenATokenAlreadyUsedToLeave_whenLeaveAgain_thenInvalidTokenAndNoOneElseIsAffected() {
        String bobToken = service.joinRoom(id, "Bob").token();
        service.joinRoom(id, "Carol");
        service.leaveLobby(id, bobToken);

        assertThrows(InvalidTokenException.class, () -> service.leaveLobby(id, bobToken));

        assertThat(service.seats(id).get(1).name(), is("Carol"));
        assertThat(service.seats(id).get(0).name(), is("Alice"));
    }

    @Test
    void givenAnUnknownLobby_whenLeave_thenNotFound() {
        assertThrows(IllegalArgumentException.class,
                () -> service.leaveLobby(GameId.generate(), hostToken));
    }

    @Test
    void givenTheHostLeavesAndTheLastHumanThenLeaves_thenTheLobbyIsRemovedOnlyAtTheSecond() {
        String bobToken = service.joinRoom(id, "Bob").token();
        service.addBot(id, hostToken);

        service.leaveLobby(id, hostToken);
        assertThat(removed.isEmpty(), is(true));
        LeaveResult last = service.leaveLobby(id, bobToken);

        assertThat(last.lobbyRemoved(), is(true));
        assertThat(removed, contains(id));
    }

    @Test
    void givenTheRoomLeftWithTooFewPlayers_whenNewHostStarts_thenNotEnoughPlayers() {
        String bobToken = service.joinRoom(id, "Bob").token();
        service.leaveLobby(id, hostToken);

        assertThrows(NotEnoughPlayersException.class, () -> service.startGame(id, bobToken));
    }
}
