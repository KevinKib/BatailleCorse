package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameCleanupService;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameDirectory;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.application.port.SessionRepository;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseLifecycleBroadcaster;
import org.kevinkib.cardgames.bullshit.domain.bot.BotTuning;
import org.kevinkib.cardgames.bullshit.domain.bot.ProbabilisticBotStrategy;
import org.kevinkib.cardgames.bullshit.presentation.BullshitGameActions;
import org.kevinkib.cardgames.bullshit.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.bullshit.presentation.bot.BullshitBotCoordinator;
import org.kevinkib.cardgames.bullshit.presentation.bot.TaskSchedulerBotScheduler;
import org.kevinkib.cardgames.bullshit.presentation.bot.ThinkingDelay;
import org.kevinkib.cardgames.bullshit.presentation.bot.ThreadLocalRandomGenerator;
import org.kevinkib.cardgames.bullshit.presentation.bot.UniformThinkingDelay;
import org.kevinkib.cardgames.bullshit.presentation.BullshitLifecycleBroadcaster;
import org.kevinkib.cardgames.bullshit.presentation.BullshitStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.presence.application.PresenceEvictionCleanup;
import org.kevinkib.cardgames.sessionmanagement.presence.application.PresenceService;
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
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Value;
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
    public GameDirectory gameDirectory() {
        return sessionService();
    }

    @Bean
    public SessionRepository sessionRepository() {
        return new InMemorySessionRepository(clock());
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
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
        return new GameMessagingService(messagingTemplate, sessionService());
    }

    @Bean
    public GameLifecycleBroadcaster batailleCorseLifecycleBroadcaster(GameMessagingService gameMessagingService) {
        return new BatailleCorseLifecycleBroadcaster(gameMessagingService, forfeitLog());
    }

    @Bean
    public BullshitStateBroadcaster bullshitStateBroadcaster(GameMessagingService gameMessagingService) {
        return new BullshitStateBroadcaster(gameMessagingService, sessionService()::botSeats);
    }

    @Bean
    public BotScheduler botScheduler() {
        return new TaskSchedulerBotScheduler(taskScheduler());
    }

    /** A bot "thinks" for a random 1000-2000 ms; the {@code test} profile pins it to 0 so e2e runs are not slowed. */
    @Bean
    public ThinkingDelay botThinkingDelay(@Value("${bullshit.bots.delay-min-ms:1000}") long minMillis,
                                          @Value("${bullshit.bots.delay-max-ms:2000}") long maxMillis) {
        return new UniformThinkingDelay(minMillis, maxMillis, new ThreadLocalRandomGenerator());
    }

    @Bean
    public BullshitBotCoordinator bullshitBotCoordinator(BullshitGameActions actions,
                                                         BullshitStateBroadcaster broadcaster,
                                                         BotScheduler botScheduler,
                                                         ThinkingDelay botThinkingDelay) {
        RandomGenerator random = new ThreadLocalRandomGenerator();
        BullshitBotCoordinator coordinator = new BullshitBotCoordinator(
                sessionService()::botSeats, actions, botScheduler, botThinkingDelay,
                game -> new ProbabilisticBotStrategy(game.getClaimMode(), random, BotTuning.DEFAULT));
        broadcaster.addListener(coordinator);
        return coordinator;
    }

    @Bean
    public BullshitGameActions bullshitGameActions(BullshitStateBroadcaster bullshitStateBroadcaster) {
        return new BullshitGameActions(sessionService(), bullshitStateBroadcaster);
    }

    @Bean
    public LobbyBroadcaster lobbyBroadcaster(GameMessagingService gameMessagingService) {
        return new LobbyBroadcaster(gameMessagingService, sessionService());
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
    public PresenceService presenceService(GameLifecycleBroadcasters gameLifecycleBroadcasters) {
        return new PresenceService(
                gameDirectory(), connectionRegistry(), forfeitScheduler(), clock(), forfeitLog(),
                gameLifecycleBroadcasters);
    }

    @Bean
    public WebSocketDisconnectListener webSocketDisconnectListener(PresenceService presenceService) {
        return new WebSocketDisconnectListener(presenceService);
    }

    @Bean
    public GameEvictionListener presenceEvictionCleanup() {
        return new PresenceEvictionCleanup(connectionRegistry(), forfeitLog());
    }

    @Bean
    public GameCleanupService gameCleanupService(List<GameEvictionListener> evictionListeners) {
        return new GameCleanupService(sessionRepository(), evictionListeners);
    }
}
