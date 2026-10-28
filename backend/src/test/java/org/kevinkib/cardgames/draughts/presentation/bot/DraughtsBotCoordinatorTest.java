package org.kevinkib.cardgames.draughts.presentation.bot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.DraughtsFactory;
import org.kevinkib.cardgames.draughts.domain.DraughtsMove;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;
import org.kevinkib.cardgames.draughts.domain.DraughtsResult;
import org.kevinkib.cardgames.draughts.domain.bot.DraughtsBot;
import org.kevinkib.cardgames.draughts.domain.bot.DraughtsBotLevel;
import org.kevinkib.cardgames.draughts.domain.bot.DraughtsDecision;
import org.kevinkib.cardgames.draughts.presentation.DraughtsGameActions;
import org.kevinkib.cardgames.draughts.presentation.dto.DraughtsDto;
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

/**
 * The coordinator drives the computer seat after each broadcast, with a manual scheduler and a scripted strategy.
 * Seat 0 is the human host (White, who moves first), seat 1 the computer (Black).
 */
class DraughtsBotCoordinatorTest {

    private static final Duration DELAY = Duration.ofMillis(900);
    private static final PlayerId HUMAN = new PlayerId(0);
    private static final PlayerId BOT = new PlayerId(1);

    private InMemorySessionRepository repository;
    private SessionService sessions;
    private RecordingMessaging messaging;
    private SeatStateBroadcaster<Draughts, DraughtsDto> broadcaster;
    private DraughtsGameActions actions;
    private ManualBotScheduler scheduler;
    private DraughtsBotCoordinator coordinator;
    private final List<DraughtsBotLevel> levelsAsked = new ArrayList<>();
    private Scripted bot;

    /** A strategy the test scripts: what it was asked, whether it held the game monitor, and its answer. */
    private final class Scripted implements DraughtsBot {
        final List<Boolean> offerPending = new ArrayList<>();
        final List<Colour> colours = new ArrayList<>();
        final List<org.kevinkib.cardgames.draughts.domain.bot.DraughtsDrawContext> contexts = new ArrayList<>();
        final List<Boolean> underMonitor = new ArrayList<>();
        Draughts watched;
        Function<DraughtsPosition, DraughtsDecision> answer = position -> new DraughtsDecision.Play(
                new org.kevinkib.cardgames.draughts.domain.MoveGenerator().legalMoves(position).get(0));

        @Override
        public DraughtsDecision decide(DraughtsPosition position, Colour me, boolean drawOfferPending,
                                       org.kevinkib.cardgames.draughts.domain.bot.DraughtsDrawContext drawContext) {
            contexts.add(drawContext);
            return decide(position, me, drawOfferPending);
        }

        @Override
        public DraughtsDecision decide(DraughtsPosition position, Colour me, boolean drawOfferPending) {
            offerPending.add(drawOfferPending);
            colours.add(me);
            underMonitor.add(watched != null && Thread.holdsLock(watched));
            return answer.apply(position);
        }
    }

    @BeforeEach
    void setUp() {
        repository = new InMemorySessionRepository(Clock.systemUTC());
        sessions = new SessionService(repository,
                new GameFactories(List.of(new DraughtsFactory(), new BullshitFactory())));
        messaging = new RecordingMessaging(sessions);
        broadcaster = new SeatStateBroadcaster<>(messaging, sessions::botSeats, sessions::seatNames,
                (game, viewer, names, bots) -> DraughtsDto.forViewer(game, viewer, names));
        actions = new DraughtsGameActions(sessions, broadcaster);
        scheduler = new ManualBotScheduler();
        bot = new Scripted();
        coordinator = new DraughtsBotCoordinator(sessions::botSeats, sessions::seatOptions, actions, scheduler,
                new FixedThinkingDelay(DELAY), level -> {
                    levelsAsked.add(level);
                    return bot;
                });
        broadcaster.addListener(coordinator);
    }

    private Draughts register(String level) {
        Draughts game = new Draughts(GameId.generate(), HUMAN);
        SessionGame session = SessionGame.create(game.getId(), game.getPlayerIds(), DraughtsFactory.GAME_TYPE);
        session.claimHost("Alice");
        session.claimBot(null);
        if (level != null) {
            session.setSeatOption(BOT, DraughtsFactory.LEVEL_KEY, level);
        }
        repository.save(game, session);
        bot.watched = game;
        return game;
    }

    private Draughts register() {
        return register(null);
    }

    private void start(Draughts game) {
        broadcaster.broadcast(game, "START", new EmptyEventData(), "Game started.");
    }

    /** A started game where the human has played 32-28, so the computer is on turn with one task waiting. */
    private Draughts botOnTurn() throws Exception {
        Draughts game = register();
        start(game);
        actions.move(game.getId(), HUMAN, 32, 28, List.of());
        return game;
    }

    @Test
    void aBotOnTurnMovesAfterTheInjectedDelayAndOnlyTheHumanIsMessaged() throws Exception {
        Draughts game = register();
        start(game);
        assertThat(scheduler.all()).isEmpty(); // the human is on turn
        actions.move(game.getId(), HUMAN, 32, 28, List.of());
        assertThat(scheduler.pending()).hasSize(1);
        assertThat(scheduler.pending().get(0).delay()).isEqualTo(DELAY);
        messaging.clear();

        scheduler.runNext();

        assertThat(game.history()).hasSize(2);
        assertThat(game.lastMove().seat()).isEqualTo(BOT);
        assertThat(game.currentSeat()).isEqualTo(HUMAN);
        assertThat(messaging.seats).contains(HUMAN).doesNotContain(BOT);
    }

    @Test
    void theStrategyIsAskedForItsOwnColourWithoutAPendingOfferAndWithoutHoldingTheGameMonitor() throws Exception {
        Draughts game = botOnTurn();

        scheduler.runNext();

        assertThat(bot.colours).containsExactly(Colour.BLACK);
        assertThat(bot.offerPending).containsExactly(false);
        assertThat(bot.underMonitor).containsExactly(false);
    }

    @Test
    void theLevelComesFromTheSeatOptionAndDefaultsToTheDefaultLevel() throws Exception {
        Draughts expert = register("expert");
        start(expert);
        actions.move(expert.getId(), HUMAN, 32, 28, List.of());
        scheduler.runNext();
        Draughts unset = botOnTurn();
        scheduler.runNext();

        assertThat(levelsAsked).containsExactly(DraughtsBotLevel.EXPERT, DraughtsBotLevel.DEFAULT);
        assertThat(unset.history()).hasSize(2);
    }

    @Test
    void aHumanActionBetweenTheSnapshotAndTheDecisionDropsTheAnswer() throws Exception {
        Draughts game = botOnTurn();
        bot.answer = position -> {
            try {
                game.offerDraw(HUMAN); // moves the version while the bot thinks, without a broadcast
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            return new DraughtsDecision.Play(new org.kevinkib.cardgames.draughts.domain.MoveGenerator()
                    .legalMoves(position).get(0));
        };

        scheduler.runNext();

        assertThat(game.history()).hasSize(1);
        assertThat(game.currentSeat()).isEqualTo(BOT);
    }

    @Test
    void aTaskIsDroppedWhenTheStateMovedOnWhileItWasWaiting() throws Exception {
        Draughts game = botOnTurn();
        ManualBotScheduler.Entry task = scheduler.pending().get(0);
        game.offerDraw(HUMAN); // moves the version without a broadcast

        task.run();

        assertThat(bot.colours).isEmpty();
        assertThat(game.history()).hasSize(1);
    }

    @Test
    void aBroadcastThatChangesNothingKeepsTheWaitingTask() throws Exception {
        Draughts game = botOnTurn();

        broadcaster.broadcast(game, "OPPONENT_DISCONNECTED", new EmptyEventData(), "");

        assertThat(scheduler.pending()).hasSize(1);
        assertThat(scheduler.cancelledCount()).isZero();
    }

    @Test
    void aFailingStrategyFallsBackToTheFirstLegalMove() throws Exception {
        Draughts game = register();
        bot.answer = position -> {
            throw new IllegalStateException("boom");
        };
        start(game);
        actions.move(game.getId(), HUMAN, 32, 28, List.of());
        DraughtsMove first = game.legalMoves(BOT).get(0);

        scheduler.runNext();

        assertThat(game.history()).containsExactly("32-28", first.notation());
        assertThat(game.currentSeat()).isEqualTo(HUMAN);
    }

    @Test
    void anIllegalDecisionFallsBackToALegalMove() throws Exception {
        Draughts game = botOnTurn();
        DraughtsMove first = game.legalMoves(BOT).get(0);
        bot.answer = position -> new DraughtsDecision.Play(DraughtsMove.plain(1, 2)); // not a legal move

        scheduler.runNext();

        assertThat(game.history()).containsExactly("32-28", first.notation());
    }

    @Test
    void aPendingOfferIsAnsweredAndAnAcceptedDrawEndsTheGame() throws Exception {
        Draughts game = register();
        start(game);
        bot.answer = position -> new DraughtsDecision.AcceptDraw();

        actions.offerDraw(game.getId(), HUMAN);
        assertThat(scheduler.pending()).hasSize(1);
        scheduler.runNext();

        assertThat(bot.offerPending).containsExactly(true);
        assertThat(game.isFinished()).isTrue();
        assertThat(game.result().reason()).isEqualTo(DraughtsResult.Reason.DRAW_AGREED);
    }

    @Test
    void theBotIsToldHowManyPliesWerePlayedAndWhetherACounterRuns() throws Exception {
        Draughts game = register();
        start(game);

        actions.offerDraw(game.getId(), HUMAN);
        scheduler.runNext();

        assertThat(bot.contexts).containsExactly(
                new org.kevinkib.cardgames.draughts.domain.bot.DraughtsDrawContext(game.history().size(), false));
    }

    @Test
    void aDeclinedOfferLeavesTheBotToMoveWhenItIsItsTurn() throws Exception {
        Draughts game = botOnTurn();
        bot.answer = position -> new DraughtsDecision.DeclineDraw();

        actions.offerDraw(game.getId(), HUMAN); // cancels the move task, schedules the answer
        assertThat(scheduler.pending()).hasSize(1);
        scheduler.runNext();

        assertThat(game.drawOffer()).isNull();
        assertThat(game.history()).hasSize(1);
        assertThat(scheduler.pending()).hasSize(1); // the offer is gone: now the move
        bot.answer = position -> new DraughtsDecision.Play(new org.kevinkib.cardgames.draughts.domain.MoveGenerator()
                .legalMoves(position).get(0));
        scheduler.runNext();

        assertThat(bot.offerPending).containsExactly(true, false);
        assertThat(game.history()).hasSize(2);
    }

    @Test
    void aFailingStrategyDeclinesAPendingOffer() throws Exception {
        Draughts game = register();
        start(game);
        bot.answer = position -> {
            throw new IllegalStateException("boom");
        };

        actions.offerDraw(game.getId(), HUMAN);
        scheduler.runNext();

        assertThat(game.isFinished()).isFalse();
        assertThat(game.drawOffer()).isNull();
    }

    @Test
    void aMoveDecisionWhileAnOfferIsPendingFallsBackToDeclining() throws Exception {
        Draughts game = register();
        start(game);

        actions.offerDraw(game.getId(), HUMAN); // the default script answers with a move
        scheduler.runNext();

        assertThat(game.isFinished()).isFalse();
        assertThat(game.drawOffer()).isNull();
        assertThat(game.history()).isEmpty();
    }

    @Test
    void nothingIsScheduledOnceTheGameIsOver() throws Exception {
        Draughts game = register();
        game.forfeit(HUMAN);

        start(game);

        assertThat(scheduler.pending()).isEmpty();
    }

    @Test
    void anEvictedGameDropsItsTable() throws Exception {
        Draughts game = botOnTurn();
        ManualBotScheduler.Entry task = scheduler.pending().get(0);

        coordinator.onEvicted(game.getId());

        assertThat(task.isCancelled()).isTrue();
    }

    @Test
    void aGameWithoutABotIsNotTracked() throws Exception {
        Draughts game = new Draughts(GameId.generate(), HUMAN);
        SessionGame session = SessionGame.create(game.getId(), game.getPlayerIds(), DraughtsFactory.GAME_TYPE);
        session.claimHost("Alice");
        session.claimNextFreeSeat("Bob");
        repository.save(game, session);

        start(game);
        actions.move(game.getId(), HUMAN, 32, 28, List.of());

        assertThat(scheduler.all()).isEmpty();
    }
}
