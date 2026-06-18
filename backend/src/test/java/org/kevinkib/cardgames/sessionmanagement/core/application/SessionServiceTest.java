package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameMode;
import org.kevinkib.cardgames.sessionmanagement.core.application.SeatView;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;

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
        service = new SessionService(new InMemorySessionRepository(java.time.Clock.systemUTC()), new GameFactories(java.util.List.of(new BatailleCorseFactory())));
    }

    @Nested
    class CreateGameTest {

        @Test
        void givenSoloMode_whenCreateGame_thenBothSeatsClaimed() {
            var game = service.createGame("bataille-corse", 2, GameMode.SOLO);

            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(0)), is(true));
            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(1)), is(true));
        }

        @Test
        void givenMultiplayerMode_whenCreateGame_thenOnlySeatZeroClaimed() {
            var game = service.createGame("bataille-corse", 2, GameMode.MULTIPLAYER);

            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(0)), is(true));
            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(1)), is(false));
        }
    }

    @Nested
    class JoinGameTest {

        @Test
        void givenMultiplayerGame_whenJoin_thenSeatOneClaimedAndTokenReturned() {
            var game = service.createGame("bataille-corse", 2, GameMode.MULTIPLAYER);

            JoinResult result = service.joinGame(game.getId());

            assertThat(result.playerId(), is(new PlayerId(1)));
            assertThat(result.token(), is(notNullValue()));
            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(1)), is(true));
        }

        @Test
        void givenSoloGame_whenJoin_thenThrowsSeatUnavailable() {
            var game = service.createGame("bataille-corse", 2, GameMode.SOLO);

            assertThrows(SeatUnavailableException.class, () -> service.joinGame(game.getId()));
        }

        @Test
        void givenAlreadyJoinedGame_whenJoinAgain_thenThrowsSeatUnavailable() {
            var game = service.createGame("bataille-corse", 2, GameMode.MULTIPLAYER);
            service.joinGame(game.getId());

            assertThrows(SeatUnavailableException.class, () -> service.joinGame(game.getId()));
        }
    }

    @Nested
    class FindPlayerIdByTokenTest {

        @Test
        void givenValidToken_whenFindPlayerIdByToken_thenReturnsPlayerId() {
            var game = service.createGame("bataille-corse", 2);
            String token = service.tokenForSeat(game.getId(), new PlayerId(0));

            Optional<PlayerId> result = service.findPlayerIdByToken(game.getId(), token);

            assertThat(result, is(Optional.of(new PlayerId(0))));
        }

        @Test
        void givenInvalidToken_whenFindPlayerIdByToken_thenReturnsEmpty() {
            var game = service.createGame("bataille-corse", 2);

            Optional<PlayerId> result = service.findPlayerIdByToken(game.getId(), java.util.UUID.randomUUID().toString());

            assertThat(result, is(Optional.empty()));
        }
    }

    @Test
    public void givenMultiplayerCreateWithName_whenCreating_thenSeatZeroClaimedWithName() {
        var game = service.createGame("bataille-corse", 2, GameMode.MULTIPLAYER, "Alice");

        List<SeatView> seats = service.seats(game.getId());
        assertThat(seats.get(0).joined(), is(true));
        assertThat(seats.get(0).name(), is("Alice"));
        assertThat(seats.get(1).joined(), is(false));
    }

    @Test
    public void givenMultiplayerCreateWithBlankName_whenCreating_thenSeatZeroGetsDefaultName() {
        var game = service.createGame("bataille-corse", 2, GameMode.MULTIPLAYER, "  ");

        List<SeatView> seats = service.seats(game.getId());
        assertThat(seats.get(0).name(), is("Player 1"));
    }

    @Test
    public void givenMultiplayerGame_whenJoiningWithName_thenSeatOneClaimedWithName() {
        var game = service.createGame("bataille-corse", 2, GameMode.MULTIPLAYER, "Alice");

        service.joinGame(game.getId(), "Bob");

        List<SeatView> seats = service.seats(game.getId());
        assertThat(seats.get(1).joined(), is(true));
        assertThat(seats.get(1).name(), is("Bob"));
    }

    @Test
    public void givenMultiplayerGame_whenJoiningWithBlankName_thenSeatOneGetsDefaultName() {
        var game = service.createGame("bataille-corse", 2, GameMode.MULTIPLAYER, null);

        service.joinGame(game.getId(), null);

        List<SeatView> seats = service.seats(game.getId());
        assertThat(seats.get(1).name(), is("Player 2"));
    }

    @Nested
    class RematchTest {

        @Test
        void givenSoloGame_whenRematch_thenSameIdAndSeatsPreserved() {
            var game = service.createGame("bataille-corse", 2, GameMode.SOLO, "Alice");
            String seat0TokenBefore = service.tokenForSeat(game.getId(), new PlayerId(0));
            String seat0NameBefore = service.seats(game.getId()).get(0).name();

            var fresh = service.rematch(game.getId());

            assertThat(fresh.getId(), is(game.getId()));
            assertThat(fresh.isFinished(), is(false));
            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(0)), is(true));
            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(1)), is(true));
            assertThat(service.seats(game.getId()).get(0).name(), is(seat0NameBefore));
            assertThat(service.tokenForSeat(game.getId(), new PlayerId(0)), is(seat0TokenBefore));
        }

        @Test
        void givenRequestedRematch_whenRematch_thenRequestFlagsCleared() {
            var game = service.createGame("bataille-corse", 2, GameMode.SOLO);
            service.requestRematch(game.getId(), new PlayerId(0));
            service.requestRematch(game.getId(), new PlayerId(1));

            service.rematch(game.getId());

            assertThat(service.requestRematch(game.getId(), new PlayerId(0)), is(false));
        }

        @Test
        void givenOneSeatLeft_whenTheOtherJoins_thenRematchStartsAmongStayers() {
            var game = service.createGame("bataille-corse", 2, GameMode.SOLO);
            var id = game.getId();
            service.leaveRematch(id, new PlayerId(0)); // seat 0 went home

            RematchOutcome outcome = service.joinRematch(id, new PlayerId(1));

            assertThat(outcome.started(), is(true));   // only seat 1 is staying, and it asked
            assertThat(outcome.game().getId(), is(id)); // fresh game surfaced under the same id
            assertThat(outcome.ready(), is(1));
            assertThat(outcome.eligible(), is(1));
        }

        @Test
        void givenTwoStayers_whenOneJoins_thenPendingWithReadyOne() {
            var game = service.createGame("bataille-corse", 2, GameMode.SOLO);
            var id = game.getId();

            RematchOutcome outcome = service.joinRematch(id, new PlayerId(0));

            assertThat(outcome.started(), is(false));
            assertThat(outcome.ready(), is(1));
            assertThat(outcome.eligible(), is(2));
        }
    }

    @Nested
    class TouchTest {

        @Test
        void givenExistingGame_whenTouch_thenDoesNotThrow() {
            var game = service.createGame("bataille-corse", 2, GameMode.MULTIPLAYER);
            service.touch(game.getId()); // smoke: delegation wired
        }
    }

    @Test
    void playAgain_reopensRoom_andNextGameHasOnlyReturningPlayers() {
        var bullshitService = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
        RoomCreated room = bullshitService.createRoom("bullshit", "Alice");
        GameId id = new GameId(room.gameId());
        bullshitService.joinRoom(id, "Bob");
        bullshitService.joinRoom(id, "Cara");                  // 3 joined of a 6-seat room
        bullshitService.startGame(id, room.hostToken());       // game dealt to 3

        JoinResult first = bullshitService.playAgain(id, "Alice");  // reopens -> seat 0 (host)
        JoinResult second = bullshitService.playAgain(id, "Bob");   // joins reopened lobby -> seat 1
        Game fresh = bullshitService.startGame(id, first.token());

        assertThat(first.playerId(), is(new PlayerId(0)));
        assertThat(second.playerId(), is(new PlayerId(1)));
        assertThat(fresh.getPlayerIds().size(), is(2));     // only the two who came back, not 3 or 6
    }

    @Nested
    class TypedGetGameTest {

        @Test
        void givenMatchingType_whenGetGame_thenReturnsTypedGame() {
            var game = service.createGame("bataille-corse", 2, GameMode.SOLO);

            org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse typed =
                    service.getGame(game.getId(), org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse.class);

            assertThat(typed.getId(), is(game.getId()));
        }

        @Test
        void givenWrongType_whenGetGame_thenThrows() {
            var game = service.createGame("bataille-corse", 2, GameMode.SOLO);

            assertThrows(IllegalStateException.class,
                    () -> service.getGame(game.getId(), org.kevinkib.cardgames.game.FakeGame.class));
        }
    }
}
