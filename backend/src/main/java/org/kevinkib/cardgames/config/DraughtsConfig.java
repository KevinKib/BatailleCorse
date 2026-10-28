package org.kevinkib.cardgames.config;

import java.util.HashMap;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.DraughtsFactory;
import org.kevinkib.cardgames.draughts.presentation.DraughtsGameActions;
import org.kevinkib.cardgames.draughts.presentation.dto.DraughtsDto;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.bot.ThreadLocalRandomGenerator;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * The beans of the draughts context: factory, broadcasters and actions. The bot coordinator is added with the bots
 * (chunk B4).
 */
@Configuration
public class DraughtsConfig {

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

    /** The level key of each computer seat, the default (Medium) when the host picked none. */
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
