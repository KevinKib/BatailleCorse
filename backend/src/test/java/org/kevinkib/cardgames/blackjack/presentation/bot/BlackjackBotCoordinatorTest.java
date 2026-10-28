package org.kevinkib.cardgames.blackjack.presentation.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.Action;
import org.kevinkib.cardgames.blackjack.domain.Blackjack;
import org.kevinkib.cardgames.blackjack.domain.BlackjackFactory;
import org.kevinkib.cardgames.blackjack.domain.BlackjackOptions;
import org.kevinkib.cardgames.blackjack.domain.Phase;
import org.kevinkib.cardgames.blackjack.domain.RandomShoeSource;
import org.kevinkib.cardgames.blackjack.domain.ScriptedShoeSource;
import org.kevinkib.cardgames.blackjack.domain.bot.BlackjackBotLevel;
import org.kevinkib.cardgames.blackjack.domain.bot.BlackjackBotStrategy;
import org.kevinkib.cardgames.blackjack.domain.bot.BlackjackBotView;
import org.kevinkib.cardgames.blackjack.presentation.BlackjackGameActions;
import org.kevinkib.cardgames.blackjack.presentation.dto.BlackjackDto;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.bot.FixedThinkingDelay;
import org.kevinkib.cardgames.presentation.bot.ManualBotScheduler;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.blackjack.presentation.BlackjackTable.cards;

/**
 * The coordinator drives the computer seats after each broadcast, with a manual scheduler and a scripted strategy.
 * Seat 0 is the human host, seats 1 and 2 the computers, unless a test says otherwise.
 */
class BlackjackBotCoordinatorTest {

    private static final Duration DELAY = Duration.ofMillis(900);
    private static final PlayerId HUMAN = new PlayerId(0);
    private static final PlayerId BOT = new PlayerId(1);
    private static final PlayerId BOT_2 = new PlayerId(2);
    /** Seat 0 10S 7C (17), seat 1 9H 8C (17), seat 2 8D 9C (17); dealer 6D over 5H, then 4S 3H. */
    private static final String DEAL = "10S 9H 8D 6D 7C 8C 9C 5H 4S 3H";

    private final ScriptedShoeSource shoes = new ScriptedShoeSource(new RandomShoeSource());
    private InMemorySessionRepository repository;
    private SessionService sessions;
    private SeatStateBroadcaster<Blackjack, BlackjackDto> broadcaster;
    private BlackjackGameActions actions;
    private ManualBotScheduler scheduler;
    private BlackjackBotCoordinator coordinator;
    private final List<BlackjackBotLevel> levelsAsked = new ArrayList<>();
    private Scripted bot;
    private boolean botSeatsFail;

    /** A strategy the test scripts: what it was asked, whether it held the game monitor, and its answers. */
    private final class Scripted implements BlackjackBotStrategy {
        final List<BlackjackBotView> betViews = new ArrayList<>();
        final List<BlackjackBotView> playViews = new ArrayList<>();
        final List<Boolean> underMonitor = new ArrayList<>();
        Blackjack watched;
        Function<BlackjackBotView, Integer> bet = view -> 10;
        Function<BlackjackBotView, Action> play = view -> Action.STAND;

        @Override
        public int decideBet(BlackjackBotView view) {
            betViews.add(view);
            underMonitor.add(watched != null && Thread.holdsLock(watched));
            return bet.apply(view);
        }

        @Override
        public Action decidePlay(BlackjackBotView view) {
            playViews.add(view);
            underMonitor.add(watched != null && Thread.holdsLock(watched));
            return play.apply(view);
        }
    }

    @BeforeEach
    void setUp() {
        repository = new InMemorySessionRepository(Clock.systemUTC());
        sessions = new SessionService(repository,
                new GameFactories(List.of(new BlackjackFactory(shoes), new BullshitFactory())));
        RecordingMessaging messaging = new RecordingMessaging(sessions);
        broadcaster = new SeatStateBroadcaster<>(messaging, sessions::botSeats, sessions::seatNames,
                (game, viewer, names, bots) -> BlackjackDto.forViewer(game, viewer, names, bots, sessions::seatOptions));
        actions = new BlackjackGameActions(sessions, broadcaster);
        scheduler = new ManualBotScheduler();
        bot = new Scripted();
        coordinator = new BlackjackBotCoordinator(id -> {
                    if (botSeatsFail) {
                        throw new IllegalStateException("lookup failed");
                    }
                    return sessions.botSeats(id);
                }, sessions::seatOptions, actions, scheduler,
                new FixedThinkingDelay(DELAY), level -> {
                    levelsAsked.add(level);
                    return bot;
                });
        broadcaster.addListener(coordinator);
    }

    private Blackjack register(String deal, String level, int... botSeats) {
        GameId id = GameId.generate();
        shoes.fix(id, 1, cards(deal));
        int seats = 3;
        Blackjack game = new Blackjack(id, seats, shoes, BlackjackOptions.defaults());
        SessionGame session = SessionGame.create(game.getId(), game.getPlayerIds(), BlackjackFactory.GAME_TYPE);
        session.claimHost("Alice");
        Set<Integer> bots = new HashSet<>();
        for (int seat : botSeats) {
            bots.add(seat);
        }
        for (int seat = 1; seat < seats; seat++) {
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

    private Blackjack register(int... botSeats) {
        return register(DEAL, null, botSeats);
    }

    private void start(Blackjack game) {
        broadcaster.broadcast(game, "START", new EmptyEventData(), "Game started.");
    }

    @Test
    void theStartSchedulesABetForEveryBotThatHasNotBetAfterTheInjectedDelay() {
        Blackjack game = register(1, 2);

        start(game);

        assertThat(scheduler.pending()).hasSize(2);
        assertThat(scheduler.pending().get(0).delay()).isEqualTo(DELAY);
        assertThat(game.phase()).isEqualTo(Phase.BETTING);
    }

    @Test
    void aBotBetsItsDecisionAndTheOtherBotsTaskSurvivesTheBroadcastOfThatBet() throws Exception {
        Blackjack game = register(1, 2);
        start(game);
        bot.bet = view -> view.seat().equals(BOT) ? 12 : 7;

        scheduler.runNext();

        assertThat(game.seat(BOT).betPlaced()).isTrue();
        assertThat(game.seat(BOT).bet()).isEqualTo(12);
        assertThat(game.seat(BOT_2).betPlaced()).isFalse();
        assertThat(scheduler.pending()).hasSize(1);
        assertThat(scheduler.cancelledCount()).isZero();

        scheduler.runNext();
        assertThat(game.seat(BOT_2).bet()).isEqualTo(7);
    }

    @Test
    void aHumanBetDoesNotDisturbTheTasksOfTheBots() throws Exception {
        Blackjack game = register(1, 2);
        start(game);
        actions.bet(game.getId(), HUMAN, 10);

        assertThat(scheduler.pending()).hasSize(2);
        scheduler.runNext();
        scheduler.runNext();

        assertThat(game.phase()).isEqualTo(Phase.PLAYING);
        assertThat(game.seat(BOT).bet()).isEqualTo(10);
        assertThat(game.seat(BOT_2).bet()).isEqualTo(10);
    }

    @Test
    void theStrategyReceivesTheViewOfItsOwnSeatAndDecidesWithoutHoldingTheGameMonitor() {
        Blackjack game = register(1);
        start(game);

        scheduler.runNext();

        assertThat(bot.betViews).hasSize(1);
        assertThat(bot.betViews.get(0).seat()).isEqualTo(BOT);
        assertThat(bot.betViews.get(0).maxBet()).isEqualTo(50);
        assertThat(bot.underMonitor).containsExactly(false);
    }

    @Test
    void theBotOnTurnPlaysItsDecisionAfterTheDeal() throws Exception {
        Blackjack game = register(1);
        start(game);
        actions.bet(game.getId(), HUMAN, 10);
        actions.bet(game.getId(), new PlayerId(2), 10);
        scheduler.runNext(); // the bot's bet
        assertThat(game.phase()).isEqualTo(Phase.PLAYING);
        assertThat(game.currentSeat()).isEqualTo(HUMAN);
        assertThat(scheduler.pending()).isEmpty(); // a human is on turn

        actions.stand(game.getId(), HUMAN);
        assertThat(scheduler.pending()).hasSize(1);
        bot.play = view -> Action.HIT;
        scheduler.runNext();

        assertThat(bot.playViews).hasSize(1);
        assertThat(bot.playViews.get(0).hands().get(0).total()).isEqualTo(17);
        assertThat(game.seat(BOT).hands().get(0).cards()).hasSize(3);
    }

    @Test
    void theLevelComesFromTheSeatOptionAndDefaultsToMedium() {
        Blackjack hard = register(DEAL, "hard", 1);
        start(hard);
        scheduler.runNext();
        Blackjack unset = register(1);
        start(unset);
        scheduler.runNext();

        assertThat(levelsAsked).containsExactly(BlackjackBotLevel.HARD, BlackjackBotLevel.MEDIUM);
    }

    @Test
    void aTaskIsDroppedWhenTheStateMovedOnWhileItWasWaiting() throws Exception {
        Blackjack game = register(1);
        start(game);
        actions.bet(game.getId(), HUMAN, 10);
        actions.bet(game.getId(), new PlayerId(2), 10);
        ManualBotScheduler.Entry betTask = scheduler.pending().get(0);
        game.forfeit(new PlayerId(2)); // moves the version without a broadcast

        betTask.run();
        assertThat(game.seat(BOT).betPlaced()).isTrue(); // a bet is still welcome: the hand is still betting
        actions.stand(game.getId(), HUMAN);
        ManualBotScheduler.Entry playTask = scheduler.pending().get(0);
        game.forfeit(HUMAN);

        playTask.run();

        assertThat(bot.playViews).isEmpty();
    }

    @Test
    void aFailingBotLookupKeepsTheWaitingTaskInsteadOfCancellingIt() {
        Blackjack game = register(1);
        start(game);
        ManualBotScheduler.Entry task = scheduler.pending().get(0);

        botSeatsFail = true;
        broadcaster.broadcast(game, "OPPONENT_DISCONNECTED", new EmptyEventData(), "");

        assertThat(task.isCancelled()).isFalse();
        botSeatsFail = false;
        task.run();
        assertThat(game.seat(BOT).betPlaced()).isTrue();
    }

    @Test
    void aBroadcastThatChangesNothingKeepsTheWaitingTasks() {
        Blackjack game = register(1, 2);
        start(game);

        broadcaster.broadcast(game, "OPPONENT_DISCONNECTED", new EmptyEventData(), "");

        assertThat(scheduler.pending()).hasSize(2);
        assertThat(scheduler.cancelledCount()).isZero();
    }

    @Test
    void aFailingStrategyFallsBackToABetOfOneAndToStand() throws Exception {
        Blackjack game = register(1);
        bot.bet = view -> {
            throw new IllegalStateException("boom");
        };
        bot.play = view -> {
            throw new IllegalStateException("boom");
        };
        start(game);
        scheduler.runNext();
        assertThat(game.seat(BOT).bet()).isEqualTo(1);
        actions.bet(game.getId(), HUMAN, 10);
        actions.bet(game.getId(), new PlayerId(2), 10);
        actions.stand(game.getId(), HUMAN);

        scheduler.runNext();

        assertThat(game.seat(BOT).hands().get(0).status().name()).isEqualTo("STOOD");
        assertThat(game.currentSeat()).isEqualTo(BOT_2);
    }

    @Test
    void anIllegalDecisionFallsBackToALegalOne() throws Exception {
        Blackjack game = register(1);
        bot.bet = view -> 5000;
        bot.play = view -> Action.SPLIT; // not available on 10-value plus 8
        start(game);
        scheduler.runNext();
        assertThat(game.seat(BOT).bet()).isEqualTo(1);
        actions.bet(game.getId(), HUMAN, 10);
        actions.bet(game.getId(), new PlayerId(2), 10);
        actions.stand(game.getId(), HUMAN);

        scheduler.runNext();

        assertThat(game.currentSeat()).isEqualTo(BOT_2);
    }

    @Test
    void nothingIsScheduledOnceTheGameIsOver() {
        Blackjack game = register(1);
        game.forfeit(HUMAN);
        game.forfeit(BOT_2);
        start(game);

        assertThat(scheduler.pending()).isEmpty();
    }

    @Test
    void anEvictedGameDropsItsTable() {
        Blackjack game = register(1);
        start(game);
        ManualBotScheduler.Entry task = scheduler.pending().get(0);

        coordinator.onEvicted(game.getId());

        assertThat(task.isCancelled()).isTrue();
    }
}
