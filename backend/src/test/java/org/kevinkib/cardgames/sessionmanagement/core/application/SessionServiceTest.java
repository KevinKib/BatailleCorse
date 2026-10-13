package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.bot.ScriptedRandom;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.domain.claim.RankTarget;
import org.kevinkib.cardgames.bullshit.domain.claim.SuitTarget;
import org.kevinkib.cardgames.game.FakeGameFactory;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;
import org.kevinkib.cardgames.presentation.support.StartedRooms;
import org.kevinkib.cardgames.sessionmanagement.core.application.SeatView;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionServiceTest {

    private SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(new InMemorySessionRepository(java.time.Clock.systemUTC()), new GameFactories(java.util.List.of(new BatailleCorseFactory())));
    }

    @Nested
    class AddBotTest {

        /** A fake game that has no computer player. */
        class FakeGameWithoutBot extends FakeGameFactory {
            @Override public boolean supportsBots() { return false; }
        }

        private SessionService serviceWith(org.kevinkib.cardgames.game.GameFactory... factories) {
            return new SessionService(new InMemorySessionRepository(Clock.systemUTC()),
                    new GameFactories(List.of(factories)));
        }

        @Test
        void givenGameWithoutBot_whenAddBot_thenRefusedAndNoBotSeat() {
            SessionService sessions = serviceWith(new FakeGameWithoutBot());
            RoomCreated room = sessions.createRoom(FakeGameFactory.GAME_TYPE, "Alice");
            GameId id = new GameId(room.gameId());

            assertThrows(BotsNotSupportedException.class, () -> sessions.addBot(id, room.hostToken()));
            assertThrows(BotsNotSupportedException.class, () -> sessions.addBotViews(id, room.hostToken()));

            assertThat(sessions.botSeats(id).isEmpty(), is(true));
            assertThat(sessions.seats(id).get(1).joined(), is(false));
        }

        @Test
        void givenGameWithoutBot_whenNonHostAddsBot_thenStillNotHost() {
            SessionService sessions = serviceWith(new FakeGameWithoutBot());
            RoomCreated room = sessions.createRoom(FakeGameFactory.GAME_TYPE, "Alice");
            GameId id = new GameId(room.gameId());
            String bob = sessions.joinRoom(id, "Bob").token();

            assertThrows(NotHostException.class, () -> sessions.addBot(id, bob));
        }

        @Test
        void givenGameWithBots_whenAddBot_thenBotSeated() {
            SessionService sessions = serviceWith(new FakeGameFactory());
            RoomCreated room = sessions.createRoom(FakeGameFactory.GAME_TYPE, "Alice");
            GameId id = new GameId(room.gameId());

            sessions.addBot(id, room.hostToken());

            assertThat(sessions.botSeats(id).size(), is(1));
        }
    }

    @Nested
    class FindPlayerIdByTokenTest {

        @Test
        void givenValidToken_whenFindPlayerIdByToken_thenReturnsPlayerId() {
            var game = StartedRooms.start(service, "bataille-corse", 2);
            String token = service.tokenForSeat(game.getId(), new PlayerId(0));

            Optional<PlayerId> result = service.findPlayerIdByToken(game.getId(), token);

            assertThat(result, is(Optional.of(new PlayerId(0))));
        }

        @Test
        void givenInvalidToken_whenFindPlayerIdByToken_thenReturnsEmpty() {
            var game = StartedRooms.start(service, "bataille-corse", 2);

            Optional<PlayerId> result = service.findPlayerIdByToken(game.getId(), java.util.UUID.randomUUID().toString());

            assertThat(result, is(Optional.empty()));
        }
    }

    @Test
    void givenCreateRoomWithName_thenSeatZeroClaimedWithName() {
        RoomCreated room = service.createRoom("bataille-corse", "Alice");

        List<SeatView> seats = service.seats(new GameId(room.gameId()));
        assertThat(seats.get(0).joined(), is(true));
        assertThat(seats.get(0).name(), is("Alice"));
        assertThat(seats.get(1).joined(), is(false));
    }

    @Test
    void givenCreateRoomWithBlankName_thenSeatZeroGetsDefaultName() {
        RoomCreated room = service.createRoom("bataille-corse", "  ");

        assertThat(service.seats(new GameId(room.gameId())).get(0).name(), is("Player 1"));
    }

    @Test
    void givenRoom_whenJoiningWithAndWithoutAName_thenTheNextSeatsGetThem() {
        RoomCreated room = service.createRoom("bataille-corse", "Alice");
        GameId id = new GameId(room.gameId());

        JoinResult joined = service.joinRoom(id, "Bob");

        assertThat(joined.playerId(), is(new PlayerId(1)));
        assertThat(joined.token(), is(notNullValue()));
        assertThat(service.seats(id).get(1).name(), is("Bob"));
    }

    @Test
    void givenRoom_whenJoiningWithoutAName_thenTheSeatGetsItsDefaultName() {
        RoomCreated room = service.createRoom("bataille-corse", null);
        GameId id = new GameId(room.gameId());

        service.joinRoom(id, null);

        assertThat(service.seats(id).get(1).name(), is("Player 2"));
    }

    @Nested
    class TouchTest {

        @Test
        void givenExistingGame_whenTouch_thenDoesNotThrow() {
            var game = StartedRooms.start(service, "bataille-corse", 2);
            service.touch(game.getId()); // smoke: delegation wired
        }
    }

    /**
     * Exercises the Bullshit reopen-the-room rematch workflow across the multi-participant
     * permutations where the recurring bugs lived: who returns, in what order, and who stays away.
     * Pure service level — deterministic and CI-friendly. (Message routing on reopen is covered
     * separately by GameMessagingServiceTest / SeatSubscriptionInterceptorTest.)
     */
    @Nested
    class PlayAgainTest {

        private SessionService bullshit() {
            return new SessionService(
                    new InMemorySessionRepository(Clock.systemUTC()),
                    new GameFactories(List.of(new BullshitFactory())));
        }

        /** Alice (seat 0, host) + Bob (1) + Cara (2), game started — the shared starting point. */
        private GameId startedThreePlayerRoom(SessionService service) {
            RoomCreated room = service.createRoom("bullshit", "Alice");
            GameId id = new GameId(room.gameId());
            service.joinRoom(id, "Bob");
            service.joinRoom(id, "Cara");
            service.startGame(id, room.hostToken());
            return id;
        }

        @Test
        void givenAllReturn_whenPlayAgain_thenFirstIsHostAndNextGameHasOnlyReturners() {
            var service = bullshit();
            GameId id = startedThreePlayerRoom(service);

            JoinResult first = service.playAgain(id, "Alice");   // reopens -> seat 0 (host)
            JoinResult second = service.playAgain(id, "Bob");    // joins reopened lobby -> seat 1
            Game fresh = service.startGame(id, first.token());

            assertThat(first.playerId(), is(new PlayerId(0)));
            assertThat(second.playerId(), is(new PlayerId(1)));
            assertThat(fresh.getPlayerIds().size(), is(2));      // the two who came back, not 3 or 6
        }

        @Test
        void givenMiddleSeatStaysAway_whenRemainingReturn_thenTheyGetContiguousSeats() {
            var service = bullshit();
            GameId id = startedThreePlayerRoom(service);          // Alice=0, Bob=1, Cara=2

            JoinResult alice = service.playAgain(id, "Alice");    // reopens -> seat 0
            JoinResult cara = service.playAgain(id, "Cara");      // Bob (seat 1) does not return
            Game fresh = service.startGame(id, alice.token());

            assertThat(alice.playerId(), is(new PlayerId(0)));
            assertThat(cara.playerId(), is(new PlayerId(1)));     // contiguous — no gap where Bob was
            assertThat(fresh.getPlayerIds().size(), is(2));
        }

        @Test
        void givenRoomAlreadyReopened_whenSecondReturns_thenJoinsWithoutEvictingTheFirst() {
            var service = bullshit();
            GameId id = startedThreePlayerRoom(service);

            JoinResult first = service.playAgain(id, "Alice");    // reopens -> seat 0
            JoinResult second = service.playAgain(id, "Bob");     // must only join, not re-reopen

            assertThat(second.playerId(), is(new PlayerId(1)));
            assertThat(service.isSeatClaimed(id, new PlayerId(0)), is(true));   // first not evicted
            assertThat(service.findPlayerIdByToken(id, first.token()),
                    is(Optional.of(new PlayerId(0))));                          // first's token still valid
        }

        @Test
        void givenFormerLastSeatReturnsFirst_thenTheyBecomeSeatZeroHost() {
            var service = bullshit();
            GameId id = startedThreePlayerRoom(service);          // Cara was seat 2

            JoinResult cara = service.playAgain(id, "Cara");      // first to return
            JoinResult alice = service.playAgain(id, "Alice");

            assertThat(cara.playerId(), is(new PlayerId(0)));     // recycled to seat 0
            assertThat(alice.playerId(), is(new PlayerId(1)));
            assertThat(service.seats(id).get(0).name(), is("Cara"));
            // Cara is the new host: she can start the reopened game with her token.
            Game fresh = service.startGame(id, cara.token());
            assertThat(fresh.getPlayerIds().size(), is(2));
        }

        @Test
        void givenStartedGame_whenReopened_thenEveryFormerSeatTokenStopsResolving() {
            var service = bullshit();
            GameId id = startedThreePlayerRoom(service);
            String t0 = service.tokenForSeat(id, new PlayerId(0));
            String t1 = service.tokenForSeat(id, new PlayerId(1));
            String t2 = service.tokenForSeat(id, new PlayerId(2));

            service.playAgain(id, "Alice");   // reopen

            // Stale tokens no longer resolve -> stale token-addressed subscriptions can never match.
            assertThat(service.findPlayerIdByToken(id, t0), is(Optional.empty()));
            assertThat(service.findPlayerIdByToken(id, t1), is(Optional.empty()));
            assertThat(service.findPlayerIdByToken(id, t2), is(Optional.empty()));
            assertThat(service.tokenForSeat(id, new PlayerId(0)), is(not(t0)));   // recycled seat gets a fresh token
        }
    }

    @Nested
    class TypedGetGameTest {

        @Test
        void givenMatchingType_whenGetGame_thenReturnsTypedGame() {
            var game = StartedRooms.start(service, "bataille-corse", 2);

            org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse typed =
                    service.getGame(game.getId(), org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse.class);

            assertThat(typed.getId(), is(game.getId()));
        }

        @Test
        void givenWrongType_whenGetGame_thenThrows() {
            var game = StartedRooms.start(service, "bataille-corse", 2);

            assertThrows(IllegalStateException.class,
                    () -> service.getGame(game.getId(), org.kevinkib.cardgames.game.FakeGame.class));
        }
    }

    @Nested
    class ClaimModeOptionTest {

        private SessionService bullshitService;

        @BeforeEach
        void setUpBullshit() {
            bullshitService = new SessionService(
                    new InMemorySessionRepository(Clock.systemUTC()),
                    new GameFactories(List.of(new BullshitFactory(new ScriptedRandom(0.0, 0.0, 0.0, 0.0)))));
        }

        private GameId startWithTwoPlayers(GameOptions options) {
            RoomCreated room = bullshitService.createRoom("bullshit", "Alice", options);
            GameId id = new GameId(room.gameId());
            bullshitService.joinRoom(id, "Bob");
            bullshitService.startGame(id, room.hostToken());
            return id;
        }

        @Test
        void givenSuitOption_whenStart_thenInitialTargetIsHeart() {
            GameId id = startWithTwoPlayers(GameOptions.of(Map.of("claimMode", "suit")));

            Bullshit game = (Bullshit) bullshitService.getGame(id);
            assertThat(game.getCurrentTarget(), is(new SuitTarget(FrenchSuit.HEART)));
        }

        @Test
        void givenNoOption_whenStart_thenDefaultShortDeckSoInitialTargetIsSeven() {
            GameId id = startWithTwoPlayers(GameOptions.none());

            Bullshit game = (Bullshit) bullshitService.getGame(id);
            assertThat(game.getCurrentTarget(), is(new RankTarget(FrenchRank.SEVEN)));
        }

        @Test
        void givenSuitOption_whenReopenedAndRestarted_thenStillHeart() {
            RoomCreated room = bullshitService.createRoom(
                    "bullshit", "Alice", GameOptions.of(Map.of("claimMode", "suit")));
            GameId id = new GameId(room.gameId());
            bullshitService.joinRoom(id, "Bob");
            bullshitService.startGame(id, room.hostToken());

            // Reopen the room (drops the game, resets the lobby) then start again.
            bullshitService.playAgain(id, "Alice");
            bullshitService.joinRoom(id, "Bob");
            String hostToken = bullshitService.tokenForSeat(id, new PlayerId(0));
            bullshitService.startGame(id, hostToken);

            Bullshit game = (Bullshit) bullshitService.getGame(id);
            assertThat(game.getCurrentTarget(), is(new SuitTarget(FrenchSuit.HEART)));
        }

        @Test
        void given52CardsChosen_whenReopened_thenSameDeckSizeKept() {
            GameId id = startWithTwoPlayers(GameOptions.of(Map.of("deckSize", "52")));
            assertThat(totalCards((Bullshit) bullshitService.getGame(id)), is(52));

            bullshitService.playAgain(id, "Alice");
            bullshitService.joinRoom(id, "Bob");
            bullshitService.startGame(id, bullshitService.tokenForSeat(id, new PlayerId(0)));
            assertThat(totalCards((Bullshit) bullshitService.getGame(id)), is(52));
        }

        private int totalCards(Bullshit game) {
            return game.getPlayers().stream().mapToInt(p -> p.handSize()).sum();
        }
    }
}
