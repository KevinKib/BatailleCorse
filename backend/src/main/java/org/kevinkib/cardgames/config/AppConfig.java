package org.kevinkib.cardgames.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.kevinkib.cardgames.sessionmanagement.core.application.port.UsageStatistics;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.MicrometerUsageStatistics;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.presentation.SeatSubscriptionInterceptor;
import org.kevinkib.cardgames.presentation.WebSocketDisconnectListener;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.bot.TaskSchedulerBotScheduler;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameCleanupService;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameDirectory;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.application.port.SessionRepository;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.sessionmanagement.presence.application.GameLifecycleBroadcasters;
import org.kevinkib.cardgames.sessionmanagement.presence.application.PresenceEvictionCleanup;
import org.kevinkib.cardgames.sessionmanagement.presence.application.PresenceService;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryConnectionRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.TaskSchedulerForfeitScheduler;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ConnectionRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitScheduler;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;
import java.util.List;

/**
 * The beans shared by every game. Each game declares its own in its configuration class
 * ({@link BatailleCorseConfig}, {@link BullshitConfig}, {@link KoboConfig}, {@link ThreeMenMorrisConfig});
 * the game factories and lifecycle broadcasters are discovered here as {@code List} injections.
 */
@Configuration
@EnableScheduling
public class AppConfig {

    /** Production grace before a dropped player auto-loses; only the e2e profile overrides it. */
    private static final long DEFAULT_FORFEIT_GRACE_SECONDS = 60;


    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public GameFactories gameFactories(List<GameFactory> factories) {
        return new GameFactories(factories);
    }

    @Bean
    public UsageStatistics usageStatistics(MeterRegistry meterRegistry) {
        MicrometerUsageStatistics statistics = new MicrometerUsageStatistics(meterRegistry);
        statistics.registerGauges(() -> sessionRepository().roomCount(), () -> connectionRegistry().size());
        return statistics;
    }

    @Bean
    public SessionService sessionService(GameFactories gameFactories, UsageStatistics usageStatistics) {
        return new SessionService(sessionRepository(), gameFactories, usageStatistics);
    }

    @Bean
    public GameDirectory gameDirectory(SessionService sessionService) {
        return sessionService;
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
    public GameMessagingService gameMessagingService(SimpMessagingTemplate messagingTemplate,
                                                     SessionService sessionService) {
        return new GameMessagingService(messagingTemplate, sessionService);
    }

    /** The options set on computer seats, answered by the session core for whoever drives the bots. */
    @Bean
    public BotSeatOptions botSeatOptions(SessionService sessionService) {
        return sessionService::seatOptions;
    }

    @Bean
    public BotScheduler botScheduler() {
        return new TaskSchedulerBotScheduler(taskScheduler());
    }

    @Bean
    public SeatCommands seatCommands(SessionService sessionService, GameMessagingService gameMessagingService) {
        return new SeatCommands(sessionService, gameMessagingService);
    }

    @Bean
    public LobbyBroadcaster lobbyBroadcaster(GameMessagingService gameMessagingService) {
        return new LobbyBroadcaster(gameMessagingService);
    }

    @Bean
    public SeatSubscriptionInterceptor seatSubscriptionInterceptor(SessionService sessionService) {
        return new SeatSubscriptionInterceptor(sessionService);
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
    public PresenceService presenceService(GameDirectory gameDirectory,
                                           GameLifecycleBroadcasters gameLifecycleBroadcasters,
                                           LobbyBroadcaster lobbyBroadcaster,
                                           @Value("${presence.forfeit-grace-seconds:"
                                                   + DEFAULT_FORFEIT_GRACE_SECONDS + "}") long forfeitGraceSeconds) {
        PresenceService presence = new PresenceService(
                gameDirectory, connectionRegistry(), forfeitScheduler(), clock(), forfeitLog(),
                gameLifecycleBroadcasters, Duration.ofSeconds(forfeitGraceSeconds));
        // A lobby seat whose connection dropped (or came back) is flagged to the others, so the host can remove it.
        presence.setLobbyPresenceListener((gameId, views) -> lobbyBroadcaster.send(gameId, views,
                LifecycleEventType.JOIN.toString(), new EmptyEventData(), "A player's connection changed."));
        return presence;
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
    public GameCleanupService gameCleanupService(List<GameEvictionListener> evictionListeners,
                                                 SessionService sessionService) {
        GameCleanupService cleanup = new GameCleanupService(sessionRepository(), evictionListeners);
        // A lobby whose last human left goes through the same path as an evicted one.
        sessionService.setLobbyRemovalListener(cleanup::evicted);
        // Games that ended are counted before the sweep can evict them.
        cleanup.setBeforeSweep(sessionService::recordFinishedGames);
        return cleanup;
    }
}
