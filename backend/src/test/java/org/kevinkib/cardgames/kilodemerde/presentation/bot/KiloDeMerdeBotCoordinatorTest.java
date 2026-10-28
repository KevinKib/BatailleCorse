package org.kevinkib.cardgames.kilodemerde.presentation.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeFactory;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeOptions;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeRules;
import org.kevinkib.cardgames.kilodemerde.domain.Phase;
import org.kevinkib.cardgames.kilodemerde.domain.RandomDeckSource;
import org.kevinkib.cardgames.kilodemerde.domain.ScriptedDeckSource;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeBotLevel;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeBotStrategy;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeBotTuning;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeBotTuning.Range;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeBotView;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeReflex;
import org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeGameActions;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.KiloDeMerdeDto;
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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.ALL_SETS;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.NO_SET;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.PILE;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.card;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.hands;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.cards;

/**
 * The coordinator drives the computer seats after each broadcast, with a manual scheduler and a scripted strategy.
 * Seat 0 is the human host; the other seats are computers unless a test says otherwise. Tasks are keyed on the
 * round, the step and the race, never on the version.
 */
class KiloDeMerdeBotCoordinatorTest {

    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);
    private static final PlayerId S2 = new PlayerId(2);
    private static final PlayerId S3 = new PlayerId(3);
    private static final long PASS_MS = 700;
    private static final long OWN_MS = 1000;
    private static final long FOLLOW_MS = 1200;

    private final ScriptedDeckSource decks = new ScriptedDeckSource(new RandomDeckSource());
    private InMemorySessionRepository repository;
    private SessionService sessions;
    private RecordingMessaging messaging;
    private SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto> broadcaster;
    private KiloDeMerdeGameActions actions;
    private ManualBotScheduler scheduler;
    private KiloDeMerdeBotCoordinator coordinator;
    private final List<KiloDeMerdeBotLevel> levelsAsked = new ArrayList<>();
    private Scripted bot;

    /** A strategy the test scripts: what it was asked and its answer. */
    private static final class Scripted implements KiloDeMerdeBotStrategy {
        final List<KiloDeMerdeBotView> views = new ArrayList<>();
        Function<KiloDeMerdeBotView, Card> answer = view -> view.hand().get(0);

        @Override
        public Card choosePass(KiloDeMerdeBotView view) {
            views.add(view);
            return answer.apply(view);
        }
    }

    @BeforeEach
    void setUp() {
        repository = new InMemorySessionRepository(Clock.systemUTC());
        sessions = new SessionService(repository,
                new GameFactories(List.of(new KiloDeMerdeFactory(decks), new BullshitFactory())));
        messaging = new RecordingMessaging(sessions);
        broadcaster = new SeatStateBroadcaster<>(messaging, sessions::botSeats, sessions::seatNames,
                (game, viewer, names, bots) -> KiloDeMerdeDto.forViewer(game, viewer, names, bots,
                        sessions::seatOptions, org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeDeadlines.off()));
        actions = new KiloDeMerdeGameActions(sessions, broadcaster);
        scheduler = new ManualBotScheduler();
        bot = new Scripted();
        KiloDeMerdeBotTuning.Slap slap = new KiloDeMerdeBotTuning.Slap(Range.fixed(OWN_MS), Range.fixed(FOLLOW_MS));
        KiloDeMerdeBotTuning tuning = new KiloDeMerdeBotTuning(Range.fixed(PASS_MS), slap, slap, slap);
        coordinator = new KiloDeMerdeBotCoordinator(sessions::botSeats, sessions::seatOptions, actions, scheduler,
                new KiloDeMerdeReflex(tuning), new Random(7), level -> {
                    levelsAsked.add(level);
                    return bot;
                });
        broadcaster.addListener(coordinator);
    }

    private KiloDeMerde register(int seats, String dealt, String level, int... botSeats) {
        GameId id = GameId.generate();
        decks.fixDeal(id, hands(dealt), cards(PILE));
        KiloDeMerde game = new KiloDeMerde(id, seats, decks, KiloDeMerdeOptions.defaults());
        SessionGame session = SessionGame.create(game.getId(), game.getPlayerIds(), KiloDeMerdeFactory.GAME_TYPE);
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
        return game;
    }

    private KiloDeMerde threeSeats(String dealt, int... botSeats) {
        return register(3, dealt, null, botSeats);
    }

    private void start(KiloDeMerde game) {
        broadcaster.broadcast(game, "START", new EmptyEventData(), "Game started.");
    }

    private List<ManualBotScheduler.Entry> pendingWithDelay(long ms) {
        return scheduler.pending().stream().filter(e -> e.delay().equals(Duration.ofMillis(ms))).toList();
    }

    // ---- passing ----

    @Test
    void aBotSeatSchedulesOnePassTaskForTheStepAndPassesTheCardTheStrategyChose() throws Exception {
        KiloDeMerde game = threeSeats(NO_SET, 1);
        bot.answer = view -> card("QH");

        start(game);

        assertThat(scheduler.pending()).hasSize(1);
        assertThat(scheduler.pending().get(0).delay()).isEqualTo(Duration.ofMillis(PASS_MS));
        scheduler.runNext();
        assertThat(game.chosen(S1)).contains(card("QH"));
        assertThat(bot.views).hasSize(1);
        assertThat(bot.views.get(0)).isEqualTo(KiloDeMerdeBotView.forSeat(game, S1));
        assertThat(levelsAsked).containsExactly(KiloDeMerdeBotLevel.MEDIUM);
    }

    @Test
    void theBotLevelComesFromTheSeatOption() {
        KiloDeMerde game = register(3, NO_SET, "hard", 1);

        start(game);
        scheduler.runNext();

        assertThat(levelsAsked).containsExactly(KiloDeMerdeBotLevel.HARD);
    }

    @Test
    void aBotPassesOncePerStepAndAgainOnTheNextStep() throws Exception {
        KiloDeMerde game = threeSeats(NO_SET, 1);
        start(game);
        scheduler.runNext();
        broadcaster.broadcast(game, "OPPONENT_DISCONNECTED", new EmptyEventData(), "");
        actions.choosePass(game.getId(), S0, card("QC")); // another broadcast in the same step

        assertThat(scheduler.pending()).isEmpty();
        assertThat(bot.views).hasSize(1);

        actions.choosePass(game.getId(), S2, card("JC"));

        assertThat(game.step()).isEqualTo(2);
        assertThat(scheduler.pending()).hasSize(1);
        assertThat(scheduler.pending().get(0).delay()).isEqualTo(Duration.ofMillis(PASS_MS));
    }

    @Test
    void aFailingStrategyFallsBackToTheDefaultPass() {
        KiloDeMerde game = threeSeats(NO_SET, 1);
        bot.answer = view -> {
            throw new IllegalStateException("boom");
        };
        List<Card> hand = game.hand(S1);
        start(game);

        scheduler.runNext();

        assertThat(game.chosen(S1)).contains(KiloDeMerdeRules.defaultPass(hand));
    }

    @Test
    void aStrategyThatProposesACardOutsideItsHandFallsBackToTheDefaultPass() {
        KiloDeMerde game = threeSeats(NO_SET, 1);
        bot.answer = view -> card("KS"); // seat 0's card
        List<Card> hand = game.hand(S1);
        start(game);

        scheduler.runNext();

        assertThat(game.chosen(S1)).contains(KiloDeMerdeRules.defaultPass(hand));
    }

    @Test
    void aBotNeverFalseSlapsWhenNobodyHoldsAFourOfAKind() {
        KiloDeMerde game = threeSeats(NO_SET, 1, 2);

        start(game);

        assertThat(scheduler.pending()).hasSize(2); // one pass task per bot, no slap task
        assertThat(pendingWithDelay(OWN_MS)).isEmpty();
        assertThat(pendingWithDelay(FOLLOW_MS)).isEmpty();
    }

    // ---- slapping ----

    @Test
    void aBotHoldingFourOfAKindSlapsAfterItsOwnSetDelay() {
        KiloDeMerde game = threeSeats(ALL_SETS, 1);

        start(game);

        assertThat(pendingWithDelay(OWN_MS)).hasSize(1);
        pendingWithDelay(OWN_MS).get(0).run();
        assertThat(game.phase()).isEqualTo(Phase.RACE);
        assertThat(game.slapOrder()).containsExactly(S1);
    }

    @Test
    void theOwnSetSlapIsDroppedWhenTheStepMovesOn() throws Exception {
        KiloDeMerde sets = threeSeats(ALL_SETS, 1);
        start(sets);
        ManualBotScheduler.Entry slap = pendingWithDelay(OWN_MS).get(0);
        sets.expirePassTimer(1); // the step resolves without any broadcast reaching the coordinator
        assertThat(sets.step()).isEqualTo(2);

        slap.run(); // the task re-checks its key and drops itself

        assertThat(sets.phase()).isEqualTo(Phase.PASSING);
    }

    @Test
    void everyBotFollowsARaceAndASlapOfAnotherSeatDoesNotCancelTheOthers() throws Exception {
        KiloDeMerde game = register(4, ALL_SETS + " / 2S 2H 2D 2C", null, 1, 2, 3);
        start(game);
        scheduler.all().forEach(e -> {
            if (!e.isCancelled() && !e.hasRun()) {
                e.cancel();
            }
        });

        actions.slap(game.getId(), S0); // the human opens the race

        List<ManualBotScheduler.Entry> follows = pendingWithDelay(FOLLOW_MS);
        assertThat(follows).hasSize(3);
        follows.get(0).run(); // one bot slaps: its broadcast re-runs the coordinator

        assertThat(game.slapOrder()).hasSize(2);
        assertThat(follows.get(1).isCancelled()).isFalse();
        assertThat(follows.get(2).isCancelled()).isFalse();

        follows.get(1).run();

        assertThat(game.slapOrder()).hasSize(3);
        assertThat(game.phase()).isEqualTo(Phase.ROUND_OVER); // the third bot was last and drew
        assertThat(follows.get(2).isCancelled()).isTrue();
    }

    @Test
    void aBotThatAlreadySlappedDoesNotFollowAgain() throws Exception {
        KiloDeMerde game = register(4, ALL_SETS + " / 2S 2H 2D 2C", null, 1);
        start(game);
        actions.slap(game.getId(), S0);
        pendingWithDelay(FOLLOW_MS).get(0).run();
        assertThat(game.slapOrder()).containsExactly(S0, S1);

        broadcaster.broadcast(game, "OPPONENT_DISCONNECTED", new EmptyEventData(), ""); // the race is still open

        assertThat(game.phase()).isEqualTo(Phase.RACE);
        assertThat(pendingWithDelay(FOLLOW_MS)).isEmpty();
    }

    // ---- keys, lifecycle ----

    @Test
    void aTaskOfAVoidedDealDoesNotActOnTheNewDeal() {
        KiloDeMerde game = register(4, NO_SET + " / 2S 2H 2D 2C", null, 2);
        start(game);
        ManualBotScheduler.Entry before = scheduler.pending().get(0);

        game.forfeit(S1); // round 1, step 1 again, three seats: a different deal under the same numbers
        before.run();

        assertThat(game.hasChosen(S2)).isFalse();
        assertThat(bot.views).isEmpty();

        broadcaster.broadcast(game, "FORFEIT", new EmptyEventData(), "");

        assertThat(scheduler.pending()).hasSize(1); // a fresh pass task for the new deal
    }

    @Test
    void aRoundEndCancelsTheOpenTasks() throws Exception {
        KiloDeMerde game = threeSeats(NO_SET, 1, 2);
        start(game);
        List<ManualBotScheduler.Entry> passes = scheduler.pending();

        actions.slap(game.getId(), S0); // false slap: round over

        assertThat(passes).allMatch(ManualBotScheduler.Entry::isCancelled);
        assertThat(scheduler.pending()).isEmpty();
    }

    @Test
    void aGameWithoutBotsOrAFinishedGameSchedulesNothing() {
        KiloDeMerde humans = threeSeats(NO_SET);
        start(humans);
        assertThat(scheduler.all()).isEmpty();

        KiloDeMerde game = threeSeats(NO_SET, 1);
        start(game);
        assertThat(scheduler.pending()).hasSize(1);
        game.forfeit(S2);
        broadcaster.broadcast(game, "FORFEIT", new EmptyEventData(), "");

        assertThat(game.isFinished()).isTrue();
        assertThat(scheduler.pending()).isEmpty();
    }

    @Test
    void anEvictedGameCancelsItsTasks() {
        KiloDeMerde game = threeSeats(NO_SET, 1);
        start(game);
        ManualBotScheduler.Entry task = scheduler.pending().get(0);

        coordinator.onEvicted(game.getId());

        assertThat(task.isCancelled()).isTrue();
    }

    @Test
    void aRematchUnderTheSameIdGetsAFreshTable() {
        KiloDeMerde game = threeSeats(NO_SET, 1);
        start(game);
        ManualBotScheduler.Entry old = scheduler.pending().get(0);
        KiloDeMerde rematch = new KiloDeMerde(game.getId(), 3, decks, KiloDeMerdeOptions.defaults());
        SessionGame session = SessionGame.create(rematch.getId(), rematch.getPlayerIds(), KiloDeMerdeFactory.GAME_TYPE);
        session.claimHost("Alice");
        session.claimBot(null);
        session.claimNextFreeSeat("Human");
        repository.save(rematch, session);

        start(rematch);

        assertThat(old.isCancelled()).isTrue();
        assertThat(scheduler.pending()).hasSize(1);
    }
}
