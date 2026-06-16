package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.sessionmanagement.application.GameCleanupService;
import org.kevinkib.cardgames.sessionmanagement.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.cardgames.sessionmanagement.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseLifecycleBroadcaster;
import org.kevinkib.cardgames.bullshit.presentation.BullshitLifecycleBroadcaster;
import org.kevinkib.cardgames.bullshit.presentation.BullshitStateBroadcaster;
import org.kevinkib.cardgames.presentation.DisconnectForfeitService;
import org.kevinkib.cardgames.presentation.SeatSubscriptionInterceptor;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.presence.application.GameLifecycleBroadcasters;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryConnectionRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.TaskSchedulerForfeitScheduler;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ConnectionRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitScheduler;
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
    public BatailleCorseFactory batailleCorseFactory() {
        return new BatailleCorseFactory();
    }

    @Bean
    public BullshitFactory bullshitFactory() {
        return new BullshitFactory();
    }

    @Bean
    public GameFactories gameFactories() {
        return new GameFactories(List.of(batailleCorseFactory(), bullshitFactory()));
    }

    @Bean
    public SessionService sessionService() {
        return new SessionService(sessionRepository(), gameFactories());
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
    public ConnectionRegistry connectionRegistry() {
        return new InMemoryConnectionRegistry();
    }

    @Bean
    public ForfeitLog forfeitLog() {
        return new InMemoryForfeitLog();
    }

    @Bean
    public GameMessagingService gameMessagingService(SimpMessagingTemplate messagingTemplate) {
        return new GameMessagingService(messagingTemplate);
    }

    @Bean
    public GameLifecycleBroadcaster batailleCorseLifecycleBroadcaster(GameMessagingService gameMessagingService) {
        return new BatailleCorseLifecycleBroadcaster(gameMessagingService, forfeitLog());
    }

    @Bean
    public BullshitStateBroadcaster bullshitStateBroadcaster(GameMessagingService gameMessagingService) {
        return new BullshitStateBroadcaster(gameMessagingService);
    }

    @Bean
    public LobbyBroadcaster lobbyBroadcaster(GameMessagingService gameMessagingService) {
        return new LobbyBroadcaster(gameMessagingService, gameFactories());
    }

    @Bean
    public GameLifecycleBroadcaster bullshitLifecycleBroadcaster(BullshitStateBroadcaster bullshitStateBroadcaster) {
        return new BullshitLifecycleBroadcaster(bullshitStateBroadcaster);
    }

    @Bean
    public SeatSubscriptionInterceptor seatSubscriptionInterceptor() {
        return new SeatSubscriptionInterceptor(sessionService());
    }

    @Bean
    public GameLifecycleBroadcasters gameLifecycleBroadcasters(List<GameLifecycleBroadcaster> broadcasters) {
        return new GameLifecycleBroadcasters(broadcasters);
    }

    @Bean
    public ForfeitScheduler forfeitScheduler() {
        return new TaskSchedulerForfeitScheduler(taskScheduler());
    }

    @Bean
    public DisconnectForfeitService disconnectForfeitService(GameLifecycleBroadcasters gameLifecycleBroadcasters) {
        return new DisconnectForfeitService(
                sessionService(), connectionRegistry(), forfeitScheduler(), clock(), forfeitLog(),
                gameLifecycleBroadcasters);
    }

    @Bean
    public WebSocketDisconnectListener webSocketDisconnectListener(DisconnectForfeitService disconnectForfeitService) {
        return new WebSocketDisconnectListener(disconnectForfeitService);
    }

    @Bean
    public GameCleanupService gameCleanupService() {
        return new GameCleanupService(sessionRepository(), connectionRegistry(), forfeitLog());
    }
}
