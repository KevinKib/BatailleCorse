package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.kobo.presentation.dto.KoboDto;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.KoboFactory;
import org.kevinkib.cardgames.kobo.presentation.KoboGameActions;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The beans of the Kobo context: factory, broadcasters and actions. */
@Configuration
public class KoboConfig {

    @Bean
    public KoboFactory koboFactory() {
        return new KoboFactory();
    }

    @Bean
    public SeatStateBroadcaster<Kobo, KoboDto> koboStateBroadcaster(GameMessagingService gameMessagingService,
                                                     SessionService sessionService) {
        return new SeatStateBroadcaster<>(gameMessagingService, BotSeats.none(), sessionService::seatNames,
                (game, viewer, names, bots) -> KoboDto.forViewer(game, viewer, names));
    }

    @Bean
    public KoboGameActions koboGameActions(SessionService sessionService,
                                           SeatStateBroadcaster<Kobo, KoboDto> koboStateBroadcaster) {
        return new KoboGameActions(sessionService, koboStateBroadcaster);
    }

    @Bean
    public GameLifecycleBroadcaster koboLifecycleBroadcaster(SeatStateBroadcaster<Kobo, KoboDto> koboStateBroadcaster) {
        return new SeatLifecycleBroadcaster<>(Kobo.class, koboStateBroadcaster);
    }
}
