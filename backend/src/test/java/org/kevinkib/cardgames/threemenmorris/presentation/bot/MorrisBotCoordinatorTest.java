package org.kevinkib.cardgames.threemenmorris.presentation.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.bot.FixedThinkingDelay;
import org.kevinkib.cardgames.presentation.bot.ManualBotScheduler;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisResult;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;
import org.kevinkib.cardgames.threemenmorris.domain.bot.MorrisBot;
import org.kevinkib.cardgames.threemenmorris.domain.bot.MorrisBotLevel;
import org.kevinkib.cardgames.threemenmorris.domain.bot.MorrisDecision;
import org.kevinkib.cardgames.threemenmorris.presentation.ThreeMenMorrisGameActions;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.ThreeMenMorrisDto;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/** The coordinator drives the bot seat after each broadcast, with a manual scheduler and a scripted strategy. */
class MorrisBotCoordinatorTest {

    private static final Duration DELAY = Duration.ofMillis(900);
    private static final PlayerId HUMAN = new PlayerId(0);
    private static final PlayerId BOT = new PlayerId(1);

    private InMemorySessionRepository repository;
    private SessionService sessions;
    private RecordingMessaging messaging;
    private SeatStateBroadcaster<ThreeMenMorris, ThreeMenMorrisDto> broadcaster;
    private ThreeMenMorrisGameActions actions;
    private ManualBotScheduler scheduler;
    private MorrisBotCoordinator coordinator;

    private final List<MorrisBotLevel> levelsAsked = new ArrayList<>();
    private final List<Boolean> offersAnswered = new ArrayList<>();
    private MorrisDecision scripted;

    /** The strategy the coordinator is given for every level: plays what the test scripts. */
    private final MorrisBot scriptedBot = (position, seat, offerPending) -> {
        offersAnswered.add(offerPending);
        return scripted;
    };

    @BeforeEach
    void setUp() {
        repository = new InMemorySessionRepository(Clock.systemUTC());
        sessions = new SessionService(repository, new GameFactories(List.of(new ThreeMenMorrisFactory())));
        messaging = new RecordingMessaging(sessions);
        broadcaster = new SeatStateBroadcaster<>(messaging, sessions::botSeats, sessions::seatNames,
                (game, viewer, names, bots) -> ThreeMenMorrisDto.forViewer(game, viewer, names));
        actions = new ThreeMenMorrisGameActions(sessions, broadcaster);
        scheduler = new ManualBotScheduler();
        coordinator = new MorrisBotCoordinator(sessions::botSeats, sessions::seatOptions, actions, scheduler,
                new FixedThinkingDelay(DELAY), level -> {
                    levelsAsked.add(level);
                    return scriptedBot;
                });
        broadcaster.addListener(coordinator);
    }

    /** Registers a game: seat 0 is the human host, seat 1 a bot (with the given level option, if any). */
    private ThreeMenMorris register(ThreeMenMorris game, String level) {
        SessionGame session = SessionGame.create(game.getId(), 2, "three-men-morris");
        session.claimHost("Alice");
        session.claimBot(null);
        if (level != null) {
            session.setSeatOption(BOT, "level", level);
        }
        repository.save(game, session);
        return game;
    }

    private ThreeMenMorris register(ThreeMenMorris game) {
        return register(game, null);
    }

    private void start(ThreeMenMorris game) {
        broadcaster.broadcast(game, "START", new EmptyEventData(), "Game started.");
    }

    /** Placement is over: white (the human) has 0, 2, 7 and black (the bot) 1, 5, 8; white to move. */
    private ThreeMenMorris movementGame() throws Exception {
        ThreeMenMorris game = register(new ThreeMenMorris(GameId.generate()));
        for (int[] p : new int[][]{{0, 0}, {1, 1}, {0, 2}, {1, 5}, {0, 7}, {1, 8}}) {
            game.place(new PlayerId(p[0]), p[1]);
        }
        return game;
    }

    @Test
    void whenTheBotIsOnTurnOneTaskWithTheInjectedDelayPlaysTheDecision() throws Exception {
        ThreeMenMorris game = register(new ThreeMenMorris(GameId.generate()));
        scripted = new MorrisDecision.Place(4);
        start(game);
        assertThat(scheduler.pending().size(), is(0)); // the human (white) is on turn

        actions.place(game.getId(), HUMAN, 0);

        assertThat(scheduler.pending().size(), is(1));
        assertThat(scheduler.pending().get(0).delay(), is(DELAY));
        messaging.clear();

        scheduler.runNext();

        assertThat(game.owner(4), is(BOT));
        assertThat(game.currentSeat(), is(HUMAN));
        assertThat(messaging.seats, contains(HUMAN)); // the bot seat gets no message
        assertThat(messaging.payloads.get(0).getEventType(), is("PLACE"));
    }

    @Test
    void aBotPlayingWhiteMovesFirstWhenTheGameStarts() {
        ThreeMenMorris game = register(new ThreeMenMorris(GameId.generate(), MorrisRules.TAPATAN, BOT));
        scripted = new MorrisDecision.Place(4);

        start(game);
        assertThat(scheduler.pending().size(), is(1));
        scheduler.runNext();

        assertThat(game.owner(4), is(BOT));
    }

    @Test
    void theLevelIsReadFromTheSeatOptionAndDefaultsToGreedy() {
        ThreeMenMorris perfect = register(new ThreeMenMorris(GameId.generate(), MorrisRules.TAPATAN, BOT), "perfect");
        scripted = new MorrisDecision.Place(4);
        start(perfect);
        scheduler.runNext();

        ThreeMenMorris plain = register(new ThreeMenMorris(GameId.generate(), MorrisRules.TAPATAN, BOT));
        start(plain);
        scheduler.runNext();

        assertThat(levelsAsked, is(List.of(MorrisBotLevel.PERFECT, MorrisBotLevel.GREEDY)));
    }

    @Test
    void nothingIsScheduledWhileTheHumanIsOnTurn() {
        ThreeMenMorris game = register(new ThreeMenMorris(GameId.generate()));

        start(game);

        assertThat(scheduler.all(), is(empty()));
    }

    @Test
    void aStateBroadcastThatChangesNothingDoesNotRestartTheThinking() throws Exception {
        ThreeMenMorris game = register(new ThreeMenMorris(GameId.generate(), MorrisRules.TAPATAN, BOT));
        scripted = new MorrisDecision.Place(4);
        start(game);

        broadcaster.broadcast(game, "CONNECTED", new EmptyEventData(), "Alice is back.");

        assertThat(scheduler.pending().size(), is(1));
        assertThat(scheduler.cancelledCount(), is(0L));
    }

    @Test
    void aTaskIsDroppedWhenTheStateMovedWithoutABroadcast() throws Exception {
        ThreeMenMorris game = movementGame();
        scripted = new MorrisDecision.Step(1, 4);
        actions.move(game.getId(), HUMAN, 0, 3);
        assertThat(scheduler.pending().size(), is(1));

        game.offerDraw(HUMAN); // the version moves, nobody broadcast
        scheduler.runAllPending();

        assertThat(offersAnswered, is(empty())); // the stale bot did not even think
        assertThat(game.owner(4), is(nullValue()));
    }

    @Test
    void theNextBroadcastCancelsThePreviousTasks() throws Exception {
        ThreeMenMorris game = movementGame();
        scripted = new MorrisDecision.Step(1, 4);
        actions.move(game.getId(), HUMAN, 0, 3);
        ManualBotScheduler.Entry first = scheduler.pending().get(0);

        actions.offerDraw(game.getId(), HUMAN);

        assertThat(first.isCancelled(), is(true));
    }

    @Test
    void aPendingDrawOfferIsAnsweredByTheBotAndAnAcceptEndsTheGame() throws Exception {
        ThreeMenMorris game = movementGame();
        scripted = new MorrisDecision.AcceptDraw();
        actions.offerDraw(game.getId(), HUMAN); // white offers while it is its own turn

        assertThat(scheduler.pending().size(), is(1));
        scheduler.runNext();

        assertThat(offersAnswered, is(List.of(true)));
        assertThat(game.result().reason(), is(MorrisResult.Reason.DRAW_AGREED));
    }

    @Test
    void aDeclinedOfferEndsWithNoBotTaskWhenItIsTheHumansTurn() throws Exception {
        ThreeMenMorris game = movementGame();
        scripted = new MorrisDecision.DeclineDraw();
        actions.offerDraw(game.getId(), HUMAN);

        scheduler.runNext();

        assertThat(game.drawOffer(), is(nullValue()));
        assertThat(game.currentSeat(), is(HUMAN));
        assertThat(scheduler.pending(), is(empty()));
    }

    @Test
    void whenTheBotIsOnTurnWithAnOfferPendingItAnswersFirstAndMovesAfterTheDecline() throws Exception {
        ThreeMenMorris game = movementGame();
        actions.move(game.getId(), HUMAN, 0, 3); // the bot is on turn: a move task is pending
        actions.offerDraw(game.getId(), HUMAN); // the offer replaces it with the answer
        assertThat(scheduler.pending().size(), is(1));
        scripted = new MorrisDecision.DeclineDraw();

        scheduler.runNext();

        assertThat(offersAnswered, is(List.of(true)));
        assertThat(game.drawOffer(), is(nullValue()));
        assertThat(game.currentSeat(), is(BOT));
        assertThat(scheduler.pending().size(), is(1)); // the decline woke it up for its move
        scripted = new MorrisDecision.Step(1, 4);

        scheduler.runNext();

        assertThat(offersAnswered, is(List.of(true, false)));
        assertThat(game.owner(4), is(BOT));
    }

    @Test
    void aBotNeverOffersADrawItself() throws Exception {
        ThreeMenMorris game = movementGame();
        scripted = new MorrisDecision.Step(1, 4);
        actions.move(game.getId(), HUMAN, 0, 3);

        scheduler.runNext();

        assertThat(game.drawOffer(), is(nullValue()));
    }

    @Test
    void aFailingStrategyFallsBackToTheFirstLegalPlacement() {
        ThreeMenMorris game = register(new ThreeMenMorris(GameId.generate(), MorrisRules.TAPATAN, BOT));
        scripted = new MorrisDecision.Place(99); // not a point: the game refuses it
        start(game);

        scheduler.runNext();

        assertThat(game.owner(0), is(BOT));
    }

    @Test
    void aFailingStrategyFallsBackToTheFirstLegalMove() throws Exception {
        ThreeMenMorris game = movementGame();
        scripted = new MorrisDecision.Step(1, 8); // 8 is occupied
        actions.move(game.getId(), HUMAN, 0, 3);

        scheduler.runNext();

        assertThat(game.owner(1), is(nullValue())); // the bot's first legal move: 1 to 0, freed by white
        assertThat(game.owner(0), is(BOT));
        assertThat(game.currentSeat(), is(HUMAN));
    }

    @Test
    void aTaskThatFiresAfterTheGameEndedWithoutABroadcastDoesNothing() throws Exception {
        ThreeMenMorris game = movementGame();
        scripted = new MorrisDecision.Step(1, 4);
        actions.move(game.getId(), HUMAN, 0, 3);

        game.forfeit(HUMAN); // the game is over, nobody broadcast
        scheduler.runAllPending();

        assertThat(offersAnswered, is(empty()));
        assertThat(game.result().reason(), is(MorrisResult.Reason.FORFEIT));
    }

    @Test
    void aForfeitStopsTheBot() throws Exception {
        ThreeMenMorris game = movementGame();
        scripted = new MorrisDecision.Step(1, 4);
        actions.move(game.getId(), HUMAN, 0, 3);
        ManualBotScheduler.Entry pending = scheduler.pending().get(0);

        game.forfeit(HUMAN);
        broadcaster.broadcast(game, "PLAYER_FORFEIT", new EmptyEventData(), "Alice left.");

        assertThat(pending.isCancelled(), is(true));
        assertThat(scheduler.pending(), is(empty()));
        assertThat(coordinator.trackedGameCount(), is(0));
    }

    @Test
    void aNewGameUnderTheSameIdAfterPlayAgainGetsItsOwnTable() {
        GameId id = GameId.generate();
        ThreeMenMorris first = register(new ThreeMenMorris(id, MorrisRules.TAPATAN, BOT));
        scripted = new MorrisDecision.Place(4);
        start(first);
        ManualBotScheduler.Entry stale = scheduler.pending().get(0);

        ThreeMenMorris second = register(new ThreeMenMorris(id, MorrisRules.TAPATAN, BOT));
        start(second);

        assertThat(stale.isCancelled(), is(true));
        assertThat(scheduler.pending().size(), is(1));
        scheduler.runNext();
        assertThat(second.owner(4), is(BOT));
        assertThat(first.owner(4), is(nullValue()));
        assertThat(coordinator.trackedGameCount(), is(1));
    }

    @Test
    void anEvictedGameIsForgottenAndItsTasksCancelled() {
        ThreeMenMorris game = register(new ThreeMenMorris(GameId.generate(), MorrisRules.TAPATAN, BOT));
        start(game);
        ManualBotScheduler.Entry pending = scheduler.pending().get(0);

        coordinator.onEvicted(game.getId());

        assertThat(pending.isCancelled(), is(true));
        assertThat(coordinator.trackedGameCount(), is(0));
    }

    @Test
    void aGameWithoutBotsIsNeverTracked() {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate());
        SessionGame session = SessionGame.create(game.getId(), 2, "three-men-morris");
        session.claimHost("Alice");
        session.claimNextFreeSeat("Bob");
        repository.save(game, session);

        start(game);

        assertThat(coordinator.trackedGameCount(), is(0));
        assertThat(scheduler.all(), is(empty()));
    }
}
