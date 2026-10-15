package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaFactory;
import org.kevinkib.cardgames.mangola.presentation.MangolaGameActions;
import org.kevinkib.cardgames.mangola.presentation.dto.MangolaDto;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.bot.ThreadLocalRandomGenerator;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.random.RandomGenerator;

/**
 * The beans of the Mangola context: factory, broadcasters and actions. The game has no bots yet, so its
 * broadcaster skips no seat.
 */
@Configuration
public class MangolaConfig {

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
            GameMessagingService gameMessagingService, SessionService sessionService) {
        return new SeatStateBroadcaster<>(gameMessagingService, BotSeats.none(), sessionService::seatNames,
                (game, viewer, names, bots) -> MangolaDto.forViewer(game, viewer, names));
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
}
