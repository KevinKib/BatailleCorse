package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.sessionmanagement.application.GameCleanupService;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.cardgames.sessionmanagement.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.presentation.DisconnectForfeitService;
import org.kevinkib.cardgames.presentation.ForfeitReasonRegistry;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.StompSessionSeatRegistry;
import org.kevinkib.cardgames.presentation.WebSocketDisconnectListener;
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
    public SessionService sessionService() {
        return new SessionService(sessionRepository());
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
    public DisconnectForfeitService disconnectForfeitService(GameMessagingService gameMessagingService) {
        return new DisconnectForfeitService(
                sessionService(), gameMessagingService, stompSessionSeatRegistry(), taskScheduler(), clock(), forfeitReasonRegistry());
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
