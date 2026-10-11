package org.kevinkib.cardgames.bataillecorse.presentation.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseScenario;
import org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseGameActions;
import org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseStateBroadcaster;
import org.kevinkib.cardgames.bataillecorse.presentation.RecordingMessaging;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.event.SlapEventData;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.bot.FixedThinkingDelay;
import org.kevinkib.cardgames.presentation.bot.ManualBotScheduler;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.*;

/** The bot of seat 1 plays against a human on seat 0, with a manual scheduler and a fixed 100 ms jitter. */
class BatailleCorseBotCoordinatorTest {

    private static final PlayerId HUMAN = new PlayerId(0);
    private static final PlayerId BOT = new PlayerId(1);
    private static final Duration JITTER = Duration.ofMillis(100);

    private final InMemorySessionRepository repository = new InMemorySessionRepository(Clock.systemUTC());
    private final SessionService sessions =
            new SessionService(repository, new GameFactories(List.of(new BatailleCorseFactory())));
    private final RecordingMessaging messaging = new RecordingMessaging();
    private final BatailleCorseStateBroadcaster broadcaster = new BatailleCorseStateBroadcaster(messaging);
    private final BatailleCorseGameActions actions = new BatailleCorseGameActions(sessions, broadcaster);
    private final ManualBotScheduler scheduler = new ManualBotScheduler();
    private final Map<String, String> botOptions = new HashMap<>();
    private Set<PlayerId> botSeats = Set.of(BOT);
    private Duration extraDelay = Duration.ZERO;
    private BatailleCorseBotCoordinator coordinator;

    /** Built on the first start, so a test can set the seats and the extra delay first. */
    private void wireCoordinator() {
        if (coordinator == null) {
            coordinator = new BatailleCorseBotCoordinator(
                    gameId -> botSeats, (gameId, seat) -> botOptions, sessions, actions, scheduler,
                    new FixedThinkingDelay(JITTER), extraDelay);
            broadcaster.addListener(coordinator);
        }
    }

    private BatailleCorse start(List<FrenchRank> human, List<FrenchRank> bot) {
        return start(BatailleCorseScenario.game(human, bot));
    }

    private BatailleCorse start(BatailleCorse game) {
        wireCoordinator();
        store(game);
        broadcaster.broadcast(game, "START", new EmptyEventData(), "Game started.");
        return game;
    }

    private void store(BatailleCorse game) {
        repository.save(game, SessionGame.create(game.getId(), game.getPlayerIds(), BatailleCorseFactory.GAME_TYPE));
    }

    private int movesBroadcast() {
        return (int) messaging.toGame.stream().filter(r -> !r.getEventType().equals("START")).count();
    }

    // ---- scheduling ------------------------------------------------------------------------

    @Test
    void startSchedulesOneTaskForTheBotAfterItsTierReactionTime() {
        start(List.of(TWO, FOUR), List.of(THREE, FIVE));

        assertThat(scheduler.pending(), hasSize(1));
        // Tier 1 (Bronze, 1800 ms): the reaction time minus the jitter bound, plus the jitter.
        assertThat(scheduler.pending().get(0).delay(), is(Duration.ofMillis(1800)));
    }

    @Test
    void theTierComesFromTheSeatOptions() {
        botOptions.put("tier", "8");

        start(List.of(TWO, FOUR), List.of(THREE, FIVE));

        assertThat(scheduler.pending().get(0).delay(), is(Duration.ofMillis(400)));
    }

    @Test
    void theSlowestTierWaitsTwoPointOneSeconds() {
        botOptions.put("tier", "0");

        start(List.of(TWO, FOUR), List.of(THREE, FIVE));

        assertThat(scheduler.pending().get(0).delay(), is(Duration.ofMillis(2100)));
    }

    @Test
    void anUnreadableTierFallsBackToTierOne() {
        botOptions.put("tier", "banana");

        start(List.of(TWO, FOUR), List.of(THREE, FIVE));

        assertThat(scheduler.pending().get(0).delay(), is(Duration.ofMillis(1800)));
    }

    @Test
    void theExtraDelayIsAddedToTheTierDelay() {
        extraDelay = Duration.ofMillis(250);

        start(List.of(TWO, FOUR), List.of(THREE, FIVE));

        assertThat(scheduler.pending().get(0).delay(), is(Duration.ofMillis(2050)));
    }

    @Test
    void aGameWithoutABotIsLeftAlone() {
        botSeats = Set.of();

        start(List.of(TWO, FOUR), List.of(THREE, FIVE));

        assertThat(scheduler.all(), is(empty()));
    }

    @Test
    void nothingIsScheduledWhenNoHumanIsLeft() {
        botSeats = Set.of(HUMAN, BOT);

        start(List.of(TWO, FOUR), List.of(THREE, FIVE));

        assertThat(scheduler.all(), is(empty()));
    }

    // ---- acting ----------------------------------------------------------------------------

    @Test
    void theBotDoesNothingWhenItIsNotItsTurn() {
        BatailleCorse game = start(List.of(TWO, FOUR), List.of(THREE, FIVE));

        scheduler.runNext();

        assertThat(movesBroadcast(), is(0));
        assertThat(game.getPileSize(), is(0));
    }

    @Test
    void theBotSendsOnItsTurnAfterAHumanMove() throws Exception {
        BatailleCorse game = start(List.of(TWO, FOUR), List.of(THREE, FIVE));
        actions.send(game.getId(), HUMAN);

        scheduler.runNext();

        assertThat(game.getPileSize(), is(2));
        assertThat(messaging.last().getEventType(), is("SEND"));
    }

    @Test
    void theBotSlapsATenAndTheSlapIsBroadcastForItsSeat() throws Exception {
        BatailleCorse game = start(List.of(TEN, FOUR), List.of(THREE, FIVE));
        actions.send(game.getId(), HUMAN);

        scheduler.runNext();

        assertThat(messaging.last().getEventType(), is("SLAP"));
        SlapEventData data = (SlapEventData) messaging.last().getEventData();
        assertThat(data.isSuccessful(), is(true));
        assertThat(data.player().getId(), is("1"));
    }

    @Test
    void theTaskOfAStateThatMovedOnIsCancelledAndReplaced() throws Exception {
        BatailleCorse game = start(List.of(TEN, FOUR), List.of(THREE, FIVE));
        actions.send(game.getId(), HUMAN);
        ManualBotScheduler.Entry beforeHumanSlap = scheduler.pending().get(0);

        actions.slap(game.getId(), HUMAN);

        assertThat(beforeHumanSlap.isCancelled(), is(true));
        assertThat(scheduler.pending(), hasSize(1));
    }

    @Test
    void aTaskStillRunsNothingWhenTheVersionMovedWithoutABroadcast() throws Exception {
        BatailleCorse game = start(List.of(TWO, FOUR), List.of(THREE, FIVE));
        game.send(game.getPlayerByIndex(0)); // state changed behind the coordinator's back

        scheduler.runNext();

        assertThat(game.getPileSize(), is(1));
        assertThat(movesBroadcast(), is(0));
    }

    @Test
    void aBroadcastThatIsNotAStateChangeKeepsTheBotThinking() {
        BatailleCorse game = start(List.of(TWO, FOUR), List.of(THREE, FIVE));
        ManualBotScheduler.Entry thinking = scheduler.pending().get(0);

        broadcaster.broadcast(game, "OPPONENT_DISCONNECTED", new EmptyEventData(), "gone");

        assertThat(thinking.isCancelled(), is(false));
        assertThat(scheduler.all(), hasSize(1));
    }

    // ---- auto-grab -------------------------------------------------------------------------

    private BatailleCorse botOwnsAFullPile() throws Exception {
        // Human 2, bot jack, human 4: one chance spent, the pile is full and the bot added the honour card.
        BatailleCorse game = start(List.of(TWO, FOUR, SIX), List.of(JACK, FIVE, SEVEN));
        actions.send(game.getId(), HUMAN);
        actions.send(game.getId(), BOT);
        actions.send(game.getId(), HUMAN);
        return game;
    }

    @Test
    void theBotGrabsItsPileAfterFifteenHundredMilliseconds() throws Exception {
        BatailleCorse game = botOwnsAFullPile();

        ManualBotScheduler.Entry grab = scheduler.pending().stream()
                .filter(entry -> entry.delay().equals(Duration.ofMillis(1500))).findFirst().orElseThrow();
        grab.run();

        assertThat(messaging.last().getEventType(), is("GRAB"));
        assertThat(game.getPileSize(), is(0));
    }

    @Test
    void theBotGrabsOnlyOnce() throws Exception {
        botOwnsAFullPile();
        long before = messaging.toGame.stream().filter(r -> r.getEventType().equals("GRAB")).count();

        scheduler.pending().stream().filter(entry -> entry.delay().equals(Duration.ofMillis(1500)))
                .findFirst().orElseThrow().run();
        scheduler.runAllPending();

        long grabs = messaging.toGame.stream().filter(r -> r.getEventType().equals("GRAB")).count();
        assertThat(grabs - before, is(1L));
    }

    @Test
    void theBotDoesNotGrabAPileThatBelongsToTheHuman() throws Exception {
        // Human jack, bot answers: the pile is full and the human added the honour card.
        BatailleCorse game = start(List.of(JACK, FOUR, SIX), List.of(THREE, FIVE, SEVEN));
        actions.send(game.getId(), HUMAN);
        actions.send(game.getId(), BOT);

        assertThat(scheduler.pending().stream().anyMatch(entry -> entry.delay().equals(Duration.ofMillis(1500))),
                is(false));
    }

    @Test
    void theAutoGrabIsCancelledWhenTheStateMovesOn() throws Exception {
        BatailleCorse game = botOwnsAFullPile();
        ManualBotScheduler.Entry grab = scheduler.pending().stream()
                .filter(entry -> entry.delay().equals(Duration.ofMillis(1500))).findFirst().orElseThrow();

        actions.slap(game.getId(), HUMAN); // a (failed) slap changes the state

        assertThat(grab.isCancelled(), is(true));
    }

    // ---- lifecycle -------------------------------------------------------------------------

    @Test
    void nothingActsOnceTheGameIsOver() {
        BatailleCorse game = start(List.of(TEN, FOUR), List.of(THREE, FIVE));
        ManualBotScheduler.Entry thinking = scheduler.pending().get(0);

        game.forfeit(HUMAN);
        broadcaster.broadcast(game, "FORFEIT", new EmptyEventData(), "forfeited");

        assertThat(thinking.isCancelled(), is(true));
        assertThat(scheduler.pending(), is(empty()));
    }

    @Test
    void evictionCancelsWhatIsPending() {
        BatailleCorse game = start(List.of(TWO, FOUR), List.of(THREE, FIVE));

        coordinator.onEvicted(game.getId());

        assertThat(scheduler.pending(), is(empty()));
    }

    @Test
    void whenTheLobbyIsReopenedAPendingTaskDoesNothing() throws Exception {
        BatailleCorse game = start(List.of(TWO, FOUR), List.of(THREE, FIVE));
        actions.send(game.getId(), HUMAN); // the bot now has a send to make
        repository.remove(game.getId()); // Play again: the game is dropped and the lobby reopens

        scheduler.runNext();

        assertThat(game.getPileSize(), is(1));
    }

    @Test
    void theNextStartAfterPlayAgainSchedulesTheBotAgain() throws Exception {
        BatailleCorse first = start(List.of(TWO, FOUR), List.of(THREE, FIVE));
        actions.send(first.getId(), HUMAN);
        ManualBotScheduler.Entry stale = scheduler.pending().get(0);
        repository.remove(first.getId());

        BatailleCorse second = start(BatailleCorseScenario.game(first.getId(), List.of(SIX, SEVEN), List.of(EIGHT, NINE)));

        assertThat(stale.isCancelled(), is(true));
        assertThat(scheduler.pending(), hasSize(1));
        actions.send(second.getId(), HUMAN);
        scheduler.runNext();
        assertThat(second.getPileSize(), is(2));
    }
}
