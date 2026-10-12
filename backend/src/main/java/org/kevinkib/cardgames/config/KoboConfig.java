package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.kobo.domain.KoboFactory;
import org.kevinkib.cardgames.kobo.presentation.KoboGameActions;
import org.kevinkib.cardgames.kobo.presentation.KoboLifecycleBroadcaster;
import org.kevinkib.cardgames.kobo.presentation.KoboStateBroadcaster;
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
    public KoboStateBroadcaster koboStateBroadcaster(GameMessagingService gameMessagingService,
                                                     SessionService sessionService) {
        return new KoboStateBroadcaster(gameMessagingService, sessionService::seatNames);
    }

    @Bean
    public KoboGameActions koboGameActions(SessionService sessionService,
                                           KoboStateBroadcaster koboStateBroadcaster) {
        return new KoboGameActions(sessionService, koboStateBroadcaster);
    }

    @Bean
    public GameLifecycleBroadcaster koboLifecycleBroadcaster(KoboStateBroadcaster koboStateBroadcaster) {
        return new KoboLifecycleBroadcaster(koboStateBroadcaster);
    }
}
