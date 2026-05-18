package org.kevinkib.bataillecorse.config;

import org.kevinkib.bataillecorse.sessionmanagement.application.SessionService;
import org.kevinkib.bataillecorse.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.bataillecorse.sessionmanagement.infrastructure.InMemorySessionRepository;
import org.kevinkib.bataillecorse.websocket.presentation.v1.BatailleCorseWebSocketController;
import org.kevinkib.bataillecorse.websocket.presentation.v1.GameMessagingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

//    @Bean
//    public BatailleCorseWebSocketController batailleCorseWebSocketController() {
//        return new BatailleCorseWebSocketController(sessionService());
//    }
//
//    @Bean
//    public GameMessagingService gameMessagingService() {
//        return new GameMessagingService();
//    }

    @Bean
    public SessionService sessionService() {
        return new SessionService(sessionRepository());
    }

    @Bean
    public SessionRepository sessionRepository() {
        return new InMemorySessionRepository();
    }

}
