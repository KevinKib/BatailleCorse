package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.domain.RoomFullException;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionToken;
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
        SessionGame lobby = service.createRoom("bullshit", "Alice");

        assertThat(lobby.seats().size(), is(6));
        assertThat(lobby.isClaimed(new PlayerId(0)), is(true));
        assertThat(lobby.seats().get(0).name(), is("Alice"));
        assertThat(lobby.isClaimed(new PlayerId(1)), is(false));
        assertThat(service.findGame(lobby.id()).isPresent(), is(false));
    }

    @Test
    void givenOpenRoom_whenJoinRoom_thenNextSeatClaimed() {
        SessionGame lobby = service.createRoom("bullshit", "Alice");

        JoinResult first = service.joinRoom(lobby.id(), "Bob");
        JoinResult second = service.joinRoom(lobby.id(), "Cara");

        assertThat(first.playerId(), is(new PlayerId(1)));
        assertThat(second.playerId(), is(new PlayerId(2)));
        assertThat(service.getGameSession(lobby.id()).seats().get(1).name(), is("Bob"));
    }

    @Test
    void givenFullRoom_whenJoinRoom_thenThrowsRoomFull() {
        SessionGame lobby = service.createRoom("bullshit", "Alice");
        for (int i = 1; i < 6; i++) {
            service.joinRoom(lobby.id(), "P" + i);
        }

        org.junit.jupiter.api.Assertions.assertThrows(
                RoomFullException.class, () -> service.joinRoom(lobby.id(), "Late"));
    }

    @Test
    void givenHostAndEnoughPlayers_whenStartGame_thenAggregateDealtToJoined() {
        SessionGame lobby = service.createRoom("bullshit", "Alice");
        service.joinRoom(lobby.id(), "Bob");
        service.joinRoom(lobby.id(), "Cara");
        SessionToken hostToken = lobby.findTokenByPlayer(new PlayerId(0)).orElseThrow();

        var game = service.startGame(lobby.id(), hostToken);

        assertThat(service.findGame(lobby.id()).isPresent(), is(true));
        assertThat(game.getPlayerIds().size(), is(3));
    }

    @Test
    void givenNonHostToken_whenStartGame_thenThrowsNotHost() {
        SessionGame lobby = service.createRoom("bullshit", "Alice");
        JoinResult bob = service.joinRoom(lobby.id(), "Bob");

        org.junit.jupiter.api.Assertions.assertThrows(
                NotHostException.class, () -> service.startGame(lobby.id(), bob.token()));
    }

    @Test
    void givenOnlyHost_whenStartGame_thenThrowsNotEnoughPlayers() {
        SessionGame lobby = service.createRoom("bullshit", "Alice");
        SessionToken hostToken = lobby.findTokenByPlayer(new PlayerId(0)).orElseThrow();

        org.junit.jupiter.api.Assertions.assertThrows(
                NotEnoughPlayersException.class, () -> service.startGame(lobby.id(), hostToken));
    }

    @Test
    void givenAlreadyStarted_whenStartAgain_thenThrowsAlreadyStarted() {
        SessionGame lobby = service.createRoom("bullshit", "Alice");
        service.joinRoom(lobby.id(), "Bob");
        SessionToken hostToken = lobby.findTokenByPlayer(new PlayerId(0)).orElseThrow();
        service.startGame(lobby.id(), hostToken);

        org.junit.jupiter.api.Assertions.assertThrows(
                GameAlreadyStartedException.class, () -> service.startGame(lobby.id(), hostToken));
    }

    @Test
    void givenStartedGame_whenJoinRoom_thenThrowsAlreadyStarted() {
        SessionGame lobby = service.createRoom("bullshit", "Alice");
        service.joinRoom(lobby.id(), "Bob");
        SessionToken hostToken = lobby.findTokenByPlayer(new PlayerId(0)).orElseThrow();
        service.startGame(lobby.id(), hostToken);

        org.junit.jupiter.api.Assertions.assertThrows(
                GameAlreadyStartedException.class, () -> service.joinRoom(lobby.id(), "Late"));
    }
}
