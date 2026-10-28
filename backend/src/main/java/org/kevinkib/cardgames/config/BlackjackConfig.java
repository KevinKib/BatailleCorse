package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.blackjack.domain.Blackjack;
import org.kevinkib.cardgames.blackjack.domain.BlackjackFactory;
import org.kevinkib.cardgames.blackjack.domain.RandomShoeSource;
import org.kevinkib.cardgames.blackjack.domain.ScriptedShoeSource;
import org.kevinkib.cardgames.blackjack.domain.ShoeSource;
import org.kevinkib.cardgames.blackjack.domain.bot.BlackjackBots;
import org.kevinkib.cardgames.blackjack.presentation.BlackjackGameActions;
import org.kevinkib.cardgames.blackjack.presentation.BlackjackLifecycleBroadcaster;
import org.kevinkib.cardgames.blackjack.presentation.bot.BlackjackBotCoordinator;
import org.kevinkib.cardgames.blackjack.presentation.dto.BlackjackDto;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.bot.UniformThinkingDelay;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Random;

/**
 * The beans of the Blackjack context: factory, shoe source, broadcasters, actions and the bot coordinator. Nothing is
 * registered in {@code AppConfig}. No pause bean: nothing is scheduled between hands.
 */
@Configuration
public class BlackjackConfig {

    /** A bot "thinks" for a random 700-1300 ms before each action (properties {@code blackjack.bots.delay-min-ms} / {@code delay-max-ms}). */
    public static final String DEFAULT_DELAY_MIN_MS = "700";
    public static final String DEFAULT_DELAY_MAX_MS = "1300";

    /** Production: a random shuffle. */
    @Bean
    @Profile("!test")
    public ShoeSource blackjackShoeSource() {
        return new RandomShoeSource();
    }

    /** End-to-end tests (profile {@code test}): random unless a test fixes the shoe, see BlackjackTestShoeController. */
    @Bean
    @Profile("test")
    public ScriptedShoeSource blackjackScriptedShoeSource() {
        return new ScriptedShoeSource(new RandomShoeSource());
    }

    @Bean
    public BlackjackFactory blackjackFactory(ShoeSource blackjackShoeSource) {
        return new BlackjackFactory(blackjackShoeSource);
    }

    @Bean
    public SeatStateBroadcaster<Blackjack, BlackjackDto> blackjackStateBroadcaster(
            GameMessagingService gameMessagingService, SessionService sessionService,
            BotSeatOptions botSeatOptions) {
        return new SeatStateBroadcaster<>(gameMessagingService, sessionService::botSeats, sessionService::seatNames,
                (game, viewer, names, bots) -> BlackjackDto.forViewer(game, viewer, names, bots, botSeatOptions));
    }

    @Bean
    public BlackjackGameActions blackjackGameActions(SessionService sessionService,
            SeatStateBroadcaster<Blackjack, BlackjackDto> blackjackStateBroadcaster) {
        return new BlackjackGameActions(sessionService, blackjackStateBroadcaster);
    }

    @Bean
    public GameLifecycleBroadcaster blackjackLifecycleBroadcaster(
            SeatStateBroadcaster<Blackjack, BlackjackDto> blackjackStateBroadcaster,
            BlackjackGameActions blackjackGameActions) {
        return new BlackjackLifecycleBroadcaster(blackjackStateBroadcaster, blackjackGameActions);
    }

    /**
     * Drives the computer seats. The random source of the easy level and of the thinking delay is built here and not
     * published as a bean, so it does not join the other games' generators; the {@code test} profile gives it a seed
     * ({@code blackjack.bots.seed}) and pins the delay to 0.
     */
    @Bean
    public BlackjackBotCoordinator blackjackBotCoordinator(
            SessionService sessionService,
            BotSeatOptions botSeatOptions,
            BlackjackGameActions blackjackGameActions,
            SeatStateBroadcaster<Blackjack, BlackjackDto> blackjackStateBroadcaster,
            BotScheduler botScheduler,
            @Value("${blackjack.bots.seed:}") String seed,
            @Value("${blackjack.bots.delay-min-ms:" + DEFAULT_DELAY_MIN_MS + "}") long minMillis,
            @Value("${blackjack.bots.delay-max-ms:" + DEFAULT_DELAY_MAX_MS + "}") long maxMillis) {
        Random random = seed.isBlank() ? new Random() : new Random(Long.parseLong(seed.trim()));
        BlackjackBotCoordinator coordinator = new BlackjackBotCoordinator(
                sessionService::botSeats, botSeatOptions, blackjackGameActions, botScheduler,
                new UniformThinkingDelay(minMillis, maxMillis, random),
                level -> BlackjackBots.forLevel(level, random));
        blackjackStateBroadcaster.addListener(coordinator);
        return coordinator;
    }
}
