package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bataillecorse.domain.bot.BatailleCorseBotTier;
import org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseGameActions;
import org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseLifecycleBroadcaster;
import org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseStateBroadcaster;
import org.kevinkib.cardgames.bataillecorse.presentation.bot.BatailleCorseBotCoordinator;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.bot.ThinkingDelay;
import org.kevinkib.cardgames.presentation.bot.ThreadLocalRandomGenerator;
import org.kevinkib.cardgames.presentation.bot.UniformThinkingDelay;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameDirectory;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** The beans of the Bataille Corse context: factory, broadcasters, actions and bot coordinator. */
@Configuration
public class BatailleCorseConfig {

    @Bean
    public BatailleCorseFactory batailleCorseFactory() {
        return new BatailleCorseFactory();
    }

    @Bean
    public BatailleCorseStateBroadcaster batailleCorseStateBroadcaster(GameMessagingService gameMessagingService) {
        return new BatailleCorseStateBroadcaster(gameMessagingService);
    }

    @Bean
    public BatailleCorseGameActions batailleCorseGameActions(SessionService sessionService,
            BatailleCorseStateBroadcaster batailleCorseStateBroadcaster) {
        return new BatailleCorseGameActions(sessionService, batailleCorseStateBroadcaster);
    }

    @Bean
    public GameLifecycleBroadcaster batailleCorseLifecycleBroadcaster(
            BatailleCorseStateBroadcaster batailleCorseStateBroadcaster, ForfeitLog forfeitLog) {
        return new BatailleCorseLifecycleBroadcaster(batailleCorseStateBroadcaster, forfeitLog);
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
            SessionService sessionService,
            BotSeatOptions botSeatOptions,
            GameDirectory gameDirectory,
            BatailleCorseGameActions batailleCorseGameActions,
            BatailleCorseStateBroadcaster batailleCorseStateBroadcaster,
            BotScheduler botScheduler,
            @Value("${bataillecorse.bots.extra-delay-ms:0}") long extraDelayMillis) {
        // The jitter of -100..+99 ms around the tier time, as a delay of 0..199 ms added to (time - 100).
        ThinkingDelay jitter = new UniformThinkingDelay(0, 2L * BatailleCorseBotTier.JITTER_MS - 1,
                new ThreadLocalRandomGenerator());
        BatailleCorseBotCoordinator coordinator = new BatailleCorseBotCoordinator(
                sessionService::botSeats, botSeatOptions, gameDirectory, batailleCorseGameActions,
                botScheduler, jitter, Duration.ofMillis(extraDelayMillis));
        batailleCorseStateBroadcaster.addListener(coordinator);
        return coordinator;
    }
}
