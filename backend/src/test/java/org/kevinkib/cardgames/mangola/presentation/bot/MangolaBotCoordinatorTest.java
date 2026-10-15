package org.kevinkib.cardgames.mangola.presentation.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaBoard;
import org.kevinkib.cardgames.mangola.domain.MangolaFactory;
import org.kevinkib.cardgames.mangola.domain.MangolaGeometry;
import org.kevinkib.cardgames.mangola.domain.MangolaPosition;
import org.kevinkib.cardgames.mangola.domain.MangolaResult;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.Phase;
import org.kevinkib.cardgames.mangola.domain.Side;
import org.kevinkib.cardgames.mangola.domain.bot.MangolaBot;
import org.kevinkib.cardgames.mangola.domain.bot.MangolaBotLevel;
import org.kevinkib.cardgames.mangola.presentation.MangolaGameActions;
import org.kevinkib.cardgames.mangola.presentation.dto.MangolaDto;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * The coordinator drives the bot seat after each broadcast, with a manual scheduler and a scripted strategy:
 * seat 0 is the human host, seat 1 the computer.
 */
class MangolaBotCoordinatorTest {

    private static final Duration DELAY = Duration.ofMillis(900);
    private static final PlayerId HUMAN = new PlayerId(0);
    private static final PlayerId BOT = new PlayerId(1);

    /** Side A: 24 seeds on a2 and singletons; stuck against {@link #FACING_A} (see MangolaStuckStartTest). */
    private static final int[] STUCK_A = {24, 1, 0, 1, 0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 1, 1};
    private static final int[] FACING_A = {2, 1, 1, 1, 1, 2, 3, 0, 3, 0, 1, 5, 2, 2, 3, 5};

    private InMemorySessionRepository repository;
    private SessionService sessions;
    private RecordingMessaging messaging;
    private SeatStateBroadcaster<Mangola, MangolaDto> broadcaster;
    private MangolaGameActions actions;
    private ManualBotScheduler scheduler;
    private MangolaBotCoordinator coordinator;
    private final List<MangolaBotLevel> levelsAsked = new ArrayList<>();
    private Scripted bot;

    /** A strategy the test scripts: what it was asked, and what it answers. */
    private final class Scripted implements MangolaBot {
        final List<List<List<Integer>>> rejectedSeen = new ArrayList<>();
        final List<Boolean> decidedUnderMonitor = new ArrayList<>();
        final List<Integer> movesAsked = new ArrayList<>();
        Mangola watched;
        Supplier<int[]> layout = () -> twoPerPit();
        Supplier<Integer> move = () -> 0;
        Runnable duringDecision = () -> { };

        @Override
        public int[] decideLayout(MangolaRules rules, Side side, Collection<List<Integer>> rejected) {
            rejectedSeen.add(new ArrayList<>(rejected));
            decidedUnderMonitor.add(watched != null && Thread.holdsLock(watched));
            duringDecision.run();
            return layout.get();
        }

        @Override
        public int decideMove(MangolaPosition position, Side side) {
            movesAsked.add(position.toMove());
            decidedUnderMonitor.add(watched != null && Thread.holdsLock(watched));
            duringDecision.run();
            return move.get();
        }
    }

    private static int[] twoPerPit() {
        int[] counts = new int[16];
        Arrays.fill(counts, 2);
        return counts;
    }

    @BeforeEach
    void setUp() {
        repository = new InMemorySessionRepository(Clock.systemUTC());
        sessions = new SessionService(repository, new GameFactories(List.of(new MangolaFactory())));
        messaging = new RecordingMessaging(sessions);
        broadcaster = new SeatStateBroadcaster<>(messaging, sessions::botSeats, sessions::seatNames,
                (game, viewer, names, bots) -> MangolaDto.forViewer(game, viewer, names));
        actions = new MangolaGameActions(sessions, broadcaster);
        scheduler = new ManualBotScheduler();
        bot = new Scripted();
        coordinator = new MangolaBotCoordinator(sessions::botSeats, sessions::seatOptions, actions, scheduler,
                new FixedThinkingDelay(DELAY), level -> {
                    levelsAsked.add(level);
                    return bot;
                });
        broadcaster.addListener(coordinator);
    }

    private <G extends Mangola> G register(G game, String level) {
        SessionGame session = SessionGame.create(game.getId(), 2, MangolaFactory.GAME_TYPE);
        session.claimHost("Alice");
        session.claimBot(null);
        if (level != null) {
            session.setSeatOption(BOT, "level", level);
        }
        repository.save(game, session);
        bot.watched = game;
        return game;
    }

    private Mangola register(Mangola game) {
        return register(game, null);
    }

    private void start(Mangola game) {
        broadcaster.broadcast(game, "START", new EmptyEventData(), "Game started.");
    }

    private Mangola placement(PlayerId starter) {
        return register(new Mangola(GameId.generate(), MangolaRules.OWNER, starter));
    }

    /** Side A (seat 0, human) has a2:2 and b1:2, side B (seat 1, bot) has c3:2 and d4:3. */
    private static MangolaBoard smallBoard() {
        return MangolaBoard.empty().with(MangolaGeometry.pit("a2"), 2).with(MangolaGeometry.pit("c2"), 1)
                .with(MangolaGeometry.pit("c3"), 2).with(MangolaGeometry.pit("c4"), 3);
    }

    private Mangola playing(PlayerId starter) {
        return register(Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, starter, smallBoard()));
    }

    // ------------------------------------------------------------------ placement

    @Test
    void theBotArrangesAndConfirmsOnceTheGameStartsWithTheInjectedDelay() {
        Mangola game = placement(HUMAN);
        bot.layout = MangolaBotCoordinatorTest::twoPerPit;

        start(game);

        assertThat(scheduler.pending().size(), is(1));
        assertThat(scheduler.pending().get(0).delay(), is(DELAY));
        scheduler.runNext();
        assertThat(game.isReady(BOT), is(true));
        assertThat(game.layoutOf(BOT).orElseThrow(), is(Arrays.stream(twoPerPit()).boxed().toList()));
        assertThat(game.phase(), is(Phase.PLACEMENT));
    }

    @Test
    void theGameStartsWhenTheHumanConfirmsAfterTheBot() throws Exception {
        Mangola game = placement(HUMAN);
        start(game);
        scheduler.runNext();

        actions.arrange(game.getId(), HUMAN, twoPerPit());
        actions.confirmLayout(game.getId(), HUMAN);

        assertThat(game.phase(), is(Phase.PLAY));
        assertThat(scheduler.pending(), is(empty())); // the human starts
    }

    @Test
    void whenTheHumanConfirmedFirstTheBotsConfirmationStartsTheGameAndItPlaysIfItStarts() throws Exception {
        Mangola game = placement(BOT);
        actions.arrange(game.getId(), HUMAN, twoPerPit());
        actions.confirmLayout(game.getId(), HUMAN);
        bot.move = () -> MangolaGeometry.pit("a3");

        start(game);
        scheduler.runNext(); // arranges and confirms: reveals the layouts, the bot (seat A) is on turn

        assertThat(game.phase(), is(Phase.PLAY));
        assertThat(scheduler.pending().size(), is(1));
        scheduler.runNext();
        assertThat(game.lastMove().seat(), is(BOT));
    }

    @Test
    void theBotNeverAsksForAnythingButItsOwnInformationDuringPlacement() throws Exception {
        Mangola game = placement(HUMAN);
        actions.arrange(game.getId(), HUMAN, STUCK_A);
        start(game);

        scheduler.runNext();

        // The port has no parameter for the opponent's draft: rules, the bot's own side, and what the game refused.
        assertThat(bot.rejectedSeen, contains(List.<List<Integer>>of()));
    }

    // -------------------------------------------------------- stuck start refusals

    private List<Integer> boxed(int[] counts) {
        return Arrays.stream(counts).boxed().toList();
    }

    @Test
    void aLayoutRefusedAsAStuckStartIsRetriedWithAnotherOneNeverTheSameAgain() throws Exception {
        Mangola game = placement(HUMAN);
        actions.arrange(game.getId(), HUMAN, STUCK_A);
        actions.confirmLayout(game.getId(), HUMAN); // the human starts with a draft stuck against FACING_A
        List<int[]> answers = new ArrayList<>(List.of(FACING_A, twoPerPit()));
        bot.layout = () -> answers.remove(0);

        start(game);
        scheduler.runNext(); // FACING_A: refused by the game
        assertThat(game.phase(), is(Phase.PLACEMENT));
        assertThat(game.isReady(BOT), is(false));
        assertThat(scheduler.pending().size(), is(1)); // it tries again after a thinking delay
        scheduler.runNext();

        assertThat(bot.rejectedSeen.get(1), contains(boxed(FACING_A)));
        assertThat(game.phase(), is(Phase.PLAY));
    }

    @Test
    void aBotWhoseEveryLayoutIsRefusedStopsTryingInsteadOfLooping() throws Exception {
        Mangola game = placement(HUMAN);
        actions.arrange(game.getId(), HUMAN, STUCK_A);
        actions.confirmLayout(game.getId(), HUMAN);
        bot.layout = () -> FACING_A.clone(); // a strategy that ignores what was refused

        start(game);
        int runs = 0;
        while (!scheduler.pending().isEmpty() && runs++ < 100) {
            scheduler.runNext();
        }

        assertThat(runs < 100, is(true));
        assertThat(game.phase(), is(Phase.PLACEMENT));
        assertThat(scheduler.pending(), is(empty()));
    }

    @Test
    void whenTheHumanTakesItsLayoutBackTheBotTriesAgainFromScratch() throws Exception {
        Mangola game = placement(HUMAN);
        actions.arrange(game.getId(), HUMAN, STUCK_A);
        actions.confirmLayout(game.getId(), HUMAN);
        bot.layout = () -> FACING_A.clone();
        start(game);
        while (!scheduler.pending().isEmpty()) {
            scheduler.runNext();
        }

        actions.editLayout(game.getId(), HUMAN); // a broadcast: the bot wakes up
        actions.arrange(game.getId(), HUMAN, twoPerPit());
        bot.layout = MangolaBotCoordinatorTest::twoPerPit;
        scheduler.runNext();
        actions.confirmLayout(game.getId(), HUMAN);

        assertThat(game.phase(), is(Phase.PLAY));
        assertThat(bot.rejectedSeen.get(bot.rejectedSeen.size() - 1), is(empty())); // forgotten when the human edited
    }

    // ----------------------------------------------------------------------- play

    @Test
    void aHumanMoveThatLeavesTheBotWithoutAMoveEndsTheGameAndSchedulesNothing() throws Exception {
        Mangola game = playing(HUMAN);
        start(game);
        assertThat(scheduler.pending().size(), is(0)); // the human is on turn

        actions.sow(game.getId(), HUMAN, MangolaGeometry.pit("a2")); // E3: captures everything the bot has

        assertThat(game.isFinished(), is(true));
        assertThat(scheduler.pending(), is(empty()));
        assertThat(coordinator.trackedGameCount(), is(0));
    }

    @Test
    void aBotOnTurnPlaysItsDecisionAfterTheDelayAndOnlyTheHumanIsMessaged() throws Exception {
        MangolaBoard board = smallBoard().with(MangolaGeometry.pit("e1"), 2).with(MangolaGeometry.pit("h4"), 2);
        Mangola game = register(Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, HUMAN, board));
        bot.move = () -> MangolaGeometry.pit("h4");
        start(game);
        actions.sow(game.getId(), HUMAN, MangolaGeometry.pit("e1")); // a quiet move: the bot is on turn
        assertThat(scheduler.pending().size(), is(1));
        assertThat(scheduler.pending().get(0).delay(), is(DELAY));
        messaging.clear();

        scheduler.runNext();

        assertThat(game.lastMove().seat(), is(BOT));
        assertThat(game.lastMove().pit(), is(MangolaGeometry.pit("h4")));
        assertThat(game.currentSeat(), is(HUMAN));
        assertThat(messaging.seats, contains(HUMAN)); // the bot seat gets no message
    }

    @Test
    void theDecisionIsComputedOutsideTheMonitorOfTheGame() throws Exception {
        Mangola game = placement(BOT);
        start(game);
        scheduler.runNext(); // layout
        actions.arrange(game.getId(), HUMAN, twoPerPit());
        actions.confirmLayout(game.getId(), HUMAN);
        bot.move = () -> game.legalPits(BOT).get(0);

        scheduler.runNext(); // the move

        assertThat(bot.decidedUnderMonitor, contains(false, false));
    }

    @Test
    void aMoveDecidedWhileTheStateMovedIsDroppedAfterTheVersionCheck() throws Exception {
        Mangola game = placement(HUMAN);
        start(game);
        bot.duringDecision = () -> {
            try {
                game.arrange(HUMAN, twoPerPit()); // the human edits their draft while the bot is thinking
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        };

        scheduler.runNext();

        assertThat(game.isReady(BOT), is(false)); // the stale layout was not applied
        assertThat(game.layoutOf(BOT).isEmpty(), is(true));
    }

    @Test
    void aTaskIsDroppedWhenTheStateMovedWithoutABroadcast() throws Exception {
        Mangola game = placement(HUMAN);
        start(game);
        assertThat(scheduler.pending().size(), is(1));

        game.arrange(HUMAN, twoPerPit()); // the version moves, nobody broadcast
        scheduler.runAllPending();

        assertThat(bot.rejectedSeen, is(empty())); // the stale bot did not even think
        assertThat(game.isReady(BOT), is(false));
    }

    @Test
    void theNextBroadcastCancelsThePreviousTasks() throws Exception {
        Mangola game = placement(HUMAN);
        start(game);
        ManualBotScheduler.Entry first = scheduler.pending().get(0);

        actions.arrange(game.getId(), HUMAN, twoPerPit());

        assertThat(first.isCancelled(), is(true));
        assertThat(scheduler.pending().size(), is(1));
    }

    @Test
    void aStateBroadcastThatChangesNothingDoesNotRestartTheThinking() {
        Mangola game = placement(HUMAN);
        start(game);

        broadcaster.broadcast(game, "CONNECTED", new EmptyEventData(), "Alice is back.");

        assertThat(scheduler.pending().size(), is(1));
        assertThat(scheduler.cancelledCount(), is(0L));
    }

    @Test
    void nothingIsScheduledWhileTheHumanIsOnTurn() {
        Mangola game = playing(HUMAN);

        start(game);

        assertThat(scheduler.all(), is(empty()));
    }

    @Test
    void aFailingStrategyFallsBackToTheFirstLegalPit() throws Exception {
        MangolaBoard board = smallBoard().with(MangolaGeometry.pit("e1"), 2).with(MangolaGeometry.pit("h4"), 2);
        Mangola game = register(Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, HUMAN, board));
        bot.move = () -> 99; // not a pit
        start(game);
        actions.sow(game.getId(), HUMAN, MangolaGeometry.pit("e1"));

        scheduler.runNext();

        assertThat(game.lastMove().seat(), is(BOT));
        assertThat(game.currentSeat(), is(HUMAN));
    }

    @Test
    void aThrowingStrategyFallsBackToTheFirstLegalPit() throws Exception {
        MangolaBoard board = smallBoard().with(MangolaGeometry.pit("e1"), 2).with(MangolaGeometry.pit("h4"), 2);
        Mangola game = register(Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, HUMAN, board));
        bot.move = () -> {
            throw new IllegalStateException("boom");
        };
        start(game);
        actions.sow(game.getId(), HUMAN, MangolaGeometry.pit("e1"));

        scheduler.runNext();

        assertThat(game.lastMove().seat(), is(BOT));
    }

    @Test
    void aFailingLayoutStrategyFallsBackToAFixedLegalLayout() {
        Mangola game = placement(HUMAN);
        bot.layout = () -> new int[]{1, 2, 3};
        start(game);

        scheduler.runNext();

        assertThat(game.isReady(BOT), is(true));
        assertThat(game.layoutOf(BOT).orElseThrow().stream().mapToInt(Integer::intValue).sum(), is(32));
    }

    // ------------------------------------------------------------------ lifecycle

    @Test
    void theLevelIsReadFromTheSeatOptionAndDefaultsToGreedy() {
        Mangola expert = register(new Mangola(GameId.generate(), MangolaRules.OWNER, HUMAN), "expert");
        start(expert);
        scheduler.runNext();

        Mangola plain = register(new Mangola(GameId.generate(), MangolaRules.OWNER, HUMAN));
        start(plain);
        scheduler.runNext();

        assertThat(levelsAsked, is(List.of(MangolaBotLevel.EXPERT, MangolaBotLevel.GREEDY)));
    }

    @Test
    void aForfeitStopsTheBot() {
        Mangola game = placement(HUMAN);
        start(game);
        ManualBotScheduler.Entry pending = scheduler.pending().get(0);

        game.forfeit(HUMAN);
        broadcaster.broadcast(game, "PLAYER_FORFEIT", new EmptyEventData(), "Alice left.");

        assertThat(pending.isCancelled(), is(true));
        assertThat(scheduler.pending(), is(empty()));
        assertThat(coordinator.trackedGameCount(), is(0));
        assertThat(game.result().reason(), is(MangolaResult.Reason.FORFEIT));
    }

    @Test
    void aTaskThatFiresAfterTheGameEndedWithoutABroadcastDoesNothing() {
        Mangola game = placement(HUMAN);
        start(game);

        game.forfeit(HUMAN); // the game is over, nobody broadcast
        scheduler.runAllPending();

        assertThat(bot.rejectedSeen, is(empty()));
    }

    @Test
    void aNewGameUnderTheSameIdAfterPlayAgainGetsItsOwnTable() {
        GameId id = GameId.generate();
        Mangola first = register(new Mangola(id, MangolaRules.OWNER, HUMAN));
        start(first);
        ManualBotScheduler.Entry stale = scheduler.pending().get(0);

        Mangola second = register(new Mangola(id, MangolaRules.OWNER, HUMAN));
        start(second);

        assertThat(stale.isCancelled(), is(true));
        assertThat(scheduler.pending().size(), is(1));
        scheduler.runNext();
        assertThat(second.isReady(BOT), is(true));
        assertThat(first.isReady(BOT), is(false));
        assertThat(coordinator.trackedGameCount(), is(1));
    }

    @Test
    void anEvictedGameIsForgottenAndItsTasksCancelled() {
        Mangola game = placement(HUMAN);
        start(game);
        ManualBotScheduler.Entry pending = scheduler.pending().get(0);

        coordinator.onEvicted(game.getId());

        assertThat(pending.isCancelled(), is(true));
        assertThat(coordinator.trackedGameCount(), is(0));
    }

    @Test
    void aGameWithoutBotsIsNeverTracked() {
        Mangola game = new Mangola(GameId.generate(), MangolaRules.OWNER, HUMAN);
        SessionGame session = SessionGame.create(game.getId(), 2, MangolaFactory.GAME_TYPE);
        session.claimHost("Alice");
        session.claimNextFreeSeat("Bob");
        repository.save(game, session);

        start(game);

        assertThat(coordinator.trackedGameCount(), is(0));
        assertThat(scheduler.all(), is(empty()));
    }

    @Test
    void aBotStartingAPresetGamePlaysAtOnce() {
        Mangola game = playing(BOT); // the bot owns side A: a2:2 c2:1 against the human's c3:2 c4:3
        bot.move = () -> MangolaGeometry.pit("a2");

        start(game);
        assertThat(scheduler.pending().size(), is(1));
        scheduler.runNext();

        assertThat(game.lastMove().seat(), is(BOT));
        assertThat(game.isFinished(), is(true)); // E3: it captured everything the human had
        assertThat(game.result().winner(), is(BOT));
    }
}
