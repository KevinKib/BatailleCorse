package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.kobo.domain.KoboFactory;
import org.kevinkib.cardgames.kobo.presentation.KoboGameActions;
import org.kevinkib.cardgames.kobo.presentation.KoboLifecycleBroadcaster;
import org.kevinkib.cardgames.kobo.presentation.KoboStateBroadcaster;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;
import org.kevinkib.cardgames.threemenmorris.presentation.ThreeMenMorrisGameActions;
import org.kevinkib.cardgames.threemenmorris.presentation.ThreeMenMorrisLifecycleBroadcaster;
import org.kevinkib.cardgames.threemenmorris.presentation.ThreeMenMorrisStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameCleanupService;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameDirectory;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.application.port.SessionRepository;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.bataillecorse.domain.bot.BatailleCorseBotTier;
import org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseGameActions;
import org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseLifecycleBroadcaster;
import org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseStateBroadcaster;
import org.kevinkib.cardgames.bataillecorse.presentation.bot.BatailleCorseBotCoordinator;
import org.kevinkib.cardgames.bullshit.domain.bot.BotTuning;
import org.kevinkib.cardgames.bullshit.domain.bot.ProbabilisticBotStrategy;
import org.kevinkib.cardgames.bullshit.presentation.BullshitGameActions;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.bullshit.presentation.bot.BullshitBotCoordinator;
import org.kevinkib.cardgames.bullshit.presentation.bot.CallDecisionDelay;
import org.kevinkib.cardgames.presentation.bot.TaskSchedulerBotScheduler;
import org.kevinkib.cardgames.presentation.bot.ThinkingDelay;
import org.kevinkib.cardgames.presentation.bot.ThreadLocalRandomGenerator;
import org.kevinkib.cardgames.presentation.bot.UniformThinkingDelay;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;
import java.time.Duration;

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
        return new BullshitFactory(new ThreadLocalRandomGenerator());
    }

    @Bean
    public GameFactories gameFactories() {
        return new GameFactories(List.of(batailleCorseFactory(), bullshitFactory(), koboFactory(),
                threeMenMorrisFactory()));
    }

    @Bean
    public KoboFactory koboFactory() {
        return new KoboFactory();
    }

    @Bean
    public ThreeMenMorrisFactory threeMenMorrisFactory() {
        return new ThreeMenMorrisFactory();
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
    public BatailleCorseStateBroadcaster batailleCorseStateBroadcaster(GameMessagingService gameMessagingService) {
        return new BatailleCorseStateBroadcaster(gameMessagingService);
    }

    @Bean
    public BatailleCorseGameActions batailleCorseGameActions(BatailleCorseStateBroadcaster batailleCorseStateBroadcaster) {
        return new BatailleCorseGameActions(sessionService(), batailleCorseStateBroadcaster);
    }

    @Bean
    public GameLifecycleBroadcaster batailleCorseLifecycleBroadcaster(
            BatailleCorseStateBroadcaster batailleCorseStateBroadcaster) {
        return new BatailleCorseLifecycleBroadcaster(batailleCorseStateBroadcaster, forfeitLog());
    }

    /**
     * Plays the computer seats of Bataille Corse rooms ({@code bataillecorse.bots.enabled}, on by default).
     * Each bot reacts after its tier time (400 to 2100 ms, plus or minus 100 ms) plus
     * {@code bataillecorse.bots.extra-delay-ms}. Unlike Bullshit, the {@code test} profile does not zero the
     * tier delays, so end-to-end timing matches the former local AI; only the extra delay is tunable.
     */
    @Bean
    @ConditionalOnProperty(name = "bataillecorse.bots.enabled", havingValue = "true", matchIfMissing = true)
    public BatailleCorseBotCoordinator batailleCorseBotCoordinator(
            BatailleCorseGameActions batailleCorseGameActions,
            BatailleCorseStateBroadcaster batailleCorseStateBroadcaster,
            BotScheduler botScheduler,
            @Value("${bataillecorse.bots.extra-delay-ms:0}") long extraDelayMillis) {
        // The jitter of -100..+99 ms around the tier time, as a delay of 0..199 ms added to (time - 100).
        ThinkingDelay jitter = new UniformThinkingDelay(0, 2L * BatailleCorseBotTier.JITTER_MS - 1,
                new ThreadLocalRandomGenerator());
        BatailleCorseBotCoordinator coordinator = new BatailleCorseBotCoordinator(
                sessionService()::botSeats, botSeatOptions(), gameDirectory(), batailleCorseGameActions,
                botScheduler, jitter, Duration.ofMillis(extraDelayMillis));
        batailleCorseStateBroadcaster.addListener(coordinator);
        return coordinator;
    }

    @Bean
    public BullshitStateBroadcaster bullshitStateBroadcaster(GameMessagingService gameMessagingService) {
        return new BullshitStateBroadcaster(gameMessagingService, sessionService()::botSeats,
                sessionService()::seatNames);
    }

    /** The options set on computer seats, answered by the session core for whoever drives the bots. */
    @Bean
    public BotSeatOptions botSeatOptions() {
        return sessionService()::seatOptions;
    }

    @Bean
    public BotScheduler botScheduler() {
        return new TaskSchedulerBotScheduler(taskScheduler());
    }

    /**
     * A bot "thinks" for a random 1500-3500 ms before a move. Both bounds are properties
     * ({@code bullshit.bots.delay-min-ms} / {@code delay-max-ms}); the {@code test} profile pins them to 0.
     */
    @Bean
    public ThinkingDelay botThinkingDelay(@Value("${bullshit.bots.delay-min-ms:1500}") long minMillis,
                                          @Value("${bullshit.bots.delay-max-ms:3500}") long maxMillis) {
        return new UniformThinkingDelay(minMillis, maxMillis, new ThreadLocalRandomGenerator());
    }

    /**
     * Once a bot has decided to call Bullshit it waits a further random 1500-3000 ms, the window in which a
     * human can call first ({@code bullshit.bots.call-delay-min-ms} / {@code call-delay-max-ms}).
     */
    @Bean
    public CallDecisionDelay botCallDecisionDelay(@Value("${bullshit.bots.call-delay-min-ms:1500}") long minMillis,
                                                  @Value("${bullshit.bots.call-delay-max-ms:3000}") long maxMillis) {
        return new UniformThinkingDelay(minMillis, maxMillis, new ThreadLocalRandomGenerator())::next;
    }

    @Bean
    public BullshitBotCoordinator bullshitBotCoordinator(BullshitGameActions actions,
                                                         BullshitStateBroadcaster broadcaster,
                                                         BotScheduler botScheduler,
                                                         ThinkingDelay botThinkingDelay,
                                                         CallDecisionDelay callDecisionDelay) {
        RandomGenerator random = new ThreadLocalRandomGenerator();
        BullshitBotCoordinator coordinator = new BullshitBotCoordinator(
                sessionService()::botSeats, actions, botScheduler, botThinkingDelay, callDecisionDelay,
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
        return new LobbyBroadcaster(gameMessagingService);
    }

    @Bean
    public GameLifecycleBroadcaster bullshitLifecycleBroadcaster(BullshitStateBroadcaster bullshitStateBroadcaster) {
        return new BullshitLifecycleBroadcaster(bullshitStateBroadcaster);
    }

    @Bean
    public KoboStateBroadcaster koboStateBroadcaster(GameMessagingService gameMessagingService) {
        return new KoboStateBroadcaster(gameMessagingService, sessionService()::seatNames);
    }

    @Bean
    public KoboGameActions koboGameActions(KoboStateBroadcaster koboStateBroadcaster) {
        return new KoboGameActions(sessionService(), koboStateBroadcaster);
    }

    @Bean
    public GameLifecycleBroadcaster koboLifecycleBroadcaster(KoboStateBroadcaster koboStateBroadcaster) {
        return new KoboLifecycleBroadcaster(koboStateBroadcaster);
    }

    @Bean
    public ThreeMenMorrisStateBroadcaster threeMenMorrisStateBroadcaster(GameMessagingService gameMessagingService) {
        return new ThreeMenMorrisStateBroadcaster(gameMessagingService, sessionService()::seatNames);
    }

    @Bean
    public ThreeMenMorrisGameActions threeMenMorrisGameActions(
            ThreeMenMorrisStateBroadcaster threeMenMorrisStateBroadcaster) {
        return new ThreeMenMorrisGameActions(sessionService(), threeMenMorrisStateBroadcaster);
    }

    @Bean
    public GameLifecycleBroadcaster threeMenMorrisLifecycleBroadcaster(
            ThreeMenMorrisStateBroadcaster threeMenMorrisStateBroadcaster) {
        return new ThreeMenMorrisLifecycleBroadcaster(threeMenMorrisStateBroadcaster);
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
        GameCleanupService cleanup = new GameCleanupService(sessionRepository(), evictionListeners);
        // A lobby whose last human left goes through the same path as an evicted one.
        sessionService().setLobbyRemovalListener(cleanup::evicted);
        return cleanup;
    }
}
