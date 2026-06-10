package org.kevinkib.bataillecorse.sessionmanagement.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.domain.GameMode;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionPlayer;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;
import org.kevinkib.bataillecorse.sessionmanagement.infrastructure.InMemorySessionRepository;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionServiceTest {

    private SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(new InMemorySessionRepository(java.time.Clock.systemUTC()));
    }

    @Nested
    class CreateGameTest {

        @Test
        void givenSoloMode_whenCreateGame_thenBothSeatsClaimed() {
            var game = service.createGame(2, GameMode.SOLO);

            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(0)), is(true));
            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(1)), is(true));
        }

        @Test
        void givenMultiplayerMode_whenCreateGame_thenOnlySeatZeroClaimed() {
            var game = service.createGame(2, GameMode.MULTIPLAYER);

            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(0)), is(true));
            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(1)), is(false));
        }
    }

    @Nested
    class JoinGameTest {

        @Test
        void givenMultiplayerGame_whenJoin_thenSeatOneClaimedAndTokenReturned() {
            var game = service.createGame(2, GameMode.MULTIPLAYER);

            JoinResult result = service.joinGame(game.getId());

            assertThat(result.playerId(), is(new PlayerId(1)));
            assertThat(result.token(), is(notNullValue()));
            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(1)), is(true));
        }

        @Test
        void givenSoloGame_whenJoin_thenThrowsSeatUnavailable() {
            var game = service.createGame(2, GameMode.SOLO);

            assertThrows(SeatUnavailableException.class, () -> service.joinGame(game.getId()));
        }

        @Test
        void givenAlreadyJoinedGame_whenJoinAgain_thenThrowsSeatUnavailable() {
            var game = service.createGame(2, GameMode.MULTIPLAYER);
            service.joinGame(game.getId());

            assertThrows(SeatUnavailableException.class, () -> service.joinGame(game.getId()));
        }
    }

    @Nested
    class FindPlayerIdByTokenTest {

        @Test
        void givenValidToken_whenFindPlayerIdByToken_thenReturnsPlayerId() {
            var game = service.createGame(2);
            SessionToken token = service.loadTokenByPlayerId(game.getId(), new PlayerId(0));

            Optional<PlayerId> result = service.findPlayerIdByToken(game.getId(), token);

            assertThat(result, is(Optional.of(new PlayerId(0))));
        }

        @Test
        void givenInvalidToken_whenFindPlayerIdByToken_thenReturnsEmpty() {
            var game = service.createGame(2);

            Optional<PlayerId> result = service.findPlayerIdByToken(game.getId(), SessionToken.generate());

            assertThat(result, is(Optional.empty()));
        }
    }

    @Test
    public void givenMultiplayerCreateWithName_whenCreating_thenSeatZeroClaimedWithName() {
        BatailleCorse game = service.createGame(2, GameMode.MULTIPLAYER, "Alice");

        List<SessionPlayer> seats = service.getSeats(game.getId());
        assertThat(seats.get(0).isClaimed(), is(true));
        assertThat(seats.get(0).name(), is("Alice"));
        assertThat(seats.get(1).isClaimed(), is(false));
    }

    @Test
    public void givenMultiplayerCreateWithBlankName_whenCreating_thenSeatZeroGetsDefaultName() {
        BatailleCorse game = service.createGame(2, GameMode.MULTIPLAYER, "  ");

        List<SessionPlayer> seats = service.getSeats(game.getId());
        assertThat(seats.get(0).name(), is("Player 1"));
    }

    @Test
    public void givenMultiplayerGame_whenJoiningWithName_thenSeatOneClaimedWithName() {
        BatailleCorse game = service.createGame(2, GameMode.MULTIPLAYER, "Alice");

        service.joinGame(game.getId(), "Bob");

        List<SessionPlayer> seats = service.getSeats(game.getId());
        assertThat(seats.get(1).isClaimed(), is(true));
        assertThat(seats.get(1).name(), is("Bob"));
    }

    @Test
    public void givenMultiplayerGame_whenJoiningWithBlankName_thenSeatOneGetsDefaultName() {
        BatailleCorse game = service.createGame(2, GameMode.MULTIPLAYER, null);

        service.joinGame(game.getId(), null);

        List<SessionPlayer> seats = service.getSeats(game.getId());
        assertThat(seats.get(1).name(), is("Player 2"));
    }

    @Nested
    class TouchTest {

        @Test
        void givenExistingGame_whenTouch_thenDoesNotThrow() {
            var game = service.createGame(2, GameMode.MULTIPLAYER);
            service.touch(game.getId()); // smoke: delegation wired
        }
    }
}
