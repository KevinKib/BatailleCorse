package org.kevinkib.cardgames.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.DraughtsFactory;
import org.kevinkib.cardgames.draughts.domain.bot.ExpertDraughtsBot;
import org.kevinkib.cardgames.draughts.presentation.DraughtsGameActions;
import org.kevinkib.cardgames.draughts.presentation.bot.DraughtsBotCoordinator;
import org.kevinkib.cardgames.draughts.presentation.dto.DraughtsDto;
import org.kevinkib.cardgames.game.PlayerId;
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

/** The beans of the draughts context: factory, broadcasters, actions and the bot coordinator. */
@Configuration
public class DraughtsConfig {

    /** A bot "thinks" for a random 700-1300 ms before acting (properties {@code draughts.bots.delay-min-ms} / {@code delay-max-ms}). */
    public static final String DEFAULT_DELAY_MIN_MS = "700";
    public static final String DEFAULT_DELAY_MAX_MS = "1300";

    /** The expert searches for 300 ms a move ({@code draughts.bots.think-ms}), at most 6 plies ({@code draughts.bots.depth}). */
    public static final String DEFAULT_THINK_MS = "300";
    public static final String DEFAULT_DEPTH = "6";

    /** Safety net on the nodes the expert searches in one decision. */
    static final long EXPERT_NODE_CAP = 2_000_000;

    /** Production: the colour of a "random" host is really drawn. */
    @Bean("draughtsRandom")
    @Profile("!test")
    public RandomGenerator draughtsRandom() {
        return new ThreadLocalRandomGenerator();
    }

    /** End-to-end tests (profile {@code test}): every draw is 0, so a "random" host is White. */
    @Bean("draughtsRandom")
    @Profile("test")
    public RandomGenerator draughtsRandomFixed() {
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
    public DraughtsFactory draughtsFactory(@Qualifier("draughtsRandom") RandomGenerator draughtsRandom) {
        return new DraughtsFactory(draughtsRandom);
    }

    @Bean
    public SeatStateBroadcaster<Draughts, DraughtsDto> draughtsStateBroadcaster(
            GameMessagingService gameMessagingService, SessionService sessionService,
            BotSeatOptions botSeatOptions) {
        return new SeatStateBroadcaster<>(gameMessagingService, sessionService::botSeats, sessionService::seatNames,
                (game, viewer, names, bots) -> DraughtsDto.forViewer(game, viewer, names, bots,
                        botLevels(game, bots, botSeatOptions)));
    }

    @Bean
    public DraughtsGameActions draughtsGameActions(SessionService sessionService,
            SeatStateBroadcaster<Draughts, DraughtsDto> draughtsStateBroadcaster) {
        return new DraughtsGameActions(sessionService, draughtsStateBroadcaster);
    }

    @Bean
    public GameLifecycleBroadcaster draughtsLifecycleBroadcaster(
            SeatStateBroadcaster<Draughts, DraughtsDto> draughtsStateBroadcaster) {
        return new SeatLifecycleBroadcaster<>(Draughts.class, draughtsStateBroadcaster);
    }

    /**
     * Drives the computer seat. The random source of the bots and of the thinking delay is built here and not
     * published as a bean, so it does not join the other generators; the {@code test} profile gives it a seed
     * ({@code draughts.bots.seed}), pins the delay to 0 and the expert's search time to 0 (it then completes depth 1
     * only).
     */
    @Bean
    public DraughtsBotCoordinator draughtsBotCoordinator(
            SessionService sessionService,
            BotSeatOptions botSeatOptions,
            DraughtsGameActions actions,
            SeatStateBroadcaster<Draughts, DraughtsDto> draughtsStateBroadcaster,
            BotScheduler botScheduler,
            @Value("${draughts.bots.seed:}") String seed,
            @Value("${draughts.bots.delay-min-ms:" + DEFAULT_DELAY_MIN_MS + "}") long minMillis,
            @Value("${draughts.bots.delay-max-ms:" + DEFAULT_DELAY_MAX_MS + "}") long maxMillis,
            @Value("${draughts.bots.think-ms:" + DEFAULT_THINK_MS + "}") long thinkMillis,
            @Value("${draughts.bots.depth:" + DEFAULT_DEPTH + "}") int depth) {
        Random random = seed.isBlank() ? new Random() : new Random(Long.parseLong(seed.trim()));
        ExpertDraughtsBot.Budget budget = new ExpertDraughtsBot.Budget(thinkMillis, depth, EXPERT_NODE_CAP);
        DraughtsBotCoordinator coordinator = new DraughtsBotCoordinator(
                sessionService::botSeats, botSeatOptions, actions, botScheduler,
                new UniformThinkingDelay(minMillis, maxMillis, random),
                level -> level.bot(random, budget));
        draughtsStateBroadcaster.addListener(coordinator);
        return coordinator;
    }

    /** The level key of each computer seat,the default (Medium) when the host picked none. */
    private static Map<PlayerId, String> botLevels(Draughts game, java.util.Set<PlayerId> bots,
                                                   BotSeatOptions seatOptions) {
        Map<PlayerId, String> levels = new HashMap<>();
        for (PlayerId seat : bots) {
            String level = seatOptions.seatOptions(game.getId(), seat).get(DraughtsFactory.LEVEL_KEY);
            levels.put(seat, DraughtsFactory.LEVELS.contains(level) ? level : DraughtsFactory.DEFAULT_LEVEL);
        }
        return levels;
    }
}
