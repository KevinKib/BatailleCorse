package org.kevinkib.cardgames.president.presentation.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.PresidentFactory;
import org.kevinkib.cardgames.president.domain.bot.HeuristicPresidentBot;
import org.kevinkib.cardgames.president.domain.bot.PresidentBotDecision;
import org.kevinkib.cardgames.president.domain.bot.PresidentBotLevel;
import org.kevinkib.cardgames.president.domain.bot.PresidentBotMemory;
import org.kevinkib.cardgames.president.domain.bot.PresidentBotStrategy;
import org.kevinkib.cardgames.president.domain.bot.PresidentBotView;
import org.kevinkib.cardgames.president.domain.exchange.PendingReturn;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.presentation.PresidentGameActions;
import org.kevinkib.cardgames.president.presentation.PresidentRounds;
import org.kevinkib.cardgames.president.presentation.dto.PresidentDto;
import org.kevinkib.cardgames.presentation.bot.FixedThinkingDelay;
import org.kevinkib.cardgames.presentation.bot.ManualBotScheduler;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cards.domain.Card;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.card;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/**
 * The coordinator drives the computer seats after each broadcast, with a manual scheduler and a scripted
 * strategy. Seat 0 is the human host; unless a test says otherwise seat 1 is the computer and seat 2 a human.
 */
class PresidentBotCoordinatorTest {

    private static final Duration DELAY = Duration.ofMillis(900);
    private static final PlayerId HUMAN = new PlayerId(0);
    private static final PlayerId BOT = new PlayerId(1);
    private static final PlayerId OTHER_HUMAN = new PlayerId(2);

    private InMemorySessionRepository repository;
    private SessionService sessions;
    private RecordingMessaging messaging;
    private SeatStateBroadcaster<President, PresidentDto> broadcaster;
    private PresidentGameActions actions;
    private ManualBotScheduler scheduler;
    private PresidentBotCoordinator coordinator;
    private final List<PresidentBotLevel> levelsAsked = new ArrayList<>();
    private Scripted bot;

    /** A strategy the test scripts: what it was asked, and what it answers (the hard heuristic by default). */
    private final class Scripted implements PresidentBotStrategy {
        final List<PresidentBotView> views = new ArrayList<>();
        final List<List<Card>> playedSeen = new ArrayList<>();
        final List<Boolean> underMonitor = new ArrayList<>();
        final PresidentBotStrategy heuristic = new HeuristicPresidentBot(PresidentBotLevel.HARD.tuning(),
                new Random(1));
        President watched;
        Function<PresidentBotView, PresidentBotDecision> answer = null;

        @Override
        public PresidentBotDecision decide(PresidentBotView view, PresidentBotMemory memory) {
            views.add(view);
            playedSeen.add(memory.played());
            underMonitor.add(watched != null && Thread.holdsLock(watched));
            return answer == null ? heuristic.decide(view, memory) : answer.apply(view);
        }
    }

    @BeforeEach
    void setUp() {
        repository = new InMemorySessionRepository(Clock.systemUTC());
        sessions = new SessionService(repository, new GameFactories(List.of(new PresidentFactory())));
        messaging = new RecordingMessaging(sessions);
        broadcaster = new SeatStateBroadcaster<>(messaging, sessions::botSeats, sessions::seatNames,
                (game, viewer, names, bots) -> PresidentDto.forViewer(game, viewer, names, bots));
        actions = new PresidentGameActions(sessions, broadcaster);
        scheduler = new ManualBotScheduler();
        bot = new Scripted();
        coordinator = new PresidentBotCoordinator(sessions::botSeats, sessions::seatOptions, actions, scheduler,
                new FixedThinkingDelay(DELAY), level -> {
                    levelsAsked.add(level);
                    return bot;
                });
        broadcaster.addListener(coordinator);
    }

    /** Registers the game with the given computer seats (never seat 0); the others are humans. */
    private President register(President game, String level, int... botSeats) {
        SessionGame session = SessionGame.create(game.getId(), game.getPlayerIds(), PresidentFactory.GAME_TYPE);
        session.claimHost("Alice");
        Set<Integer> bots = new java.util.HashSet<>();
        for (int seat : botSeats) {
            bots.add(seat);
        }
        for (int seat = 1; seat < game.getPlayerIds().size(); seat++) {
            if (bots.contains(seat)) {
                session.claimBot(null);
                if (level != null) {
                    session.setSeatOption(new PlayerId(seat), "level", level);
                }
            } else {
                session.claimNextFreeSeat("Human " + seat);
            }
        }
        repository.save(game, session);
        bot.watched = game;
        return game;
    }

    private President register(President game, int... botSeats) {
        return register(game, null, botSeats);
    }

    private void start(President game) {
        broadcaster.broadcast(game, "START", new EmptyEventData(), "Game started.");
    }

    /** Seat 0 5S | seat 1 (bot) 6S 7S 9H | seat 2 3C 4C KC; the given seat leads. */
    private President table(int leader) {
        return aPresident().withHand("5S 10H").withHand("6S 7S 9H").withHand("3C 4C KC").leader(leader).build();
    }

    // ------------------------------------------------------------------ the turn

    @Test
    void aBotOnTurnPlaysItsDecisionAfterTheInjectedDelayAndOnlyTheHumansAreMessaged() {
        President game = register(table(1), 1);
        bot.answer = view -> new PresidentBotDecision.Play(cards("6S"));
        start(game);
        assertThat(scheduler.pending().size(), is(1));
        assertThat(scheduler.pending().get(0).delay(), is(DELAY));
        messaging.clear();

        scheduler.runNext();

        assertThat(game.hand(BOT), is(cards("7S 9H")));
        assertThat(game.currentSeat(), is(OTHER_HUMAN));
        assertThat(messaging.seats, contains(HUMAN, OTHER_HUMAN)); // the bot seat gets no message
    }

    @Test
    void nothingIsScheduledWhileAHumanIsOnTurn() {
        President game = register(table(0), 1);

        start(game);

        assertThat(scheduler.all(), is(empty()));
    }

    @Test
    void aBotThatPassesPasses() throws Exception {
        President game = register(table(0), 1);
        bot.answer = view -> new PresidentBotDecision.Pass();
        start(game);
        actions.play(game.getId(), HUMAN, cards("5S"));
        assertThat(scheduler.pending().size(), is(1));

        scheduler.runNext();

        assertThat(game.trick().passed(), is(List.of(BOT)));
        assertThat(game.currentSeat(), is(OTHER_HUMAN));
    }

    @Test
    void theBotSeesNothingButItsOwnViewAndItsMemoryHoldsTheCardsOfAClosedTrick() throws Exception {
        President game = register(aPresident().withHand("5S").withHand("6S 7S 9H").withHand("3C 4C KC")
                .leader(0).build(), 1);
        start(game);

        actions.play(game.getId(), HUMAN, cards("5S")); // seat 0 goes out: the first player out closes the trick
        assertThat(game.trick().plays(), is(empty()));
        scheduler.runNext();

        assertThat(bot.views.get(0).seat(), is(BOT));
        assertThat(bot.views.get(0).hand(), is(cards("6S 7S 9H")));
        assertThat(bot.playedSeen.get(0), hasItem(card("5S"))); // seen played, though the trick is gone
    }

    // ------------------------------------------------------------------ stale decisions

    @Test
    void theDecisionIsTakenAndAppliedUnderTheMonitorOfTheGameSoItIsNeverStale() {
        President game = register(table(1), 1);
        start(game);

        scheduler.runNext();

        assertThat(bot.underMonitor, contains(true));
    }

    @Test
    void aTaskIsDroppedWhenTheStateMovedWithoutABroadcast() throws Exception {
        President game = register(table(1), 1);
        start(game);

        game.play(BOT, cards("6S")); // the version moves, nobody broadcast
        scheduler.runAllPending();

        assertThat(bot.views, is(empty())); // the stale bot did not even think
    }

    @Test
    void theNextBroadcastCancelsThePreviousTurnTasks() throws Exception {
        President game = register(table(1), 1);
        start(game);
        ManualBotScheduler.Entry first = scheduler.pending().get(0);

        actions.play(game.getId(), BOT, cards("6S")); // a state change by another path: broadcast

        assertThat(first.isCancelled(), is(true));
        assertThat(scheduler.pending(), is(empty())); // seat 2, a human, is on turn
    }

    @Test
    void aStateBroadcastThatChangesNothingDoesNotRestartTheThinking() {
        President game = register(table(1), 1);
        start(game);

        broadcaster.broadcast(game, "CONNECTED", new EmptyEventData(), "Alice is back.");

        assertThat(scheduler.pending().size(), is(1));
        assertThat(scheduler.cancelledCount(), is(0L));
    }

    // ------------------------------------------------------------------ fallback

    @Test
    void aThrowingStrategyLeadsItsLowestSet() {
        President game = register(table(1), 1);
        bot.answer = view -> {
            throw new IllegalStateException("boom");
        };
        start(game);

        scheduler.runNext();

        assertThat(game.hand(BOT), is(cards("7S 9H")));
        assertThat(game.trick().plays().size(), is(1));
        assertThat(game.trick().plays().get(0).play().cards(), is(cards("6S")));
    }

    @Test
    void aThrowingStrategyPassesWhenFollowing() throws Exception {
        President game = register(table(0), 1);
        bot.answer = view -> {
            throw new IllegalStateException("boom");
        };
        start(game);
        actions.play(game.getId(), HUMAN, cards("5S"));

        scheduler.runNext();

        assertThat(game.trick().passed(), is(List.of(BOT)));
    }

    @Test
    void anIllegalDecisionFallsBackAsWell() {
        President game = register(table(1), 1);
        bot.answer = view -> new PresidentBotDecision.Play(cards("AH")); // not in the hand
        start(game);

        scheduler.runNext();

        assertThat(game.trick().plays().get(0).play().cards(), is(cards("6S")));
    }

    @Test
    void aNonsenseKindOfDecisionFallsBackToo() {
        President game = register(table(1), 1);
        bot.answer = view -> new PresidentBotDecision.Ready(); // not a thing to do on a turn
        start(game);

        scheduler.runNext();

        assertThat(game.trick().plays().get(0).play().cards(), is(cards("6S")));
    }

    // ------------------------------------------------------------------ exchange and barrier

    /** A real match advanced to the exchange of round 2 whose first return is owed by the given seat. */
    private President matchInExchangeOwedBy(int seat) throws Exception {
        for (long seed = 1; seed < 500; seed++) {
            President game = new President(GameId.generate(), 3, seed);
            PresidentRounds.playRoundOut(game);
            PresidentRounds.readyAll(game);
            if (game.phase() == Phase.EXCHANGE
                    && game.pendingReturns().stream().anyMatch(p -> p.high().id() == seat)) {
                return game;
            }
        }
        throw new AssertionError("no seed gives an exchange owed by seat " + seat);
    }

    @Test
    void aBotOwingCardsGivesThemBackAfterTheDelay() throws Exception {
        President game = register(matchInExchangeOwedBy(1), 1);
        PendingReturn owed = game.pendingReturns().stream().filter(p -> p.high().equals(BOT)).findFirst()
                .orElseThrow();
        List<Card> lowest = game.hand(BOT).stream().limit(owed.count()).toList();
        bot.answer = view -> new PresidentBotDecision.GiveBack(lowest);
        start(game);
        assertThat(scheduler.pending().size(), is(1));
        assertThat(scheduler.pending().get(0).delay(), is(DELAY));

        scheduler.runNext();

        assertThat(game.pendingReturns().stream().noneMatch(p -> p.high().equals(BOT)), is(true));
        assertThat(game.given(BOT), is(lowest));
    }

    @Test
    void aThrowingStrategyGivesBackItsLowestCardsInTheExchange() throws Exception {
        President game = register(matchInExchangeOwedBy(1), 1);
        bot.answer = view -> {
            throw new IllegalStateException("boom");
        };
        start(game);

        scheduler.runNext();

        assertThat(game.pendingReturns().stream().noneMatch(p -> p.high().equals(BOT)), is(true));
    }

    @Test
    void aBotReadiesAtTheBarrierAfterTheDelayAndTheLastReadyDealsTheNextRound() throws Exception {
        President game = register(aPresident().withHand("5S").withHand("6S 7S").withHand("3C 4C").leader(0).build(),
                1);
        PresidentRounds.playRoundOut(game);
        assertThat(game.phase(), is(Phase.ROUND_OVER));
        start(game);
        assertThat(scheduler.pending().size(), is(1));
        assertThat(scheduler.pending().get(0).delay(), is(DELAY));

        scheduler.runNext();
        assertThat(game.readySeats(), is(Set.of(BOT)));
        actions.ready(game.getId(), HUMAN);
        actions.ready(game.getId(), OTHER_HUMAN);

        assertThat(game.round(), is(2));
        assertThat(game.phase() == Phase.EXCHANGE || game.phase() == Phase.PLAYING, is(true));
    }

    @Test
    void aBotsReadyDoesNotRestartTheOtherBotsThinkingAtTheBarrier() throws Exception {
        President game = register(aPresident().withHand("5S").withHand("6S 7S").withHand("3C 4C").leader(0).build(),
                1, 2);
        PresidentRounds.playRoundOut(game);
        start(game);
        assertThat(scheduler.pending().size(), is(2));
        ManualBotScheduler.Entry second = scheduler.pending().get(1);

        scheduler.pending().get(0).run();

        assertThat(second.isCancelled(), is(false));
        assertThat(scheduler.pending(), is(List.of(second)));
        second.run();
        assertThat(game.readySeats(), is(Set.of(BOT, OTHER_HUMAN)));
    }

    // ------------------------------------------------------------------ lifecycle

    @Test
    void theLevelIsReadFromTheSeatOptionAndDefaultsToMedium() {
        President hard = register(table(1), "hard", 1);
        start(hard);
        scheduler.runNext();

        President plain = register(table(1), 1);
        start(plain);
        scheduler.runNext();

        assertThat(levelsAsked, is(List.of(PresidentBotLevel.HARD, PresidentBotLevel.MEDIUM)));
    }

    @Test
    void nothingIsScheduledOnceTheMatchIsOverAndTheTableIsForgotten() throws Exception {
        President game = register(table(1), 1);
        start(game);
        ManualBotScheduler.Entry pending = scheduler.pending().get(0);

        game.forfeit(HUMAN);
        game.forfeit(OTHER_HUMAN); // a single seat left: the match is over
        broadcaster.broadcast(game, "PLAYER_FORFEIT", new EmptyEventData(), "Left.");

        assertThat(game.isFinished(), is(true));
        assertThat(pending.isCancelled(), is(true));
        assertThat(scheduler.pending(), is(empty()));
        assertThat(coordinator.trackedGameCount(), is(0));
    }

    @Test
    void aTaskThatFiresAfterTheMatchEndedWithoutABroadcastDoesNothing() {
        President game = register(table(1), 1);
        start(game);

        game.forfeit(HUMAN);
        game.forfeit(OTHER_HUMAN);
        scheduler.runAllPending();

        assertThat(bot.views, is(empty()));
    }

    @Test
    void whenNoHumanIsLeftTheTableStops() {
        President game = register(aPresident().withHand("5S 10H").withHand("6S 7S 9H").withHand("3C 4C KC")
                .withHand("8C 9C JC").leader(1).build(), 1, 2, 3);
        start(game);
        ManualBotScheduler.Entry pending = scheduler.pending().get(0);
        assertThat(coordinator.trackedGameCount(), is(1));

        game.forfeit(HUMAN); // only computer seats are left in the match
        broadcaster.broadcast(game, "PLAYER_FORFEIT", new EmptyEventData(), "Alice left.");

        assertThat(game.isFinished(), is(false));
        assertThat(pending.isCancelled(), is(true));
        assertThat(scheduler.pending(), is(empty()));
        assertThat(coordinator.trackedGameCount(), is(0));
    }

    @Test
    void aGameWithoutBotsIsNeverTracked() {
        President game = table(1);
        SessionGame session = SessionGame.create(game.getId(), game.getPlayerIds(), PresidentFactory.GAME_TYPE);
        session.claimHost("Alice");
        session.claimNextFreeSeat("Bob");
        session.claimNextFreeSeat("Carl");
        repository.save(game, session);

        start(game);

        assertThat(coordinator.trackedGameCount(), is(0));
        assertThat(scheduler.all(), is(empty()));
    }

    @Test
    void anEvictedGameIsForgottenAndItsTasksCancelled() {
        President game = register(table(1), 1);
        start(game);
        ManualBotScheduler.Entry pending = scheduler.pending().get(0);

        coordinator.onEvicted(game.getId());

        assertThat(pending.isCancelled(), is(true));
        assertThat(coordinator.trackedGameCount(), is(0));
    }

    @Test
    void aNewGameUnderTheSameIdAfterPlayAgainGetsItsOwnTable() {
        GameId id = GameId.generate();
        President first = register(aPresident().withId(id).withHand("5S 10H").withHand("6S 7S 9H")
                .withHand("3C 4C KC").leader(1).build(), 1);
        start(first);
        ManualBotScheduler.Entry stale = scheduler.pending().get(0);

        President second = register(aPresident().withId(id).withHand("5S 10H").withHand("6S 7S 9H")
                .withHand("3C 4C KC").leader(1).build(), 1);
        start(second);

        assertThat(stale.isCancelled(), is(true));
        assertThat(scheduler.pending().size(), is(1));
        scheduler.runNext();
        assertThat(second.hand(BOT).size(), is(2));
        assertThat(first.hand(BOT).size(), is(3));
        assertThat(coordinator.trackedGameCount(), is(1));
    }

    @Test
    void aBotsMoveIsBroadcastToTheHumansThroughTheSameUseCasesAsAHumans() {
        President game = register(table(1), 1);
        start(game);
        messaging.clear();

        scheduler.runNext();

        assertThat(messaging.payloads.size(), is(2)); // one frame per human seat
        assertThat(messaging.payloads.get(0).getEventType(), is("PLAY"));
    }
}
