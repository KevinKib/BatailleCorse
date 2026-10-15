package org.kevinkib.cardgames.mangola.presentation.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.config.MangolaConfig;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaFactory;
import org.kevinkib.cardgames.mangola.domain.Phase;
import org.kevinkib.cardgames.mangola.presentation.MangolaGameActions;
import org.kevinkib.cardgames.sessionmanagement.core.application.JoinResult;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * The Spring wiring of the Mangola bots, with the real scheduler and the {@code test} profile (no thinking delay,
 * constant generator): a human acts and the computer seat answers by itself.
 */
@SpringBootTest
@ActiveProfiles("test")
class MangolaBotWiringIT {

    private static final PlayerId HUMAN = new PlayerId(0);
    private static final PlayerId BOT = new PlayerId(1);

    @Autowired
    @Qualifier("sessionService")
    private SessionService sessions;

    @Autowired
    private MangolaGameActions actions;

    @Autowired
    private MangolaBotCoordinator coordinator;

    @Autowired
    private Environment environment;

    private Mangola startedGameWithBot(String level) throws Exception {
        RoomCreated room = sessions.createRoom(MangolaFactory.GAME_TYPE, "Alice",
                GameOptions.of(Map.of(MangolaFactory.FIRST_PLAYER_KEY, "host")));
        GameId id = new GameId(room.gameId());
        sessions.addBot(id, room.hostToken());
        sessions.setSeatOption(id, room.hostToken(), 1, "level", level);
        return (Mangola) sessions.startGame(id, room.hostToken());
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat("the awaited state was reached", condition.getAsBoolean(), is(true));
    }

    @Test
    void theCoordinatorIsABeanAndAComputerSeatAnswersAHumanMove() throws Exception {
        assertThat(coordinator, notNullValue());
        Mangola game = startedGameWithBot("expert");
        int[] twoPerPit = new int[16];
        Arrays.fill(twoPerPit, 2);
        actions.arrange(game.getId(), HUMAN, twoPerPit);
        actions.confirmLayout(game.getId(), HUMAN);
        await(() -> game.phase() == Phase.PLAY);
        await(() -> game.currentSeat().equals(HUMAN));
        int pit = game.legalPits(HUMAN).get(0);

        actions.sow(game.getId(), HUMAN, pit);

        await(() -> game.isFinished() || game.currentSeat().equals(HUMAN) && game.lastMove().seat().equals(BOT));
        assertThat(game.lastMove().seat().equals(BOT) || game.isFinished(), is(true));
    }

    @Test
    void aComputerSeatArrangesAndConfirmsItsLayoutDuringHiddenPlacement() throws Exception {
        Mangola game = startedGameWithBot("greedy");
        int[] twoPerPit = new int[16];
        Arrays.fill(twoPerPit, 2);

        actions.arrange(game.getId(), HUMAN, twoPerPit);
        actions.confirmLayout(game.getId(), HUMAN);

        await(() -> game.phase() != Phase.PLACEMENT);
        assertThat(game.isReady(BOT), is(true));
        assertThat(game.layoutOf(BOT).orElseThrow().stream().mapToInt(Integer::intValue).sum(), is(32));
    }

    @Test
    void aComputerSeatKeepsItsLevelAfterPlayAgain() throws Exception {
        Mangola game = startedGameWithBot("expert");
        GameId id = game.getId();

        JoinResult again = sessions.playAgain(id, "Alice");

        assertThat(again.playerId(), is(HUMAN));
        assertThat(sessions.botSeats(id), is(Set.of(BOT)));
        assertThat(sessions.seatOptions(id, BOT).get("level"), is("expert"));
    }

    @Test
    void theTestProfilePinsEveryThinkingDelayToZero() {
        assertThat(environment.getProperty("mangola.bots.delay-min-ms"), is("0"));
        assertThat(environment.getProperty("mangola.bots.delay-max-ms"), is("0"));
        assertThat(environment.getProperty("mangola.bots.think-ms"), is("0"));
        assertThat(environment.getProperty("mangola.bots.layout-ms"), is("0"));
    }

    @Test
    void theDefaultsAre700To1300MillisecondsOfDelayAnd300ToSearchAnd1000ToArrange() {
        assertThat(MangolaConfig.DEFAULT_DELAY_MIN_MS, is("700"));
        assertThat(MangolaConfig.DEFAULT_DELAY_MAX_MS, is("1300"));
        assertThat(MangolaConfig.DEFAULT_THINK_MS, is("300"));
        assertThat(MangolaConfig.DEFAULT_LAYOUT_MS, is("1000"));
    }
}
