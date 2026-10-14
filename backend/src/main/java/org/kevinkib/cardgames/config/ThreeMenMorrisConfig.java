package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.threemenmorris.presentation.dto.ThreeMenMorrisDto;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;
import org.kevinkib.cardgames.threemenmorris.presentation.ThreeMenMorrisGameActions;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.bot.UniformThinkingDelay;
import org.kevinkib.cardgames.threemenmorris.presentation.bot.MorrisBotCoordinator;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.kevinkib.cardgames.presentation.bot.ThreadLocalRandomGenerator;

import java.util.random.RandomGenerator;

/** The beans of the Three Men Morris context: factory, broadcasters, actions and the bot coordinator. */
@Configuration
public class ThreeMenMorrisConfig {

    /** A bot "thinks" for a random 700-1300 ms before acting (properties {@code threemenmorris.bots.delay-min-ms} / {@code delay-max-ms}). */
    public static final String DEFAULT_DELAY_MIN_MS = "700";
    public static final String DEFAULT_DELAY_MAX_MS = "1300";

    /** Production: the colour of a "random" host is really drawn. */
    @Bean("morrisRandom")
    @Profile("!test")
    public RandomGenerator morrisRandom() {
        return new ThreadLocalRandomGenerator();
    }

    /** End-to-end tests (profile {@code test}): every draw is 0, so a "random" host is White. */
    @Bean("morrisRandom")
    @Profile("test")
    public RandomGenerator morrisRandomFixed() {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }

    @Bean
    public ThreeMenMorrisFactory threeMenMorrisFactory(@Qualifier("morrisRandom") RandomGenerator morrisRandom) {
        return new ThreeMenMorrisFactory(morrisRandom);
    }

    @Bean
    public SeatStateBroadcaster<ThreeMenMorris, ThreeMenMorrisDto> threeMenMorrisStateBroadcaster(
            GameMessagingService gameMessagingService, SessionService sessionService) {
        return new SeatStateBroadcaster<>(gameMessagingService, sessionService::botSeats, sessionService::seatNames,
                (game, viewer, names, bots) -> ThreeMenMorrisDto.forViewer(game, viewer, names));
    }

    @Bean
    public ThreeMenMorrisGameActions threeMenMorrisGameActions(SessionService sessionService,
            SeatStateBroadcaster<ThreeMenMorris, ThreeMenMorrisDto> threeMenMorrisStateBroadcaster) {
        return new ThreeMenMorrisGameActions(sessionService, threeMenMorrisStateBroadcaster);
    }

    @Bean
    public GameLifecycleBroadcaster threeMenMorrisLifecycleBroadcaster(
            SeatStateBroadcaster<ThreeMenMorris, ThreeMenMorrisDto> threeMenMorrisStateBroadcaster) {
        return new SeatLifecycleBroadcaster<>(ThreeMenMorris.class, threeMenMorrisStateBroadcaster);
    }

    /**
     * Drives the computer seats. The thinking delay is built here and not published as a bean, so it does not
     * compete with the other games' {@code ThinkingDelay} beans; the {@code test} profile pins both bounds to 0.
     * The generator is the Morris one: constant under the {@code test} profile, so a bot's choice is fixed.
     */
    @Bean
    public MorrisBotCoordinator morrisBotCoordinator(
            SessionService sessionService,
            BotSeatOptions botSeatOptions,
            ThreeMenMorrisGameActions actions,
            SeatStateBroadcaster<ThreeMenMorris, ThreeMenMorrisDto> threeMenMorrisStateBroadcaster,
            BotScheduler botScheduler,
            @Qualifier("morrisRandom") RandomGenerator morrisRandom,
            @Value("${threemenmorris.bots.delay-min-ms:" + DEFAULT_DELAY_MIN_MS + "}") long minMillis,
            @Value("${threemenmorris.bots.delay-max-ms:" + DEFAULT_DELAY_MAX_MS + "}") long maxMillis) {
        MorrisBotCoordinator coordinator = new MorrisBotCoordinator(
                sessionService::botSeats, botSeatOptions, actions, botScheduler,
                new UniformThinkingDelay(minMillis, maxMillis, morrisRandom),
                level -> level.bot(morrisRandom));
        threeMenMorrisStateBroadcaster.addListener(coordinator);
        return coordinator;
    }
}
