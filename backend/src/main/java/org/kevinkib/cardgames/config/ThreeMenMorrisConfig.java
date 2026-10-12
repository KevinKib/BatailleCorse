package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;
import org.kevinkib.cardgames.threemenmorris.presentation.ThreeMenMorrisGameActions;
import org.kevinkib.cardgames.threemenmorris.presentation.ThreeMenMorrisLifecycleBroadcaster;
import org.kevinkib.cardgames.threemenmorris.presentation.ThreeMenMorrisStateBroadcaster;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The beans of the Three Men Morris context: factory, broadcasters and actions. */
@Configuration
public class ThreeMenMorrisConfig {

    @Bean
    public ThreeMenMorrisFactory threeMenMorrisFactory() {
        return new ThreeMenMorrisFactory();
    }

    @Bean
    public ThreeMenMorrisStateBroadcaster threeMenMorrisStateBroadcaster(
            GameMessagingService gameMessagingService, SessionService sessionService) {
        return new ThreeMenMorrisStateBroadcaster(gameMessagingService, sessionService::seatNames);
    }

    @Bean
    public ThreeMenMorrisGameActions threeMenMorrisGameActions(SessionService sessionService,
            ThreeMenMorrisStateBroadcaster threeMenMorrisStateBroadcaster) {
        return new ThreeMenMorrisGameActions(sessionService, threeMenMorrisStateBroadcaster);
    }

    @Bean
    public GameLifecycleBroadcaster threeMenMorrisLifecycleBroadcaster(
            ThreeMenMorrisStateBroadcaster threeMenMorrisStateBroadcaster) {
        return new ThreeMenMorrisLifecycleBroadcaster(threeMenMorrisStateBroadcaster);
    }
}
