package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class SessionLobbyTest {

    private SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
    }

    @Test
    void givenBullshit_whenCreateRoom_thenMaxSeatsHostClaimedNoGame() {
        RoomCreated room = service.createRoom("bullshit", "Alice");
        GameId id = new GameId(room.gameId());
        SessionGame lobby = service.getGameSession(id);

        assertThat(lobby.seats().size(), is(6));
        assertThat(lobby.isClaimed(new PlayerId(0)), is(true));
        assertThat(lobby.seats().get(0).name(), is("Alice"));
        assertThat(lobby.isClaimed(new PlayerId(1)), is(false));
        assertThat(service.findGame(id).isPresent(), is(false));
    }

    @Test
    void givenOpenRoom_whenJoinRoom_thenNextSeatClaimed() {
        GameId id = new GameId(service.createRoom("bullshit", "Alice").gameId());

        JoinResult first = service.joinRoom(id, "Bob");
        JoinResult second = service.joinRoom(id, "Cara");

        assertThat(first.playerId(), is(new PlayerId(1)));
        assertThat(second.playerId(), is(new PlayerId(2)));
        assertThat(service.getGameSession(id).seats().get(1).name(), is("Bob"));
    }

    @Test
    void givenFullRoom_whenJoinRoom_thenThrowsRoomFull() {
        GameId id = new GameId(service.createRoom("bullshit", "Alice").gameId());
        for (int i = 1; i < 6; i++) {
            service.joinRoom(id, "P" + i);
        }

        org.junit.jupiter.api.Assertions.assertThrows(
                RoomFullException.class, () -> service.joinRoom(id, "Late"));
    }

    @Test
    void givenHostAndEnoughPlayers_whenStartGame_thenAggregateDealtToJoined() {
        RoomCreated room = service.createRoom("bullshit", "Alice");
        GameId id = new GameId(room.gameId());
        service.joinRoom(id, "Bob");
        service.joinRoom(id, "Cara");

        var game = service.startGame(id, room.hostToken());

        assertThat(service.findGame(id).isPresent(), is(true));
        assertThat(game.getPlayerIds().size(), is(3));
    }

    @Test
    void givenNonHostToken_whenStartGame_thenThrowsNotHost() {
        GameId id = new GameId(service.createRoom("bullshit", "Alice").gameId());
        JoinResult bob = service.joinRoom(id, "Bob");

        org.junit.jupiter.api.Assertions.assertThrows(
                NotHostException.class, () -> service.startGame(id, bob.token()));
    }

    @Test
    void givenOnlyHost_whenStartGame_thenThrowsNotEnoughPlayers() {
        RoomCreated room = service.createRoom("bullshit", "Alice");
        GameId id = new GameId(room.gameId());

        org.junit.jupiter.api.Assertions.assertThrows(
                NotEnoughPlayersException.class, () -> service.startGame(id, room.hostToken()));
    }

    @Test
    void givenAlreadyStarted_whenStartAgain_thenThrowsAlreadyStarted() {
        RoomCreated room = service.createRoom("bullshit", "Alice");
        GameId id = new GameId(room.gameId());
        service.joinRoom(id, "Bob");
        service.startGame(id, room.hostToken());

        org.junit.jupiter.api.Assertions.assertThrows(
                GameAlreadyStartedException.class, () -> service.startGame(id, room.hostToken()));
    }

    @Test
    void givenStartedGame_whenJoinRoom_thenThrowsAlreadyStarted() {
        RoomCreated room = service.createRoom("bullshit", "Alice");
        GameId id = new GameId(room.gameId());
        service.joinRoom(id, "Bob");
        service.startGame(id, room.hostToken());

        org.junit.jupiter.api.Assertions.assertThrows(
                GameAlreadyStartedException.class, () -> service.joinRoom(id, "Late"));
    }
}
