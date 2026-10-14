package org.kevinkib.cardgames.kobo.presentation.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.KoboFactory;
import org.kevinkib.cardgames.kobo.domain.bot.HeuristicKoboBot;
import org.kevinkib.cardgames.kobo.domain.bot.KoboBotDecision;
import org.kevinkib.cardgames.kobo.domain.bot.KoboBotLevel;
import org.kevinkib.cardgames.kobo.domain.bot.KoboBotMemory;
import org.kevinkib.cardgames.kobo.domain.bot.KoboBotStrategy;
import org.kevinkib.cardgames.kobo.domain.bot.KoboBotView;
import org.kevinkib.cardgames.kobo.domain.power.Power;
import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import org.kevinkib.cardgames.kobo.presentation.KoboGameActions;
import org.kevinkib.cardgames.kobo.presentation.dto.KoboDto;
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
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.ref;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;

/**
 * The coordinator drives the bot seats after each broadcast, with a manual scheduler, real session core, real
 * actions and broadcaster, the real memory, and a strategy the test scripts (or the real heuristic for matches).
 */
class KoboBotCoordinatorTest {

    private static final Duration THINK = Duration.ofMillis(900);
    private static final PlayerId HUMAN = seat(0);
    private static final PlayerId BOT = seat(1);

    /** The strategy handed to the coordinator for every level; it plays what the test scripts. */
    private final class ScriptedStrategy implements KoboBotStrategy {
        KoboBotDecision decision;
        RuntimeException failure;
        Optional<KoboBotDecision.Match> match = Optional.empty();
        boolean realMatching;
        int decideCalls;
        private final KoboBotStrategy real = new HeuristicKoboBot(KoboBotLevel.MEDIUM.tuning());

        @Override
        public KoboBotDecision decide(KoboBotView view, KoboBotMemory memory) {
            decideCalls++;
            if (failure != null) {
                throw failure;
            }
            return decision;
        }

        @Override
        public Optional<KoboBotDecision.Match> matchFor(KoboBotView view, KoboBotMemory memory) {
            return realMatching ? real.matchFor(view, memory) : match;
        }
    }

    private InMemorySessionRepository repository;
    private SessionService sessions;
    private RecordingMessaging messaging;
    private SeatStateBroadcaster<Kobo, KoboDto> broadcaster;
    private KoboGameActions actions;
    private ManualBotScheduler scheduler;
    private KoboBotCoordinator coordinator;
    private final ScriptedStrategy strategy = new ScriptedStrategy();
    private final List<KoboBotLevel> levelsAsked = new ArrayList<>();
    /** What every forgetting draw returns: 0.99 keeps a memory entry, 0.0 forgets it. */
    private double forgetDraw = 0.99;

    @BeforeEach
    void setUp() {
        repository = new InMemorySessionRepository(Clock.systemUTC());
        sessions = new SessionService(repository, new GameFactories(List.of(new KoboFactory())));
        messaging = new RecordingMessaging(sessions);
        broadcaster = new SeatStateBroadcaster<>(messaging, sessions::botSeats, sessions::seatNames,
                (game, viewer, names, bots) -> KoboDto.forViewer(game, viewer, names, bots));
        actions = new KoboGameActions(sessions, broadcaster);
        scheduler = new ManualBotScheduler();
        RandomGenerator random = new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public double nextDouble() {
                return forgetDraw;
            }
        };
        coordinator = new KoboBotCoordinator(sessions::botSeats, sessions::seatOptions, actions, scheduler,
                new FixedThinkingDelay(THINK), tuning -> Duration.ofMillis(tuning.reactionMinMs()), level -> {
                    levelsAsked.add(level);
                    return strategy;
                }, random);
        broadcaster.addListener(coordinator);
    }

    /**
     * Registers a game: seat 0 is the human host, then one entry per other seat, "human", "bot" or "bot:&lt;level&gt;".
     */
    private Kobo register(Kobo game, String... others) {
        SessionGame session = SessionGame.create(game.getId(), game.getPlayerIds().size(), KoboFactory.GAME_TYPE);
        session.claimHost("Alice");
        for (int i = 0; i < others.length; i++) {
            if (others[i].startsWith("bot")) {
                session.claimBot(null);
                if (others[i].contains(":")) {
                    session.setSeatOption(seat(i + 1), KoboFactory.LEVEL_KEY, others[i].substring(4));
                }
            } else {
                session.claimNextFreeSeat("Human " + (i + 1));
            }
        }
        repository.save(game, session);
        return game;
    }

    private Kobo register(Kobo game) {
        return register(game, "bot");
    }

    private void start(Kobo game) {
        broadcaster.broadcast(game, "START", new EmptyEventData(), "Game started.");
    }

    /**
     * Opens the game in its memorising phase, so the bots see their starting cards, then lets every seat say ready
     * (through the real use cases): the round starts with the first draw still to come.
     */
    private void startAndMemorise(Kobo game) throws Exception {
        start(game);
        for (PlayerId player : game.getPlayerIds()) {
            actions.ready(game.getId(), player);
        }
        assertThat(game.phase(), is(Phase.DRAW));
    }

    private static Kobo twoPlayers(Phase phase, int current) {
        return aKobo().withPlayers(2).inPhase(phase).currentSeat(current).build();
    }

    private List<Duration> pendingDelays() {
        return scheduler.pending().stream().map(ManualBotScheduler.Entry::delay).sorted().toList();
    }

    /** Seat 0 human with a plain hand, seat 1 the bot holding 5H and KS in its starting slots, a 5 on the pile. */
    private Kobo fiveOnThePile() {
        return aKobo()
                .withTableau("AH", "2H", "3H", "4H").withTableau("9S", "8S", "5H", "KS")
                .withDiscard("5C").inPhase(Phase.MEMORISING).currentSeat(0).build();
    }

    // ---------------------------------------------------------------- turns

    @Test
    void actsAfterTheThinkingDelayAndTheBotSeatReceivesNoMessage() throws Exception {
        Kobo game = register(twoPlayers(Phase.DRAW, 1));
        strategy.decision = new KoboBotDecision.Draw();
        AtomicInteger notified = new AtomicInteger();
        broadcaster.addListener((g, type, data) -> notified.incrementAndGet());

        start(game);

        assertThat(pendingDelays(), contains(THINK));
        assertThat(notified.get(), is(1));
        messaging.clear();

        scheduler.runNext();

        assertThat(game.phase(), is(Phase.DECISION));
        assertThat(messaging.seats, contains(HUMAN));
        assertThat(messaging.payloads.get(0).getEventType(), is("DRAW"));
        assertThat(notified.get(), is(2));
    }

    @Test
    void nothingIsScheduledWhileAHumanIsOnTurn() {
        Kobo game = register(twoPlayers(Phase.DRAW, 0));

        start(game);

        assertThat(scheduler.all(), is(empty()));
    }

    @Test
    void aStateBroadcastThatChangesNothingDoesNotRestartTheThinking() {
        Kobo game = register(twoPlayers(Phase.DRAW, 1));
        start(game);

        broadcaster.broadcast(game, "CONNECTED", new EmptyEventData(), "Alice is back.");

        assertThat(scheduler.pending().size(), is(1));
        assertThat(scheduler.cancelledCount(), is(0L));
    }

    @Test
    void aStaleTurnTaskIsDroppedWhenTheStateMovedWithoutABroadcast() throws Exception {
        Kobo game = register(aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .withDiscard("2C").inPhase(Phase.DRAW).currentSeat(1).build());
        strategy.decision = new KoboBotDecision.Draw();
        start(game);

        game.matchDiscard(HUMAN, ref(0, 1), null, FrenchRank.TWO, 0); // the version moves, nobody broadcast
        scheduler.runAllPending();

        assertThat(strategy.decideCalls, is(0));
        assertThat(game.phase(), is(Phase.DRAW));
    }

    @Test
    void theNextBroadcastCancelsThePreviousTurnTask() throws Exception {
        Kobo game = register(aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .withDiscard("2C").inPhase(Phase.DRAW).currentSeat(1).build());
        start(game);
        ManualBotScheduler.Entry first = scheduler.pending().get(0);

        actions.match(game.getId(), HUMAN, ref(0, 1), null, FrenchRank.TWO, 0);

        assertThat(first.isCancelled(), is(true));
        assertThat(scheduler.pending().size(), is(1));
    }

    @Test
    void readiesAtTheMemorisingBarrier() throws Exception {
        Kobo game = register(twoPlayers(Phase.MEMORISING, 0));
        strategy.decision = new KoboBotDecision.Ready();

        start(game);
        assertThat(pendingDelays(), contains(THINK));
        scheduler.runNext();

        assertThat(game.readySeats(), hasItem(BOT));
    }

    @Test
    void readiesAtTheRoundOverBarrier() throws Exception {
        Kobo game = register(twoPlayers(Phase.ROUND_OVER, 0));
        strategy.decision = new KoboBotDecision.Ready();

        start(game);
        scheduler.runNext();

        assertThat(game.readySeats(), hasItem(BOT));
    }

    @Test
    void doesNotReadyTwiceOnceReady() throws Exception {
        Kobo game = register(twoPlayers(Phase.MEMORISING, 0));
        strategy.decision = new KoboBotDecision.Ready();
        start(game);
        scheduler.runNext();

        assertThat(scheduler.pending(), is(empty())); // ready: no further READY offered to the bot
        assertThat(strategy.decideCalls, is(1));
    }

    @Test
    void givesTenInTheGiftPhase() throws Exception {
        Kobo game = aKobo().withTableau("KS", "QS", "JS", "10S").withTableau("AH", "2H", "3H", "4H")
                .inPhase(Phase.TURN_END).currentSeat(1).withDrawPile("2C", "3C").build();
        game.announceKobo(BOT);
        game.draw(HUMAN);
        game.discardDrawn(HUMAN);
        game.endTurn(HUMAN);
        assertThat(game.phase(), is(Phase.GIFT));
        register(game);
        strategy.decision = new KoboBotDecision.GiveTen(HUMAN);

        start(game);
        scheduler.runNext();

        assertThat(game.phase(), is(Phase.ROUND_OVER));
        assertThat(game.lastRoundResult().get().giftTo(), is(HUMAN));
    }

    // -------------------------------------------------------------- fallbacks

    private void assertTheFallbackPlays(Kobo game, java.util.function.Consumer<Kobo> check) {
        register(game);
        strategy.failure = new IllegalStateException("boom");
        start(game);

        scheduler.runNext();

        check.accept(game);
    }

    @Test
    void aFailingStrategyFallsBackToDrawing() {
        assertTheFallbackPlays(twoPlayers(Phase.DRAW, 1), g -> assertThat(g.phase(), is(Phase.DECISION)));
    }

    @Test
    void aFailingStrategyFallsBackToDiscardingTheDrawnCard() {
        assertTheFallbackPlays(aKobo().withPlayers(2).inPhase(Phase.DECISION).currentSeat(1).withDrawn("5H").build(),
                g -> {
                    assertThat(g.phase(), is(Phase.TURN_END));
                    assertThat(g.discardTop().get(), is(card("5H")));
                });
    }

    @Test
    void aFailingStrategyFallsBackToSkippingAPower() {
        assertTheFallbackPlays(aKobo().withPlayers(2).inPhase(Phase.POWER).currentSeat(1)
                .withPendingPower(Power.PEEK_OWN).build(), g -> assertThat(g.phase(), is(Phase.TURN_END)));
    }

    @Test
    void aFailingStrategyFallsBackToSkippingTheKingDecision() throws Exception {
        Kobo game = aKobo().withPlayers(2).inPhase(Phase.POWER).currentSeat(1).withPendingPower(Power.KING).build();
        game.peek(BOT, ref(0, 0));
        assertThat(game.phase(), is(Phase.KING_DECISION));

        assertTheFallbackPlays(game, g -> assertThat(g.phase(), is(Phase.TURN_END)));
    }

    @Test
    void aFailingStrategyFallsBackToEndingTheTurn() {
        assertTheFallbackPlays(twoPlayers(Phase.TURN_END, 1), g -> {
            assertThat(g.phase(), is(Phase.DRAW));
            assertThat(g.currentSeat(), is(HUMAN));
        });
    }

    @Test
    void aFailingStrategyFallsBackToReadyAtABarrier() {
        assertTheFallbackPlays(twoPlayers(Phase.MEMORISING, 0), g -> assertThat(g.readySeats(), hasItem(BOT)));
    }

    @Test
    void aFailingStrategyFallsBackToGivingTheTenToTheFirstOtherSeat() throws Exception {
        Kobo game = aKobo().withTableau("KS", "QS", "JS", "10S").withTableau("AH", "2H", "3H", "4H")
                .inPhase(Phase.TURN_END).currentSeat(1).withDrawPile("2C", "3C").build();
        game.announceKobo(BOT);
        game.draw(HUMAN);
        game.discardDrawn(HUMAN);
        game.endTurn(HUMAN);

        assertTheFallbackPlays(game, g -> assertThat(g.phase(), is(Phase.ROUND_OVER)));
    }

    @Test
    void aRefusedDecisionFallsBackLikeAFailure() {
        strategy.decision = new KoboBotDecision.Draw(); // not allowed while deciding what to do with a card
        Kobo game = register(aKobo().withPlayers(2).inPhase(Phase.DECISION).currentSeat(1).withDrawn("5H").build());
        start(game);

        scheduler.runNext();

        assertThat(game.phase(), is(Phase.TURN_END));
        assertThat(game.discardTop().get(), is(card("5H")));
    }

    @Test
    void aKingSwapRefusedAsStaleFallsBackToSkippingThePower() throws Exception {
        Kobo game = aKobo().withTableau("5S", "2H", "3H", "4H").withTableau("9S", "8S", "6H", "KS")
                .withTableau("7S", "6S", "5D", "KC")
                .withDiscard("5C").inPhase(Phase.POWER).currentSeat(1).withPendingPower(Power.KING).build();
        game.peek(BOT, ref(0, 0));
        register(game, "bot", "human");
        strategy.decision = new KoboBotDecision.KingSwap(3);
        start(game);
        // a third player gives a card into the peeked slot: its revision moves, the king swap is refused
        actions.match(game.getId(), seat(2), ref(0, 0), 0, FrenchRank.FIVE, 0);
        assertThat(scheduler.pending().size(), is(1));

        scheduler.runNext();

        assertThat(strategy.decideCalls, is(1));
        assertThat(game.phase(), is(Phase.TURN_END));
        assertThat(game.slots(BOT).get(3).revision(), is(0)); // no swap happened
    }

    // -------------------------------------------------------- the match race

    @Test
    void aMatchTaskSurvivesAHumansDrawAndFiresAfterTheReactionDelay() throws Exception {
        Kobo game = register(fiveOnThePile());
        strategy.realMatching = true;

        startAndMemorise(game);
        assertThat(pendingDelays(), contains(Duration.ofMillis(KoboBotLevel.MEDIUM.tuning().reactionMinMs())));
        ManualBotScheduler.Entry match = scheduler.pending().get(0);

        actions.draw(game.getId(), HUMAN);

        assertThat(match.isCancelled(), is(false));
        assertThat(scheduler.pending(), contains(match));
        messaging.clear();

        scheduler.runNext();

        assertThat(game.handSize(BOT), is(3));
        assertThat(game.discardTop().get(), is(card("5H")));
        assertThat(messaging.payloads.get(0).getEventType(), is("MATCH"));
    }

    @Test
    void aMatchTaskIsCancelledWhenTheDiscardTopChanges() throws Exception {
        Kobo game = register(fiveOnThePile());
        strategy.realMatching = true;
        startAndMemorise(game);
        ManualBotScheduler.Entry match = scheduler.pending().get(0);
        actions.draw(game.getId(), HUMAN);

        actions.discardDrawn(game.getId(), HUMAN); // a 2 lands on the pile

        assertThat(match.isCancelled(), is(true));
        assertThat(scheduler.pending(), is(empty())); // the bot knows no 2
    }

    @Test
    void aNewTopOfTheSameRankSchedulesANewMatchWithTheCardStillRemembered() throws Exception {
        Kobo game = register(aKobo()
                .withTableau("5D", "2H", "3H", "4H").withTableau("9S", "8S", "5H", "KS")
                .withDiscard("5C").inPhase(Phase.MEMORISING).currentSeat(0).build());
        strategy.realMatching = true;
        startAndMemorise(game);
        ManualBotScheduler.Entry first = scheduler.pending().get(0);

        actions.match(game.getId(), HUMAN, ref(0, 0), null, FrenchRank.FIVE, 0); // the human is quicker

        assertThat(first.isCancelled(), is(true));
        assertThat(scheduler.pending().size(), is(1)); // the bot still knows its 5 and tries again
    }

    @Test
    void aLostRaceIsIgnoredWithoutAFallbackOrAPenalty() throws Exception {
        Kobo game = register(fiveOnThePile());
        // the bot remembers its slot 2 at revision 0 but believes the pile shows a 6: the game refuses, no penalty
        strategy.match = Optional.of(new KoboBotDecision.Match(ref(1, 2), null, FrenchRank.SIX, 0));
        startAndMemorise(game);
        int version = game.version();
        messaging.clear();

        scheduler.runNext();

        assertThat(game.version(), is(version));
        assertThat(game.handSize(BOT), is(4));
        assertThat(messaging.payloads, is(empty()));
        assertThat(strategy.decideCalls, is(0));
        assertThat(scheduler.pending(), is(empty()));
    }

    @Test
    void aMatchTaskForACardTheBotNoLongerRemembersDoesNothing() throws Exception {
        Kobo game = register(fiveOnThePile());
        // slot 0 of the bot was never seen: the memory holds no entry for it
        strategy.match = Optional.of(new KoboBotDecision.Match(ref(1, 0), null, FrenchRank.FIVE, 0));
        startAndMemorise(game);
        int version = game.version();

        scheduler.runNext();

        assertThat(game.version(), is(version));
    }

    @Test
    void eachBotReactsAtItsOwnLevelSpeed() throws Exception {
        Kobo game = register(aKobo()
                .withTableau("AH", "2H", "3H", "4H").withTableau("9S", "8S", "5H", "KS").withTableau("9D", "8D", "QD", "5S")
                .withDiscard("5C").inPhase(Phase.MEMORISING).currentSeat(0).build(), "bot:easy", "bot:hard");
        strategy.realMatching = true;

        startAndMemorise(game);

        assertThat(pendingDelays(), contains(
                Duration.ofMillis(KoboBotLevel.HARD.tuning().reactionMinMs()),
                Duration.ofMillis(KoboBotLevel.EASY.tuning().reactionMinMs())));
        assertThat(levelsAsked, hasItem(KoboBotLevel.EASY));
        assertThat(levelsAsked, hasItem(KoboBotLevel.HARD));
    }

    @Test
    void theLevelDefaultsToMediumWithoutASeatOption() {
        Kobo game = register(twoPlayers(Phase.DRAW, 1));

        start(game);

        assertThat(levelsAsked, hasItem(KoboBotLevel.MEDIUM));
        assertThat(levelsAsked, not(hasItem(KoboBotLevel.EASY)));
    }

    // ------------------------------------------------- what the memory is fed

    private KoboBotMemory memory(Kobo game) {
        return coordinator.memoryOf(game.getId(), BOT).orElseThrow();
    }

    private Kobo humanWithAJackToPlay(String botSeat) {
        return register(aKobo()
                .withTableau("AH", "2H", "3H", "4H").withTableau("9S", "8S", "5H", "KS")
                .withDiscard("QC").withDrawPile("JC", "3C", "4C")
                .inPhase(Phase.MEMORISING).currentSeat(0).build(), botSeat);
    }

    @Test
    void theStartingCardsAreRememberedFromTheFirstBroadcast() throws Exception {
        Kobo game = humanWithAJackToPlay("bot");

        startAndMemorise(game);

        assertThat(memory(game).known(ref(1, 2)).get().card(), is(card("5H")));
        assertThat(memory(game).known(ref(1, 3)).get().card(), is(card("KS")));
        assertThat(memory(game).known(ref(1, 0)).isPresent(), is(false));
    }

    @Test
    void aBlindSwapMovesWhatTheBotRemembers() throws Exception {
        Kobo game = humanWithAJackToPlay("bot");
        startAndMemorise(game);
        actions.draw(game.getId(), HUMAN);
        actions.discardDrawn(game.getId(), HUMAN); // a jack: blind swap

        actions.blindSwap(game.getId(), HUMAN, 0, ref(1, 2));

        assertThat(memory(game).known(ref(0, 0)).get().card(), is(card("5H")));
        assertThat(memory(game).known(ref(0, 0)).get().revision(), is(game.slots(HUMAN).get(0).revision()));
        assertThat(memory(game).known(ref(1, 2)).isPresent(), is(false));
        assertThat(memory(game).known(ref(1, 3)).get().card(), is(card("KS")));
    }

    @Test
    void aBotThatDoesNotFollowMovesLosesTheCardsThatMoved() throws Exception {
        Kobo game = humanWithAJackToPlay("bot:easy");
        startAndMemorise(game);
        actions.draw(game.getId(), HUMAN);
        actions.discardDrawn(game.getId(), HUMAN);

        actions.blindSwap(game.getId(), HUMAN, 0, ref(1, 2));

        assertThat(memory(game).known(ref(0, 0)).isPresent(), is(false));
        assertThat(memory(game).known(ref(1, 2)).isPresent(), is(false));
        assertThat(memory(game).known(ref(1, 3)).get().card(), is(card("KS")));
    }

    @Test
    void aKingSwapMovesWhatTheBotRemembers() throws Exception {
        Kobo game = register(aKobo()
                .withTableau("AH", "2H", "3H", "4H").withTableau("9S", "8S", "5H", "KS")
                .withDiscard("QC").withDrawPile("KC", "3C", "4C")
                .inPhase(Phase.MEMORISING).currentSeat(0).build());
        startAndMemorise(game);
        actions.draw(game.getId(), HUMAN);
        actions.discardDrawn(game.getId(), HUMAN); // a king
        actions.peek(game.getId(), HUMAN, ref(1, 2));

        actions.kingSwap(game.getId(), HUMAN, 3);

        assertThat(memory(game).known(ref(0, 3)).get().card(), is(card("5H")));
        assertThat(memory(game).known(ref(1, 2)).isPresent(), is(false));
    }

    @Test
    void aMatchGiftMovesWhatTheBotRemembers() throws Exception {
        Kobo game = register(aKobo()
                .withTableau("AH", "7D", "3H", "4H").withTableau("9S", "8S", "5H", "KS")
                .withDiscard("KC").inPhase(Phase.POWER).currentSeat(1).withPendingPower(Power.PEEK_OTHER).build());
        strategy.decision = new KoboBotDecision.Peek(ref(0, 1));
        start(game);
        scheduler.runNext(); // the bot looks at the human's 7
        assertThat(memory(game).known(ref(0, 1)).get().card(), is(card("7D")));

        // the human matches the bot's king and gives that very 7 into the bot's slot
        actions.match(game.getId(), HUMAN, ref(1, 3), 1, FrenchRank.KING, 0);

        assertThat(memory(game).known(ref(1, 3)).get().card(), is(card("7D")));
        assertThat(memory(game).known(ref(1, 3)).get().revision(), is(game.slots(BOT).get(3).revision()));
        assertThat(memory(game).known(ref(0, 1)).isPresent(), is(false));
    }

    @Test
    void theBotsOwnSwapRemembersTheCardItPlaced() throws Exception {
        Kobo game = register(aKobo().withPlayers(2).inPhase(Phase.DECISION).currentSeat(1).withDrawn("9H").build());
        strategy.decision = new KoboBotDecision.SwapDrawn(0);
        start(game);

        scheduler.runNext();

        Card placed = card("9H");
        assertThat(memory(game).known(ref(1, 0)).get().card(), is(placed));
        assertThat(memory(game).known(ref(1, 0)).get().revision(), is(game.slots(BOT).get(0).revision()));
    }

    private Kobo botOnTurnKnowingItsStartingCards() throws Exception {
        Kobo game = register(aKobo()
                .withTableau("AH", "2H", "3H", "4H").withTableau("9S", "8S", "5H", "KS")
                .withDiscard("QC").withDrawPile("2C", "3C", "4C")
                .inPhase(Phase.MEMORISING).currentSeat(0).build());
        startAndMemorise(game);
        actions.draw(game.getId(), HUMAN); // the human plays a quiet turn, then it is the bot's
        actions.discardDrawn(game.getId(), HUMAN);
        actions.endTurn(game.getId(), HUMAN);
        assertThat(game.currentSeat(), is(BOT));
        strategy.decision = new KoboBotDecision.Draw();
        scheduler.runNext();
        strategy.decision = new KoboBotDecision.DiscardDrawn();
        scheduler.runNext();
        assertThat(game.phase(), is(Phase.TURN_END));
        assertThat(memory(game).known(ref(1, 2)).isPresent(), is(true));
        return game;
    }

    @Test
    void theBotForgetsWhenItEndsItsTurnAndKeepsWhenTheDrawIsHigh() throws Exception {
        forgetDraw = 0.0;
        Kobo forgetting = botOnTurnKnowingItsStartingCards();
        strategy.decision = new KoboBotDecision.EndTurn();
        scheduler.runNext();
        assertThat(memory(forgetting).known(ref(1, 2)).isPresent(), is(false));

        forgetDraw = 0.99;
        Kobo keeping = botOnTurnKnowingItsStartingCards();
        strategy.decision = new KoboBotDecision.EndTurn();
        scheduler.runNext();
        assertThat(memory(keeping).known(ref(1, 2)).isPresent(), is(true));
        assertThat(memory(keeping).ownTurns(), is(1));
    }

    @Test
    void theBotAlsoForgetsWhenItAnnouncesKobo() throws Exception {
        forgetDraw = 0.0;
        Kobo game = botOnTurnKnowingItsStartingCards();
        strategy.decision = new KoboBotDecision.AnnounceKobo();

        scheduler.runNext();

        assertThat(game.announcer().get(), is(BOT));
        assertThat(memory(game).known(ref(1, 2)).isPresent(), is(false));
        assertThat(memory(game).ownTurns(), is(1));
    }

    // ---------------------------------------------------------------- lifecycle

    @Test
    void aFinishedGameStopsTheBotAndCancelsItsTasks() {
        Kobo game = register(twoPlayers(Phase.DRAW, 1));
        start(game);
        ManualBotScheduler.Entry pending = scheduler.pending().get(0);

        game.forfeit(HUMAN);
        broadcaster.broadcast(game, "PLAYER_FORFEIT", new EmptyEventData(), "Alice left.");

        assertThat(pending.isCancelled(), is(true));
        assertThat(scheduler.pending(), is(empty()));
        assertThat(coordinator.trackedGameCount(), is(0));
    }

    @Test
    void aTaskThatFiresAfterTheGameEndedWithoutABroadcastDoesNothing() {
        Kobo game = register(twoPlayers(Phase.DRAW, 1));
        strategy.decision = new KoboBotDecision.Draw();
        start(game);

        game.forfeit(HUMAN);
        scheduler.runAllPending();

        assertThat(strategy.decideCalls, is(0));
    }

    @Test
    void anEvictedGameIsForgottenAndItsTasksCancelled() {
        Kobo game = register(twoPlayers(Phase.DRAW, 1));
        start(game);
        ManualBotScheduler.Entry pending = scheduler.pending().get(0);

        coordinator.onEvicted(game.getId());

        assertThat(pending.isCancelled(), is(true));
        assertThat(coordinator.trackedGameCount(), is(0));
    }

    @Test
    void nothingIsScheduledWhenOnlyBotsRemain() {
        Kobo game = twoPlayers(Phase.DRAW, 1);
        SessionGame session = SessionGame.create(game.getId(), 2, KoboFactory.GAME_TYPE);
        session.claimBot(null);
        session.claimBot(null);
        repository.save(game, session);

        start(game);

        assertThat(scheduler.all(), is(empty()));
        assertThat(coordinator.trackedGameCount(), is(0));
    }

    @Test
    void aGameWithoutBotsIsNeverTracked() {
        Kobo game = register(twoPlayers(Phase.DRAW, 0), "human");

        start(game);

        assertThat(coordinator.trackedGameCount(), is(0));
        assertThat(scheduler.all(), is(empty()));
    }

    @Test
    void aNewGameUnderTheSameIdAfterPlayAgainGetsItsOwnTableAndMemories() throws Exception {
        GameId id = GameId.generate();
        strategy.realMatching = true;
        Kobo first = register(aKobo().withId(id)
                .withTableau("AH", "2H", "3H", "4H").withTableau("9S", "8S", "5H", "KS")
                .withDiscard("5C").inPhase(Phase.MEMORISING).currentSeat(0).build());
        startAndMemorise(first);
        ManualBotScheduler.Entry stale = scheduler.pending().get(0); // a match of the first game's 5

        Kobo second = register(aKobo().withId(id)
                .withTableau("AH", "2H", "3H", "4H").withTableau("9D", "8D", "QD", "6S")
                .withDiscard("7C").inPhase(Phase.MEMORISING).currentSeat(0).build());
        startAndMemorise(second);

        assertThat(stale.isCancelled(), is(true));
        assertThat(scheduler.pending(), is(empty())); // the new bot knows no 7
        assertThat(coordinator.trackedGameCount(), is(1));
        assertThat(coordinator.memoryOf(id, BOT).get().known(ref(1, 2)).get().card(), is(card("QD")));
        assertThat(coordinator.memoryOf(id, BOT).get().known(ref(1, 3)).get().card(), is(card("6S")));
    }
}
