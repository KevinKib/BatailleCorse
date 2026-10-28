package org.kevinkib.cardgames.draughts.presentation.bot;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.config.DraughtsConfig;
import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.DraughtsFactory;
import org.kevinkib.cardgames.draughts.presentation.DraughtsGameActions;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * The Spring wiring of the draughts bots, with the real scheduler and the {@code test} profile (no thinking delay,
 * seeded generator): a human moves and the computer seat answers by itself.
 */
@SpringBootTest
@ActiveProfiles("test")
class DraughtsBotWiringIT {

    private static final PlayerId HUMAN = new PlayerId(0);
    private static final PlayerId BOT = new PlayerId(1);

    @Autowired
    @Qualifier("sessionService")
    private SessionService sessions;

    @Autowired
    private DraughtsGameActions actions;

    @Autowired
    private DraughtsBotCoordinator coordinator;

    @Autowired
    private List<GameEvictionListener> evictionListeners;

    @Autowired
    private Environment environment;

    private Draughts startedGameWithBot(String level) throws Exception {
        RoomCreated room = sessions.createRoom(DraughtsFactory.GAME_TYPE, "Alice");
        GameId id = new GameId(room.gameId());
        sessions.addBot(id, room.hostToken());
        sessions.setSeatOption(id, room.hostToken(), 1, DraughtsFactory.LEVEL_KEY, level);
        return (Draughts) sessions.startGame(id, room.hostToken());
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat("the awaited state was reached", condition.getAsBoolean(), is(true));
    }

    @Test
    void theCoordinatorIsABeanReleasedWhenAGameIsEvicted() {
        assertThat(coordinator, notNullValue());
        assertThat(evictionListeners.contains(coordinator), is(true));
    }

    @Test
    void aComputerSeatAnswersAHumanMoveAtEveryLevel() throws Exception {
        for (String level : List.of("random", "greedy", "expert")) {
            Draughts game = startedGameWithBot(level);
            assertThat(game.currentSeat(), is(HUMAN)); // a "random" host is White under the test profile
            actions.move(game.getId(), HUMAN, 32, 28, List.of());

            await(() -> game.isFinished() || game.currentSeat().equals(HUMAN) && game.lastMove().seat().equals(BOT));

            assertThat(level, game.lastMove().seat(), is(BOT));
            assertThat(level, game.history().size(), is(2));
        }
    }

    @Test
    void aComputerSeatKeepsItsLevelAfterPlayAgain() throws Exception {
        Draughts game = startedGameWithBot("expert");

        sessions.playAgain(game.getId(), "Alice");

        assertThat(sessions.botSeats(game.getId()), is(Set.of(BOT)));
        assertThat(sessions.seatOptions(game.getId(), BOT).get(DraughtsFactory.LEVEL_KEY), is("expert"));
    }

    @Test
    void theTestProfilePinsEveryThinkingDelayToZeroAndSeedsTheGenerator() {
        assertThat(environment.getProperty("draughts.bots.delay-min-ms"), is("0"));
        assertThat(environment.getProperty("draughts.bots.delay-max-ms"), is("0"));
        assertThat(environment.getProperty("draughts.bots.think-ms"), is("0"));
        assertThat(environment.getProperty("draughts.bots.seed"), notNullValue());
    }

    @Test
    void theDefaultsAre700To1300MillisecondsOfDelay300ToSearchAndSixPlies() {
        assertThat(DraughtsConfig.DEFAULT_DELAY_MIN_MS, is("700"));
        assertThat(DraughtsConfig.DEFAULT_DELAY_MAX_MS, is("1300"));
        assertThat(DraughtsConfig.DEFAULT_THINK_MS, is("300"));
        assertThat(DraughtsConfig.DEFAULT_DEPTH, is("6"));
    }
}
