package org.kevinkib.cardgames.kobo.presentation.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.config.KoboConfig;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.KoboFactory;
import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import org.kevinkib.cardgames.kobo.presentation.KoboGameActions;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.random.RandomGenerator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * The Spring wiring of the Kobo bots, with the real scheduler and the {@code test} profile (no delay, a fixed
 * seed): a human says ready and the computer seat answers by itself, which proves that the coordinator listens to
 * the Kobo broadcaster.
 */
@SpringBootTest
@ActiveProfiles("test")
class KoboBotWiringIT {

    private static final PlayerId HUMAN = new PlayerId(0);
    private static final PlayerId BOT = new PlayerId(1);

    @Autowired
    @Qualifier("sessionService")
    private SessionService sessions;

    @Autowired
    private KoboGameActions actions;

    @Autowired
    private KoboBotCoordinator coordinator;

    @Autowired
    private List<GameEvictionListener> evictionListeners;

    @Autowired
    private Environment environment;

    @Autowired
    @Qualifier("koboBotRandom")
    private RandomGenerator koboBotRandom;

    private Kobo startedGameWithBot(String level) throws Exception {
        RoomCreated room = sessions.createRoom(KoboFactory.GAME_TYPE, "Alice");
        GameId id = new GameId(room.gameId());
        sessions.addBot(id, room.hostToken());
        sessions.setSeatOption(id, room.hostToken(), 1, KoboFactory.LEVEL_KEY, level);
        return (Kobo) sessions.startGame(id, room.hostToken());
    }

    @Test
    void theCoordinatorIsABeanAndAnEvictionListener() {
        assertThat(coordinator, notNullValue());
        assertThat(evictionListeners, hasItem(instanceOf(KoboBotCoordinator.class)));
    }

    @Test
    void aComputerSeatReadiesByItselfOnceTheHumanIsReady() throws Exception {
        Kobo game = startedGameWithBot("hard");
        assertThat(sessions.botSeats(game.getId()), is(java.util.Set.of(BOT)));

        actions.ready(game.getId(), HUMAN);
        long deadline = System.currentTimeMillis() + 5_000;
        while (game.phase() == Phase.MEMORISING && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        assertThat(game.phase(), is(Phase.DRAW));
    }

    @Test
    void theTestProfilePinsTheDelaysAndTheReactionScaleToZero() {
        assertThat(environment.getProperty("kobo.bots.delay-min-ms"), is("0"));
        assertThat(environment.getProperty("kobo.bots.delay-max-ms"), is("0"));
        assertThat(environment.getProperty("kobo.bots.reaction-scale"), is("0"));
    }

    @Test
    void theDefaultsAre600To1200MillisecondsPerStepAndTheTuningsReactionTimes() {
        assertThat(KoboConfig.DEFAULT_DELAY_MIN_MS, is("600"));
        assertThat(KoboConfig.DEFAULT_DELAY_MAX_MS, is("1200"));
        assertThat(KoboConfig.DEFAULT_REACTION_SCALE, is("1.0"));
    }

    @Test
    void theTestProfileGeneratorIsSeededNotConstant() {
        // a constant 0 would make every "nextDouble() < p" true and erase a medium bot's memory at each turn end
        boolean varies = false;
        double first = koboBotRandom.nextDouble();
        for (int i = 0; i < 20; i++) {
            varies |= koboBotRandom.nextDouble() != first;
        }
        assertThat(varies, is(true));
    }
}
