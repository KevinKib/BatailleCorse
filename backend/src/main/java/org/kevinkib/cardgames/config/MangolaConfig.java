package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaFactory;
import org.kevinkib.cardgames.mangola.domain.bot.ExpertMangolaBot;
import org.kevinkib.cardgames.mangola.presentation.bot.MangolaBotLevels;
import org.kevinkib.cardgames.mangola.presentation.MangolaGameActions;
import org.kevinkib.cardgames.mangola.presentation.bot.MangolaBotCoordinator;
import org.kevinkib.cardgames.mangola.presentation.dto.MangolaDto;
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

import java.util.random.RandomGenerator;

/**
 * The beans of the Mangola context: factory, broadcasters, actions and the bot coordinator. The broadcaster
 * skips the computer seats, which receive no message.
 */
@Configuration
public class MangolaConfig {

    /** A bot "thinks" for a random 700-1300 ms before acting (properties {@code mangola.bots.delay-min-ms} / {@code delay-max-ms}). */
    public static final String DEFAULT_DELAY_MIN_MS = "700";
    public static final String DEFAULT_DELAY_MAX_MS = "1300";
    /** The expert searches for 300 ms a move ({@code mangola.bots.think-ms}) and 1000 ms a layout ({@code mangola.bots.layout-ms}). */
    public static final String DEFAULT_THINK_MS = "300";
    public static final String DEFAULT_LAYOUT_MS = "1000";
    /** A safety net on the nodes of one search, far above what the time budget reaches. */
    static final long EXPERT_NODE_CAP = 5_000_000L;

    /** Production: the starting seat and the random set-up are really drawn. */
    @Bean("mangolaRandom")
    @Profile("!test")
    public RandomGenerator mangolaRandom() {
        return new ThreadLocalRandomGenerator();
    }

    /** End-to-end tests (profile {@code test}): every draw is 0, so the host starts and the random set-up is fixed. */
    @Bean("mangolaRandom")
    @Profile("test")
    public RandomGenerator mangolaRandomFixed() {
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
    public MangolaFactory mangolaFactory(@Qualifier("mangolaRandom") RandomGenerator mangolaRandom) {
        return new MangolaFactory(mangolaRandom);
    }

    @Bean
    public SeatStateBroadcaster<Mangola, MangolaDto> mangolaStateBroadcaster(
            GameMessagingService gameMessagingService, SessionService sessionService,
            BotSeatOptions botSeatOptions) {
        return new SeatStateBroadcaster<>(gameMessagingService, sessionService::botSeats, sessionService::seatNames,
                (game, viewer, names, bots) -> MangolaDto.forViewer(game, viewer, names, bots,
                        MangolaBotLevels.of(game.getId(), bots, botSeatOptions)));
    }

    @Bean
    public MangolaGameActions mangolaGameActions(SessionService sessionService,
            SeatStateBroadcaster<Mangola, MangolaDto> mangolaStateBroadcaster) {
        return new MangolaGameActions(sessionService, mangolaStateBroadcaster);
    }

    @Bean
    public GameLifecycleBroadcaster mangolaLifecycleBroadcaster(
            SeatStateBroadcaster<Mangola, MangolaDto> mangolaStateBroadcaster) {
        return new SeatLifecycleBroadcaster<>(Mangola.class, mangolaStateBroadcaster);
    }

    /**
     * Drives the computer seats. The thinking delay is built here and not published as a bean, so it does not
     * compete with the other games' {@code ThinkingDelay} beans; the {@code test} profile pins the delays and the
     * search budgets to 0 (the expert then completes its first depth only). The generator is the Mangola one:
     * constant under the {@code test} profile, so a bot's choice is fixed.
     */
    @Bean
    public MangolaBotCoordinator mangolaBotCoordinator(
            SessionService sessionService,
            BotSeatOptions botSeatOptions,
            MangolaGameActions actions,
            SeatStateBroadcaster<Mangola, MangolaDto> mangolaStateBroadcaster,
            BotScheduler botScheduler,
            @Qualifier("mangolaRandom") RandomGenerator mangolaRandom,
            @Value("${mangola.bots.delay-min-ms:" + DEFAULT_DELAY_MIN_MS + "}") long minMillis,
            @Value("${mangola.bots.delay-max-ms:" + DEFAULT_DELAY_MAX_MS + "}") long maxMillis,
            @Value("${mangola.bots.think-ms:" + DEFAULT_THINK_MS + "}") long thinkMillis,
            @Value("${mangola.bots.layout-ms:" + DEFAULT_LAYOUT_MS + "}") long layoutMillis) {
        ExpertMangolaBot.Budget budget = new ExpertMangolaBot.Budget(thinkMillis, EXPERT_NODE_CAP, layoutMillis);
        MangolaBotCoordinator coordinator = new MangolaBotCoordinator(
                sessionService::botSeats, botSeatOptions, actions, botScheduler,
                new UniformThinkingDelay(minMillis, maxMillis, mangolaRandom),
                level -> level.bot(mangolaRandom, budget));
        mangolaStateBroadcaster.addListener(coordinator);
        return coordinator;
    }
}
