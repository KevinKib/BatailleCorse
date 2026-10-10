package org.kevinkib.cardgames.bullshit.presentation.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.domain.bot.BotDecision;
import org.kevinkib.cardgames.bullshit.domain.bot.BotMemory;
import org.kevinkib.cardgames.bullshit.domain.bot.BotObservation;
import org.kevinkib.cardgames.bullshit.domain.bot.BotStrategy;
import org.kevinkib.cardgames.bullshit.domain.claim.RankTarget;
import org.kevinkib.cardgames.bullshit.presentation.BullshitGameActions;
import org.kevinkib.cardgames.bullshit.presentation.BullshitStateBroadcaster;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.bullshit.domain.BullshitBuilder.aBullshit;
import static org.kevinkib.cardgames.bullshit.domain.BullshitFixtures.playerWithRanks;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.ACE;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.FIVE;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.FOUR;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.KING;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.NINE;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.THREE;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.TWO;

class BullshitBotCoordinatorTest {

    private static final Duration DELAY = Duration.ofMillis(1500);

    static final class RecordingMessaging extends GameMessagingService {
        final List<PlayerId> seats = new ArrayList<>();
        final List<Response> payloads = new ArrayList<>();

        RecordingMessaging() {
            super(null, null);
        }

        @Override
        public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
            seats.add(seat);
            payloads.add((Response) payload);
        }
    }

    private InMemorySessionRepository repository;
    private SessionService sessionService;
    private RecordingMessaging messaging;
    private BullshitStateBroadcaster broadcaster;
    private BullshitGameActions actions;
    private ManualBotScheduler scheduler;
    private Function<BotObservation, BotDecision> script;
    private final AtomicInteger decisions = new AtomicInteger();
    private BullshitBotCoordinator coordinator;

    @BeforeEach
    void setUp() {
        repository = new InMemorySessionRepository(Clock.systemUTC());
        sessionService = new SessionService(repository, new GameFactories(List.of(new BullshitFactory())));
        messaging = new RecordingMessaging();
        broadcaster = new BullshitStateBroadcaster(messaging, sessionService::botSeats);
        actions = new BullshitGameActions(sessionService, broadcaster);
        scheduler = new ManualBotScheduler();
        script = observation -> new BotDecision.Pass();
        buildCoordinator(actions);
    }

    private void buildCoordinator(BullshitGameActions gameActions) {
        BotStrategy strategy = (observation, memory) -> {
            decisions.incrementAndGet();
            return script.apply(observation);
        };
        coordinator = new BullshitBotCoordinator(
                sessionService::botSeats, gameActions, scheduler, new FixedThinkingDelay(DELAY), game -> strategy);
        broadcaster.addListener(coordinator);
    }

    /** Registers a crafted game: seat 0 is the human host, every other seat a bot. */
    private Bullshit register(Bullshit game) {
        SessionGame session = SessionGame.create(game.getId(), game.getPlayerIds().size(), "bullshit");
        session.claimHost("Alice");
        for (int i = 1; i < game.getPlayerIds().size(); i++) {
            session.claimBot(null);
        }
        repository.save(game, session);
        return game;
    }

    private void start(Bullshit game) {
        broadcaster.broadcast(game, "START", new EmptyEventData(), "Game started.");
    }

    private Bullshit botOnTurnGame() {
        return register(aBullshit()
                .withPlayers(playerWithRanks(0, ACE, KING), playerWithRanks(1, TWO, THREE))
                .withCurrentTarget(TWO)
                .withCurrentPlayerIndex(1)
                .build());
    }

    /** Human on turn with two bots behind: after the human plays, both bots have something to do. */
    private Bullshit threeSeatGame() {
        return register(aBullshit()
                .withPlayers(playerWithRanks(0, ACE, KING), playerWithRanks(1, TWO, NINE), playerWithRanks(2, FOUR, FIVE))
                .build());
    }

    private static Card card(FrenchRank rank, FrenchSuit suit) {
        return new Card(rank, suit);
    }

    // ---- scheduling --------------------------------------------------------------------------

    @Test
    void givenBotOnTurn_whenStateChanges_thenOneTaskWithTheInjectedDelayThatPlaysTheDecision() {
        Bullshit game = botOnTurnGame();
        script = obs -> new BotDecision.Discard(List.of(obs.hand().get(0)));

        start(game);

        assertThat(scheduler.pending().size(), is(1));
        assertThat(scheduler.pending().get(0).delay(), is(DELAY));
        messaging.seats.clear();
        messaging.payloads.clear();

        scheduler.runNext();

        assertThat(game.getDiscardPileSize(), is(1));
        assertThat(game.getCurrentPlayerIndex(), is(0));
        assertThat(messaging.seats, contains(new PlayerId(0)));
        assertThat(messaging.payloads.get(0).getEventType(), is("DISCARD"));
    }

    @Test
    void givenHumanOnTurn_whenStateChanges_thenNothingIsScheduled() {
        Bullshit game = register(aBullshit()
                .withPlayers(playerWithRanks(0, ACE), playerWithRanks(1, TWO))
                .build());

        start(game);

        assertThat(scheduler.all(), is(empty()));
    }

    @Test
    void givenClaimOnTable_thenEveryBotWithAnActionGetsATaskAndTheFirstToRunWins() throws Exception {
        Bullshit game = threeSeatGame();
        start(game);
        script = obs -> obs.me().equals(new PlayerId(2))
                ? new BotDecision.CallBullshit()
                : new BotDecision.Discard(List.of(obs.hand().get(0)));

        actions.discard(game.getId(), new PlayerId(0), List.of(game.getPlayers().get(0).getCards().get(0)));

        List<ManualBotScheduler.Entry> thinking = scheduler.pending();
        assertThat(thinking.size(), is(2)); // seat 1 (turn and call) and seat 2 (call)

        thinking.get(1).run(); // seat 2 calls first

        assertThat(game.getLastDiscard().isPresent(), is(false));
        assertThat(thinking.get(0).isCancelled(), is(true)); // seat 1's stale thinking is gone
        assertThat(decisions.get(), is(1));
    }

    @Test
    void givenStaleTask_whenItFiresAfterAnotherActionNobodyBroadcast_thenItDoesNothing() throws Exception {
        Bullshit game = threeSeatGame();
        start(game);
        actions.discard(game.getId(), new PlayerId(0), List.of(game.getPlayers().get(0).getCards().get(0)));
        assertThat(scheduler.pending().size(), is(2));

        game.discard(new PlayerId(1), List.of(game.getPlayers().get(1).getCards().get(0))); // version moves, no broadcast
        script = obs -> {
            throw new AssertionError("a stale bot must not even think");
        };
        scheduler.runAllPending();

        assertThat(decisions.get(), is(0));
        assertThat(game.getDiscardPileSize(), is(2));
    }

    @Test
    void givenPendingTasks_whenTheStateChangesAgain_thenThePreviousTasksAreCancelled() throws Exception {
        Bullshit game = threeSeatGame();
        start(game);
        actions.discard(game.getId(), new PlayerId(0), List.of(game.getPlayers().get(0).getCards().get(0)));
        List<ManualBotScheduler.Entry> first = scheduler.pending();

        actions.callBullshit(game.getId(), new PlayerId(2));

        assertThat(first.stream().allMatch(ManualBotScheduler.Entry::isCancelled), is(true));
    }

    @Test
    void givenNonStateEvent_whenBroadcast_thenPendingTasksAreKept() {
        Bullshit game = botOnTurnGame();
        start(game);
        ManualBotScheduler.Entry thinking = scheduler.pending().get(0);

        broadcaster.broadcast(game, "OPPONENT_DISCONNECTED", new EmptyEventData(), "Player 0 disconnected.");

        assertThat(thinking.isCancelled(), is(false));
        assertThat(scheduler.all().size(), is(1));
    }

    // ---- end of game and humans leaving -------------------------------------------------------

    @Test
    void givenFinishedGame_thenTasksAreCancelledAndTheGameForgotten() {
        Bullshit game = botOnTurnGame();
        start(game);
        ManualBotScheduler.Entry thinking = scheduler.pending().get(0);

        game.forfeit(new PlayerId(0)); // the bot is the last one standing
        broadcaster.broadcast(game, "FORFEIT", new EmptyEventData(), "Player 0 forfeited.");

        assertThat(game.isFinished(), is(true));
        assertThat(thinking.isCancelled(), is(true));
        assertThat(scheduler.pending(), is(empty()));
        assertThat(coordinator.trackedGameCount(), is(0));
    }

    @Test
    void givenNoHumanLeftInALiveGame_thenNothingIsScheduledAndPendingTasksAreCancelled() {
        Bullshit game = register(aBullshit()
                .withPlayers(playerWithRanks(0, ACE), playerWithRanks(1, TWO), playerWithRanks(2, THREE))
                .withCurrentTarget(TWO)
                .withCurrentPlayerIndex(1)
                .build());
        start(game);
        assertThat(scheduler.pending().size(), is(1));

        game.forfeit(new PlayerId(0));
        broadcaster.broadcast(game, "FORFEIT", new EmptyEventData(), "Player 0 forfeited.");

        assertThat(game.isFinished(), is(false));
        assertThat(scheduler.pending(), is(empty()));
        assertThat(coordinator.trackedGameCount(), is(0));
    }

    @Test
    void givenEvictedGame_thenItsTasksAreCancelled() {
        Bullshit game = botOnTurnGame();
        start(game);
        ManualBotScheduler.Entry thinking = scheduler.pending().get(0);

        coordinator.onEvicted(game.getId());

        assertThat(thinking.isCancelled(), is(true));
        assertThat(coordinator.trackedGameCount(), is(0));
    }

    @Test
    void givenGameWithoutBots_thenTheCoordinatorIgnoresIt() {
        Bullshit game = aBullshit()
                .withPlayers(playerWithRanks(0, ACE), playerWithRanks(1, TWO))
                .build();
        SessionGame session = SessionGame.create(game.getId(), 2, "bullshit");
        session.claimHost("Alice");
        session.claimNextFreeSeat("Bob");
        repository.save(game, session);

        start(game);

        assertThat(scheduler.all(), is(empty()));
        assertThat(coordinator.trackedGameCount(), is(0));
    }

    // ---- decisions ---------------------------------------------------------------------------

    @Test
    void givenPass_thenNothingIsAppliedAndNothingNewIsScheduled() {
        Bullshit game = botOnTurnGame();
        int version = game.version();
        start(game);

        scheduler.runNext();

        assertThat(decisions.get(), is(1));
        assertThat(game.version(), is(version));
        assertThat(game.getDiscardPileSize(), is(0));
        assertThat(scheduler.pending(), is(empty()));
    }

    @Test
    void givenStrategyThrows_thenTheBotDiscardsItsLowestCardSoTheTableDoesNotStall() {
        Bullshit game = register(aBullshit()
                .withPlayers(playerWithRanks(0, ACE), playerWithRanks(1, NINE, THREE, KING))
                .withCurrentTarget(TWO)
                .withCurrentPlayerIndex(1)
                .build());
        script = obs -> {
            throw new IllegalStateException("boom");
        };
        start(game);

        scheduler.runNext();

        assertThat(game.getDiscardPileSize(), is(1));
        assertThat(game.getLastDiscard().orElseThrow().actualCards().get(0).getRank() == THREE, is(true));
        assertThat(game.getCurrentPlayerIndex(), is(0));
    }

    @Test
    void givenDecisionTheRulesRefuse_thenFallsBackToTheLowestCard() {
        Bullshit game = botOnTurnGame();
        script = obs -> new BotDecision.Discard(List.of(card(KING, FrenchSuit.CLUB))); // not in the bot's hand
        start(game);

        scheduler.runNext();

        assertThat(game.getLastDiscard().orElseThrow().actualCards().get(0).getRank() == TWO, is(true));
    }

    @Test
    void givenFallbackFailsToo_thenItIsOnlyLoggedAndNothingEscapes() {
        BullshitGameActions failing = new BullshitGameActions(sessionService, broadcaster) {
            @Override
            public void discard(GameId gameId, PlayerId playerId, List<Card> cards) {
                throw new IllegalStateException("cannot discard");
            }
        };
        scheduler = new ManualBotScheduler();
        buildCoordinator(failing);
        Bullshit game = botOnTurnGame();
        script = obs -> new BotDecision.Discard(List.of(obs.hand().get(0)));
        start(game);

        scheduler.runNext(); // must not throw

        assertThat(game.getDiscardPileSize(), is(0));
    }

    // ---- memory ------------------------------------------------------------------------------

    @Test
    void givenPublicEvents_thenMemoryTracksOwnPileCardsAndReveals() throws Exception {
        Bullshit game = threeSeatGame();
        start(game);
        script = obs -> obs.me().equals(new PlayerId(1)) && obs.can(org.kevinkib.cardgames.bullshit.domain.Action.DISCARD)
                ? new BotDecision.Discard(List.of(card(NINE, FrenchSuit.HEART)))
                : new BotDecision.Pass();
        actions.discard(game.getId(), new PlayerId(0), List.of(game.getPlayers().get(0).getCards().get(0)));

        scheduler.pending().get(0).run(); // bot 1 plays its NINE as a TWO

        assertThat(coordinator.memoryOf(game.getId(), new PlayerId(1)).knownPileCards().size(), is(1));

        actions.callBullshit(game.getId(), new PlayerId(0)); // caught: bot 1 picks the pile up

        assertThat(coordinator.memoryOf(game.getId(), new PlayerId(1)).knownPileCards(), is(empty()));
        assertThat(coordinator.memoryOf(game.getId(), new PlayerId(2))
                .knownMatches(new PlayerId(1), new RankTarget(NINE), game.getClaimMode()), is(1));
        assertThat(coordinator.memoryOf(game.getId(), new PlayerId(1))
                .knownMatches(new PlayerId(1), new RankTarget(NINE), game.getClaimMode()), is(0));
    }

    @Test
    void givenClaimantsKnownCards_thenTheyAreForgottenOnceTheirClaimIsResolved() throws Exception {
        Bullshit game = threeSeatGame();
        start(game);
        actions.discard(game.getId(), new PlayerId(0), List.of(game.getPlayers().get(0).getCards().get(0)));
        // bot 2 calls a truthful ACE: it picks the pile up, and bot 1 learns that seat 2 holds the ace
        actions.callBullshit(game.getId(), new PlayerId(2));
        BotMemory bot1 = coordinator.memoryOf(game.getId(), new PlayerId(1));
        assertThat(bot1.knownMatches(new PlayerId(2), new RankTarget(ACE), game.getClaimMode()), is(1));

        // seat 2 now claims a TWO with a four: while that claim is on the table bot 1 still reasons with what it knew
        actions.discard(game.getId(), new PlayerId(2), List.of(card(FOUR, FrenchSuit.HEART)));
        assertThat(bot1.knownMatches(new PlayerId(2), new RankTarget(ACE), game.getClaimMode()), is(1));

        // the claim is called: seat 2 takes the pile back, the old knowledge is dropped and the reveal recorded
        actions.callBullshit(game.getId(), new PlayerId(0));
        assertThat(bot1.knownMatches(new PlayerId(2), new RankTarget(ACE), game.getClaimMode()), is(0));
        assertThat(bot1.knownMatches(new PlayerId(2), new RankTarget(FOUR), game.getClaimMode()), is(1));
    }
}
