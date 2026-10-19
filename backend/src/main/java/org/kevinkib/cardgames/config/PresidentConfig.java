package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.president.domain.DeckSource;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.PresidentFactory;
import org.kevinkib.cardgames.president.domain.RandomDeckSource;
import org.kevinkib.cardgames.president.domain.ScriptedDeckSource;
import org.kevinkib.cardgames.president.domain.bot.HeuristicPresidentBot;
import org.kevinkib.cardgames.president.presentation.PresidentExchangeViewTimeout;
import org.kevinkib.cardgames.president.presentation.PresidentGameActions;
import org.kevinkib.cardgames.president.presentation.bot.PresidentBotCoordinator;
import org.kevinkib.cardgames.president.presentation.dto.PresidentDto;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.bot.ThreadLocalRandomGenerator;
import org.kevinkib.cardgames.presentation.bot.UniformThinkingDelay;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;
import java.util.Random;
import java.util.random.RandomGenerator;

/** The beans of the President context: factory, deal source, broadcasters, actions and the bot coordinator. */
@Configuration
public class PresidentConfig {

    /** A bot "thinks" for a random 700-1300 ms before each step (properties {@code president.bots.delay-min-ms} / {@code delay-max-ms}). */
    public static final String DEFAULT_DELAY_MIN_MS = "700";
    public static final String DEFAULT_DELAY_MAX_MS = "1300";

    /** How long the exchange view may last before the first trick starts anyway (ISO-8601 duration). */
    public static final String DEFAULT_EXCHANGE_VIEW_TIMEOUT = "PT20S";

    /** Production: the bots' random choices (the easy level, ties) really vary. */
    @Bean("presidentBotRandom")
    @Profile("!test")
    public RandomGenerator presidentBotRandom() {
        return new ThreadLocalRandomGenerator();
    }

    /**
     * End-to-end tests (profile {@code test}): a generator with a fixed seed, so a bot's choices are repeatable.
     * It is not a constant: a constant 0 would make every {@code nextDouble() < p} true and turn an easy bot into
     * a random player.
     */
    @Bean("presidentBotRandom")
    @Profile("test")
    public RandomGenerator presidentBotRandomSeeded() {
        return new Random(20261016L);
    }

    /** Production: a random deal. */
    @Bean
    @Profile("!test")
    public DeckSource presidentDeckSource() {
        return new RandomDeckSource();
    }

    /** End-to-end tests (profile {@code test}): random unless a test fixes the deal, see PresidentTestDealController. */
    @Bean
    @Profile("test")
    public ScriptedDeckSource presidentScriptedDeckSource() {
        return new ScriptedDeckSource(new RandomDeckSource());
    }

    @Bean
    public PresidentFactory presidentFactory(DeckSource presidentDeckSource) {
        return new PresidentFactory(presidentDeckSource);
    }

    @Bean
    public SeatStateBroadcaster<President, PresidentDto> presidentStateBroadcaster(
            GameMessagingService gameMessagingService, SessionService sessionService) {
        return new SeatStateBroadcaster<>(gameMessagingService, sessionService::botSeats, sessionService::seatNames,
                (game, viewer, names, bots) -> PresidentDto.forViewer(game, viewer, names, bots));
    }

    @Bean
    public PresidentGameActions presidentGameActions(SessionService sessionService,
            SeatStateBroadcaster<President, PresidentDto> presidentStateBroadcaster) {
        return new PresidentGameActions(sessionService, presidentStateBroadcaster);
    }

    @Bean
    public GameLifecycleBroadcaster presidentLifecycleBroadcaster(
            SeatStateBroadcaster<President, PresidentDto> presidentStateBroadcaster) {
        return new SeatLifecycleBroadcaster<>(President.class, presidentStateBroadcaster);
    }

    /**
     * Stops the table waiting for the exchange view after {@code president.exchange-view.timeout} (default
     * {@value #DEFAULT_EXCHANGE_VIEW_TIMEOUT}): the seats that have not acknowledged count as having seen it.
     */
    @Bean
    public PresidentExchangeViewTimeout presidentExchangeViewTimeout(
            PresidentGameActions actions,
            SeatStateBroadcaster<President, PresidentDto> presidentStateBroadcaster,
            BotScheduler botScheduler,
            @Value("${president.exchange-view.timeout:" + DEFAULT_EXCHANGE_VIEW_TIMEOUT + "}") Duration timeout) {
        PresidentExchangeViewTimeout viewTimeout = new PresidentExchangeViewTimeout(actions, botScheduler, timeout);
        presidentStateBroadcaster.addListener(viewTimeout);
        return viewTimeout;
    }

    /**
     * Drives the computer seats. The thinking delay is built here and not published as a bean, so it does not
     * compete with the other games' {@code ThinkingDelay} beans; the {@code test} profile pins it to 0.
     */
    @Bean
    public PresidentBotCoordinator presidentBotCoordinator(
            SessionService sessionService,
            BotSeatOptions botSeatOptions,
            PresidentGameActions actions,
            SeatStateBroadcaster<President, PresidentDto> presidentStateBroadcaster,
            BotScheduler botScheduler,
            @Qualifier("presidentBotRandom") RandomGenerator presidentBotRandom,
            @Value("${president.bots.delay-min-ms:" + DEFAULT_DELAY_MIN_MS + "}") long minMillis,
            @Value("${president.bots.delay-max-ms:" + DEFAULT_DELAY_MAX_MS + "}") long maxMillis) {
        PresidentBotCoordinator coordinator = new PresidentBotCoordinator(
                sessionService::botSeats, botSeatOptions, actions, botScheduler,
                new UniformThinkingDelay(minMillis, maxMillis, presidentBotRandom),
                level -> new HeuristicPresidentBot(level.tuning(), presidentBotRandom));
        presidentStateBroadcaster.addListener(coordinator);
        return coordinator;
    }
}
