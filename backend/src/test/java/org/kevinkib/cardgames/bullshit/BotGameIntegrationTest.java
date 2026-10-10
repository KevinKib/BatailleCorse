package org.kevinkib.cardgames.bullshit;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.domain.bot.BotTuning;
import org.kevinkib.cardgames.bullshit.domain.bot.ProbabilisticBotStrategy;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimModeOption;
import org.kevinkib.cardgames.bullshit.domain.player.Player;
import org.kevinkib.cardgames.bullshit.presentation.BullshitGameActions;
import org.kevinkib.cardgames.bullshit.presentation.BullshitStateBroadcaster;
import org.kevinkib.cardgames.bullshit.presentation.bot.BullshitBotCoordinator;
import org.kevinkib.cardgames.bullshit.presentation.bot.FixedThinkingDelay;
import org.kevinkib.cardgames.bullshit.presentation.bot.ManualBotScheduler;
import org.kevinkib.cardgames.bullshit.domain.options.BullshitOptions;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cards.domain.Card;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

/**
 * Whole-game scenarios through the real lobby, session core, broadcaster, coordinator and use cases,
 * with a manual scheduler and seeded randomness: no thread, no sleeping, no wall clock.
 */
class BotGameIntegrationTest {

    private static final int STEP_BOUND = 20_000;

    /** A room (host plus {@code bots} bots) started through the session service, bots driven by a manual scheduler. */
    private static final class Table {
        final SessionService sessions;
        final ManualBotScheduler scheduler = new ManualBotScheduler();
        final BullshitGameActions actions;
        final BullshitBotCoordinator coordinator;
        final List<Object> humanMessages = new ArrayList<>();
        final Bullshit game;
        final GameId id;
        final String hostToken;

        Table(String claimMode, int bots, long seed) {
            sessions = new SessionService(
                    new InMemorySessionRepository(Clock.systemUTC()),
                    new GameFactories(List.of(new BullshitFactory())));
            GameMessagingService messaging = new GameMessagingService(null, null) {
                @Override
                public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
                    if (sessions.botSeats(gameId).contains(seat)) {
                        throw new AssertionError("A bot seat was messaged: " + seat);
                    }
                    humanMessages.add(payload);
                }
            };
            BullshitStateBroadcaster broadcaster = new BullshitStateBroadcaster(messaging, sessions::botSeats);
            actions = new BullshitGameActions(sessions, broadcaster);
            Random random = new Random(seed);
            coordinator = new BullshitBotCoordinator(sessions::botSeats, actions, scheduler,
                    new FixedThinkingDelay(Duration.ZERO),
                    g -> new ProbabilisticBotStrategy(g.getClaimMode(), random, BotTuning.DEFAULT));
            broadcaster.addListener(coordinator);

            RoomCreated room = sessions.createRoom(BullshitFactory.GAME_TYPE, "Alice",
                    GameOptions.of(Map.of(BullshitOptions.CLAIM_MODE_KEY, claimMode)));
            id = new GameId(room.gameId());
            hostToken = room.hostToken();
            for (int i = 0; i < bots; i++) {
                sessions.addBot(id, hostToken);
            }
            game = (Bullshit) sessions.startGame(id, hostToken);
            broadcaster.broadcast(game, "START", new org.kevinkib.cardgames.presentation.dto.event.EmptyEventData(), "Game started.");
        }
    }

    /**
     * Plays the human seat like a simple player: honest when it holds the target, otherwise a one-card lie,
     * and a call only when declining would hand the win to someone else.
     */
    private static boolean humanActs(Table table) throws Exception {
        Bullshit game = table.game;
        PlayerId human = new PlayerId(0);
        if (game.getAvailableActions(human).isEmpty()) {
            return false;
        }
        if (game.getPendingWinner().isPresent() && game.getCurrentPlayer().id().equals(human)) {
            table.actions.callBullshit(table.id, human);
            return true;
        }
        if (game.getCurrentPlayer().id().equals(human)) {
            Player me = game.getPlayers().get(0);
            List<Card> matching = me.getCards().stream()
                    .filter(c -> game.getClaimMode().matches(List.of(c), game.getCurrentTarget()))
                    .limit(4)
                    .toList();
            table.actions.discard(table.id, human, matching.isEmpty() ? List.of(me.getCards().get(0)) : matching);
            return true;
        }
        return false;
    }

    /** Runs the game one step at a time: a due bot task (in a seeded order), else the human. Returns the steps used. */
    private static int play(Table table, long seed) throws Exception {
        Random order = new Random(seed);
        int steps = 0;
        while (!table.game.isFinished() && steps < STEP_BOUND) {
            List<ManualBotScheduler.Entry> due = table.scheduler.pending();
            if (!due.isEmpty()) {
                due.get(order.nextInt(due.size())).run();
            } else if (!humanActs(table)) {
                throw new AssertionError("The table stalled at step " + steps + " with nobody able to act");
            }
            steps++;
        }
        return steps;
    }

    @Test
    void givenOneHumanAndTwoBots_whenPlayedInSuitMode_thenEveryGameReachesAWinnerWithoutStalling() throws Exception {
        for (long seed = 1; seed <= 8; seed++) {
            Table table = new Table(ClaimModeOption.SUIT.key(), 2, seed);

            play(table, seed);

            assertThat("seed " + seed, table.game.isFinished(), is(true));
            assertThat(table.game.getWinner() != null, is(true));
        }
    }

    @Test
    void givenOneHumanAndOneBot_whenPlayedInSuitMode_thenTheGameReachesAWinner() throws Exception {
        for (long seed = 1; seed <= 8; seed++) {
            Table table = new Table(ClaimModeOption.SUIT.key(), 1, seed);

            play(table, seed);

            assertThat("seed " + seed, table.game.isFinished(), is(true));
        }
    }

    @Test
    void givenOneHumanAndFourBots_whenPlayedInRankMode_thenTheGameReachesAWinnerWithoutStalling() throws Exception {
        for (long seed = 1; seed <= 4; seed++) {
            Table table = new Table(ClaimModeOption.RANK.key(), 4, seed);

            play(table, seed);

            assertThat("seed " + seed, table.game.isFinished(), is(true));
        }
    }

    @Test
    void givenBotSeats_thenTheyNeverReceiveAMessageAndTheHumanAlwaysDoes() throws Exception {
        Table table = new Table(ClaimModeOption.SUIT.key(), 2, 3);

        play(table, 3);

        assertThat(table.humanMessages.isEmpty(), is(false));
    }

    @Test
    void givenAllHumansForfeited_thenTheCoordinatorStopsSchedulingAndTheGameIsLeftToEviction() throws Exception {
        Table table = new Table(ClaimModeOption.SUIT.key(), 2, 5);
        // let the human play once so the bots start answering
        assertThat(humanActs(table), is(true));
        assertThat(table.scheduler.pending().isEmpty(), is(false));

        table.game.forfeit(new PlayerId(0));
        table.coordinator.onBroadcast(table.game, "FORFEIT",
                new org.kevinkib.cardgames.presentation.dto.event.EmptyEventData());

        assertThat(table.game.isFinished(), is(false));
        assertThat(table.scheduler.pending(), is(empty()));
    }
}
