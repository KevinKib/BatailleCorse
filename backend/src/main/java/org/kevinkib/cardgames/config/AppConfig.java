package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.sessionmanagement.application.GameCleanupService;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.cardgames.sessionmanagement.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.presentation.BatailleCorseLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.DisconnectForfeitService;
import org.kevinkib.cardgames.presentation.ForfeitReasonRegistry;
import org.kevinkib.cardgames.presentation.GameLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.GameLifecycleBroadcasters;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.StompSessionSeatRegistry;
import org.kevinkib.cardgames.presentation.WebSocketDisconnectListener;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;

@Configuration
@EnableScheduling
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public GameFactory gameFactory() {
        return new BatailleCorseFactory();
    }

    @Bean
    public SessionService sessionService() {
        return new SessionService(sessionRepository(), gameFactory());
    }

    @Bean
    public SessionRepository sessionRepository() {
        return new InMemorySessionRepository(clock());
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("game-sched-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    public StompSessionSeatRegistry stompSessionSeatRegistry() {
        return new StompSessionSeatRegistry();
    }

    @Bean
    public ForfeitReasonRegistry forfeitReasonRegistry() {
        return new ForfeitReasonRegistry();
    }

    @Bean
    public GameMessagingService gameMessagingService(SimpMessagingTemplate messagingTemplate) {
        return new GameMessagingService(messagingTemplate);
    }

    @Bean
    public GameLifecycleBroadcaster batailleCorseLifecycleBroadcaster(GameMessagingService gameMessagingService) {
        return new BatailleCorseLifecycleBroadcaster(gameMessagingService, forfeitReasonRegistry());
    }

    @Bean
    public GameLifecycleBroadcasters gameLifecycleBroadcasters(List<GameLifecycleBroadcaster> broadcasters) {
        return new GameLifecycleBroadcasters(broadcasters);
    }

    @Bean
    public DisconnectForfeitService disconnectForfeitService(GameLifecycleBroadcasters gameLifecycleBroadcasters) {
        return new DisconnectForfeitService(
                sessionService(), stompSessionSeatRegistry(), taskScheduler(), clock(), forfeitReasonRegistry(),
                gameLifecycleBroadcasters);
    }

    @Bean
    public WebSocketDisconnectListener webSocketDisconnectListener(DisconnectForfeitService disconnectForfeitService) {
        return new WebSocketDisconnectListener(disconnectForfeitService);
    }

    @Bean
    public GameCleanupService gameCleanupService() {
        return new GameCleanupService(sessionRepository(), stompSessionSeatRegistry(), forfeitReasonRegistry());
    }
}
