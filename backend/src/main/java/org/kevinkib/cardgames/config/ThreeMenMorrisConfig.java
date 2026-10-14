package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.threemenmorris.presentation.dto.ThreeMenMorrisDto;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;
import org.kevinkib.cardgames.threemenmorris.presentation.ThreeMenMorrisGameActions;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.kevinkib.cardgames.presentation.bot.ThreadLocalRandomGenerator;

import java.util.random.RandomGenerator;

/** The beans of the Three Men Morris context: factory, broadcasters and actions. */
@Configuration
public class ThreeMenMorrisConfig {

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
    public ThreeMenMorrisFactory threeMenMorrisFactory(RandomGenerator morrisRandom) {
        return new ThreeMenMorrisFactory(morrisRandom);
    }

    @Bean
    public SeatStateBroadcaster<ThreeMenMorris, ThreeMenMorrisDto> threeMenMorrisStateBroadcaster(
            GameMessagingService gameMessagingService, SessionService sessionService) {
        return new SeatStateBroadcaster<>(gameMessagingService, BotSeats.none(), sessionService::seatNames,
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
}
