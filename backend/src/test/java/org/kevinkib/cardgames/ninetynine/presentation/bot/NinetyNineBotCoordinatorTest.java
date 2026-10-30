package org.kevinkib.cardgames.ninetynine.presentation.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNineFactory;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNineOptions;
import org.kevinkib.cardgames.ninetynine.domain.Phase;
import org.kevinkib.cardgames.ninetynine.domain.RandomDeckSource;
import org.kevinkib.cardgames.ninetynine.domain.ScriptedDeckSource;
import org.kevinkib.cardgames.ninetynine.domain.bot.NinetyNineBotLevel;
import org.kevinkib.cardgames.ninetynine.domain.bot.NinetyNineBotStrategy;
import org.kevinkib.cardgames.ninetynine.domain.bot.NinetyNineBotView;
import org.kevinkib.cardgames.ninetynine.domain.bot.NinetyNineDecision;
import org.kevinkib.cardgames.ninetynine.presentation.NinetyNineGameActions;
import org.kevinkib.cardgames.ninetynine.presentation.dto.NinetyNineDto;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.ninetynine.presentation.NinetyNineTable.card;
import static org.kevinkib.cardgames.ninetynine.presentation.NinetyNineTable.cards;

/**
 * The coordinator drives the computer seats after each broadcast, with a manual scheduler and a scripted strategy.
 * Seat 0 is the human host, seat 1 the computer, seat 2 a human, unless a test says otherwise.
 */
class NinetyNineBotCoordinatorTest {

    private static final Duration DELAY = Duration.ofMillis(900);
    private static final PlayerId HUMAN = new PlayerId(0);
    private static final PlayerId BOT = new PlayerId(1);
    private static final PlayerId OTHER_HUMAN = new PlayerId(2);
    private static final String DEAL = "9S 7H JC 4S 8H QC 5S 2H 3C 5D 6D";

    private final ScriptedDeckSource decks = new ScriptedDeckSource(new RandomDeckSource());
    private InMemorySessionRepository repository;
    private SessionService sessions;
    private RecordingMessaging messaging;
    private SeatStateBroadcaster<NinetyNine, NinetyNineDto> broadcaster;
    private NinetyNineGameActions actions;
    private ManualBotScheduler scheduler;
    private NinetyNineBotCoordinator coordinator;
    private final List<NinetyNineBotLevel> levelsAsked = new ArrayList<>();
    private Scripted bot;
    private boolean botSeatsFail;

    /** A strategy the test scripts: what it was asked, whether it held the game monitor, and its answer. */
    private final class Scripted implements NinetyNineBotStrategy {
        final List<NinetyNineBotView> views = new ArrayList<>();
        final List<Boolean> underMonitor = new ArrayList<>();
        NinetyNine watched;
        Function<NinetyNineBotView, NinetyNineDecision> answer = view -> {
            Card first = view.hand().get(0);
            return new NinetyNineDecision.Play(first, first.getRank() == FrenchRank.ACE ? 1 : null);
        };

        @Override
        public NinetyNineDecision decide(NinetyNineBotView view) {
            views.add(view);
            underMonitor.add(watched != null && Thread.holdsLock(watched));
            return answer.apply(view);
        }
    }

    @BeforeEach
    void setUp() {
        repository = new InMemorySessionRepository(Clock.systemUTC());
        sessions = new SessionService(repository,
                new GameFactories(List.of(new NinetyNineFactory(decks), new BullshitFactory())));
        messaging = new RecordingMessaging(sessions);
        broadcaster = new SeatStateBroadcaster<>(messaging, sessions::botSeats, sessions::seatNames,
                (game, viewer, names, bots) -> NinetyNineDto.forViewer(game, viewer, names, bots, sessions::seatOptions));
        actions = new NinetyNineGameActions(sessions, broadcaster);
        scheduler = new ManualBotScheduler();
        bot = new Scripted();
        coordinator = new NinetyNineBotCoordinator(id -> {
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

    private NinetyNine register(String deal, String level, int... botSeats) {
        GameId id = GameId.generate();
        decks.fix(id, 1, cards(deal));
        decks.fixFirstSeat(id, 0);
        int seats = 3;
        NinetyNine game = new NinetyNine(id, seats, decks, NinetyNineOptions.defaults());
        SessionGame session = SessionGame.create(game.getId(), game.getPlayerIds(), NinetyNineFactory.GAME_TYPE);
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

    private NinetyNine register(int... botSeats) {
        return register(DEAL, null, botSeats);
    }

    private void start(NinetyNine game) {
        broadcaster.broadcast(game, "START", new EmptyEventData(), "Game started.");
    }

    @Test
    void aBotOnTurnPlaysItsDecisionAfterTheInjectedDelayAndOnlyTheHumansAreMessaged() throws Exception {
        NinetyNine game = register(1);
        start(game);
        assertThat(scheduler.all()).isEmpty(); // a human is on turn
        actions.play(game.getId(), HUMAN, card("9S"), null);
        assertThat(scheduler.pending()).hasSize(1);
        assertThat(scheduler.pending().get(0).delay()).isEqualTo(DELAY);
        messaging.clear();

        scheduler.runNext();

        assertThat(game.pileTop()).contains(card("7H"));
        assertThat(game.currentSeat()).isEqualTo(OTHER_HUMAN);
        assertThat(messaging.seats).contains(HUMAN, OTHER_HUMAN).doesNotContain(BOT);
    }

    @Test
    void theStrategyReceivesTheViewOfItsOwnSeatAndDecidesWithoutHoldingTheGameMonitor() throws Exception {
        NinetyNine game = register(1);
        start(game);
        actions.play(game.getId(), HUMAN, card("9S"), null);

        scheduler.runNext();

        assertThat(bot.views).hasSize(1);
        assertThat(bot.views.get(0).seat()).isEqualTo(BOT);
        assertThat(bot.views.get(0).hand()).containsExactlyElementsOf(cards("7H 8H 2H"));
        assertThat(bot.underMonitor).containsExactly(false);
    }

    @Test
    void theLevelComesFromTheSeatOptionAndDefaultsToMedium() throws Exception {
        NinetyNine hard = register(DEAL, "hard", 1);
        start(hard);
        actions.play(hard.getId(), HUMAN, card("9S"), null);
        scheduler.runNext();
        NinetyNine unset = register(1);
        start(unset);
        actions.play(unset.getId(), HUMAN, card("9S"), null);
        scheduler.runNext();

        assertThat(levelsAsked).containsExactly(NinetyNineBotLevel.HARD, NinetyNineBotLevel.MEDIUM);
    }

    @Test
    void aTaskIsDroppedWhenTheStateMovedOnWhileItWasWaiting() throws Exception {
        NinetyNine game = register(1);
        start(game);
        actions.play(game.getId(), HUMAN, card("9S"), null);
        ManualBotScheduler.Entry task = scheduler.pending().get(0);
        game.forfeit(OTHER_HUMAN); // moves the version without a broadcast

        task.run();

        assertThat(bot.views).isEmpty();
        assertThat(game.currentSeat()).isEqualTo(BOT);
    }

    @Test
    void aFailingBotLookupKeepsTheWaitingTaskInsteadOfCancellingIt() throws Exception {
        NinetyNine game = register(1);
        start(game);
        actions.play(game.getId(), HUMAN, card("9S"), null);
        ManualBotScheduler.Entry task = scheduler.pending().get(0);

        botSeatsFail = true;
        broadcaster.broadcast(game, "OPPONENT_DISCONNECTED", new EmptyEventData(), "");

        assertThat(task.isCancelled()).isFalse();
        botSeatsFail = false;
        task.run();
        assertThat(game.currentSeat()).isEqualTo(OTHER_HUMAN);
    }

    @Test
    void aBotLookupThatFailsOnAStateChangeIsRetriedLater() throws Exception {
        NinetyNine game = register(1);
        start(game);
        botSeatsFail = true;

        actions.play(game.getId(), HUMAN, card("9S"), null); // the bot is now on turn, but the lookup fails
        assertThat(scheduler.pending()).hasSize(1); // a retry, not the bot's play
        botSeatsFail = false;
        scheduler.runNext(); // the retry finds the bot on turn and schedules its play
        assertThat(scheduler.pending()).hasSize(1);
        scheduler.runNext();

        assertThat(game.currentSeat()).isEqualTo(OTHER_HUMAN);
    }

    @Test
    void aBroadcastThatChangesNothingKeepsTheWaitingTask() throws Exception {
        NinetyNine game = register(1);
        start(game);
        actions.play(game.getId(), HUMAN, card("9S"), null);

        broadcaster.broadcast(game, "OPPONENT_DISCONNECTED", new EmptyEventData(), "");

        assertThat(scheduler.pending()).hasSize(1);
        assertThat(scheduler.cancelledCount()).isZero();
    }

    @Test
    void aFailingStrategyFallsBackToTheFirstSafeCardAndAnAceCountsOne() throws Exception {
        NinetyNine game = register("9S AS JC 4S 8H QC 5S 2H 3C 5D 6D", null, 1);
        bot.answer = view -> {
            throw new IllegalStateException("boom");
        };
        start(game);
        actions.play(game.getId(), HUMAN, card("9S"), null);

        scheduler.runNext();

        assertThat(game.pileTop()).contains(card("AS"));
        assertThat(game.total()).isEqualTo(1);
    }

    @Test
    void underFreePlayAFailingStrategyStillFallsBackToASafeCardNotTheFirstOne() throws Exception {
        // the bot (seat 1) holds 7H 9H 2H at 99: only the 9 is safe, and nothing but its choice keeps it from the 7
        NinetyNine game = register("KS 7H JC 9S 9H QC 4S 2H 3C 5D", null, 1);
        bot.answer = view -> {
            throw new IllegalStateException("boom");
        };
        start(game);
        actions.play(game.getId(), HUMAN, card("KS"), null);

        scheduler.runNext();

        assertThat(game.pileTop()).contains(card("9H"));
        assertThat(game.total()).isEqualTo(99);
        assertThat(game.lives().get(BOT)).isEqualTo(3);
    }

    @Test
    void anIllegalDecisionFallsBackToALegalPlay() throws Exception {
        NinetyNine game = register(1);
        bot.answer = view -> new NinetyNineDecision.Play(
                card("KD"), null); // not in hand
        start(game);
        actions.play(game.getId(), HUMAN, card("9S"), null);

        scheduler.runNext();

        assertThat(game.currentSeat()).isEqualTo(OTHER_HUMAN);
        assertThat(game.pileTop()).contains(card("7H"));
    }

    @Test
    void aBotThatStartsTheNextRoundPlaysAfterTheDeal() throws Exception {
        NinetyNine game = register("KS 7H JC 9S 8H QC 4S 2H 3C 5D", null, 1);
        start(game);
        actions.play(game.getId(), HUMAN, card("KS"), null);
        assertThat(game.phase()).isEqualTo(Phase.PLAYING); // the bot is stuck: it must play a card
        scheduler.runNext();
        assertThat(game.phase()).isEqualTo(Phase.ROUND_OVER); // it played a card over 99 and lost a life
        assertThat(scheduler.pending()).isEmpty();

        actions.nextRound(game.getId(), game.version());

        assertThat(game.currentSeat()).isEqualTo(BOT);
        assertThat(scheduler.pending()).hasSize(1);
    }

    @Test
    void aStuckBotPlaysACardThatGoesOver99AndLosesALife() throws Exception {
        NinetyNine game = register("KS 7H JC 9S 8H QC 4S 2H 3C 5D", null, 1);
        start(game);
        actions.play(game.getId(), HUMAN, card("KS"), null);
        assertThat(scheduler.pending()).hasSize(1);

        scheduler.runNext();

        assertThat(game.pileTop()).contains(card("7H"));
        assertThat(game.total()).isEqualTo(106);
        assertThat(game.lives().get(BOT)).isEqualTo(2);
        assertThat(game.lastBust()).isPresent();
    }

    @Test
    void aStuckBotWhoseStrategyFailsFallsBackToItsFirstCard() throws Exception {
        NinetyNine game = register("KS 7H JC 9S 8H QC 4S 2H 3C 5D", null, 1);
        bot.answer = view -> {
            throw new IllegalStateException("boom");
        };
        start(game);
        actions.play(game.getId(), HUMAN, card("KS"), null);

        scheduler.runNext();

        assertThat(game.phase()).isEqualTo(Phase.ROUND_OVER);
        assertThat(game.pileTop()).contains(card("7H"));
    }

    @Test
    void nothingIsScheduledOnceTheGameIsOver() throws Exception {
        NinetyNine game = register(1);
        game.forfeit(HUMAN);
        game.forfeit(OTHER_HUMAN);
        start(game);

        assertThat(scheduler.pending()).isEmpty();
    }

    @Test
    void anEvictedGameDropsItsTable() throws Exception {
        NinetyNine game = register(1);
        start(game);
        actions.play(game.getId(), HUMAN, card("9S"), null);
        ManualBotScheduler.Entry task = scheduler.pending().get(0);

        coordinator.onEvicted(game.getId());

        assertThat(task.isCancelled()).isTrue();
    }
}
