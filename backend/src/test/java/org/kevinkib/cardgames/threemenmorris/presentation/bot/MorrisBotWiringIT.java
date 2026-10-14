package org.kevinkib.cardgames.threemenmorris.presentation.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.config.ThreeMenMorrisConfig;
import org.kevinkib.cardgames.sessionmanagement.core.application.JoinResult;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;
import org.kevinkib.cardgames.threemenmorris.presentation.ThreeMenMorrisGameActions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * The Spring wiring of the bots, with the real scheduler and the {@code test} profile (no thinking delay,
 * constant generator): a human places a piece and the computer seat answers by itself.
 */
@SpringBootTest
@ActiveProfiles("test")
class MorrisBotWiringIT {

    private static final PlayerId HUMAN = new PlayerId(0);
    private static final PlayerId BOT = new PlayerId(1);

    @Autowired
    @Qualifier("sessionService")
    private SessionService sessions;

    @Autowired
    private ThreeMenMorrisGameActions actions;

    @Autowired
    private MorrisBotCoordinator coordinator;

    @Autowired
    private Environment environment;

    private ThreeMenMorris startedGameWithBot(String level) throws Exception {
        RoomCreated room = sessions.createRoom(ThreeMenMorrisFactory.GAME_TYPE, "Alice",
                GameOptions.of(Map.of(ThreeMenMorrisFactory.HOST_COLOUR_KEY, "white")));
        GameId id = new GameId(room.gameId());
        sessions.addBot(id, room.hostToken());
        sessions.setSeatOption(id, room.hostToken(), 1, "level", level);
        return (ThreeMenMorris) sessions.startGame(id, room.hostToken());
    }

    private static void awaitBotPiece(ThreeMenMorris game) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (game.currentSeat().equals(BOT) && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
    }

    @Test
    void theCoordinatorIsABeanAndAComputerSeatAnswersAHumanMove() throws Exception {
        assertThat(coordinator, notNullValue());
        ThreeMenMorris game = startedGameWithBot("perfect");

        actions.place(game.getId(), HUMAN, 0);
        awaitBotPiece(game);

        assertThat(game.currentSeat(), is(HUMAN));
        assertThat(game.board().stream().filter(BOT::equals).count(), is(1L));
        assertThat(game.piecesToPlace(BOT), is(2));
    }

    @Test
    void aComputerSeatKeepsItsLevelAfterPlayAgain() throws Exception {
        ThreeMenMorris game = startedGameWithBot("random");
        GameId id = game.getId();

        JoinResult again = sessions.playAgain(id, "Alice");

        assertThat(again.playerId(), is(HUMAN));
        assertThat(sessions.botSeats(id), is(java.util.Set.of(BOT)));
        assertThat(sessions.seatOptions(id, BOT).get("level"), is("random"));
    }

    @Test
    void theTestProfilePinsBothThinkingDelaysToZero() {
        assertThat(environment.getProperty("threemenmorris.bots.delay-min-ms"), is("0"));
        assertThat(environment.getProperty("threemenmorris.bots.delay-max-ms"), is("0"));
    }

    @Test
    void theDefaultThinkingDelayIs700To1300Milliseconds() {
        assertThat(ThreeMenMorrisConfig.DEFAULT_DELAY_MIN_MS, is("700"));
        assertThat(ThreeMenMorrisConfig.DEFAULT_DELAY_MAX_MS, is("1300"));
    }
}
