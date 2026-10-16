package org.kevinkib.cardgames.president.presentation.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.config.PresidentConfig;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.PresidentFactory;
import org.kevinkib.cardgames.president.domain.ScriptedDeckSource;
import org.kevinkib.cardgames.president.presentation.dto.PresidentDto;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.util.function.BooleanSupplier;
import java.util.random.RandomGenerator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/**
 * The Spring wiring of the President bots, with the real scheduler and the {@code test} profile (no thinking
 * delay, seeded generator): a computer seat that holds the lead plays by itself.
 */
@SpringBootTest
@ActiveProfiles("test")
class PresidentBotWiringIT {

    private static final PlayerId BOT = new PlayerId(1);

    @Autowired
    @Qualifier("sessionService")
    private SessionService sessions;

    @Autowired
    private PresidentBotCoordinator coordinator;

    @Autowired
    private SeatStateBroadcaster<President, PresidentDto> broadcaster;

    @Autowired
    private ScriptedDeckSource decks;

    @Autowired
    @Qualifier("presidentBotRandom")
    private RandomGenerator botRandom;

    @Autowired
    private Environment environment;

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat("the awaited state was reached", condition.getAsBoolean(), is(true));
    }

    @Test
    void theCoordinatorIsABeanAndAComputerSeatHoldingTheLeadPlaysByItself() throws Exception {
        assertThat(coordinator, notNullValue());
        RoomCreated room = sessions.createRoom(PresidentFactory.GAME_TYPE, "Alice");
        GameId id = new GameId(room.gameId());
        sessions.addBot(id, room.hostToken());
        sessions.setSeatOption(id, room.hostToken(), 1, "level", "hard");
        sessions.joinRoom(id, "Carl");
        decks.fix(id, 1, cards("3C KH 4D")); // the king of hearts, so the computer leads
        President game = (President) sessions.startGame(id, room.hostToken());

        broadcaster.broadcast(game, "START", new EmptyEventData(), "Game started.");

        await(() -> game.trick().plays().size() == 1 || !game.currentSeat().equals(BOT));
        assertThat(game.currentSeat(), is(not(BOT)));
        assertThat(game.hand(BOT).size() < 17, is(true));
    }

    @Test
    void theTestProfilePinsTheThinkingDelayToZero() {
        assertThat(environment.getProperty("president.bots.delay-min-ms"), is("0"));
        assertThat(environment.getProperty("president.bots.delay-max-ms"), is("0"));
    }

    @Test
    void theDefaultsAre700To1300Milliseconds() {
        assertThat(PresidentConfig.DEFAULT_DELAY_MIN_MS, is("700"));
        assertThat(PresidentConfig.DEFAULT_DELAY_MAX_MS, is("1300"));
    }

    @Test
    void theTestProfileUsesASeededGeneratorNotAConstantOne() {
        // two beans of the same seed would draw the same numbers; a constant generator would draw one value forever
        double first = botRandom.nextDouble();
        double second = botRandom.nextDouble();
        assertThat(first == second, is(false));
    }
}
