package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.domain.bot.BotTuning;
import org.kevinkib.cardgames.bullshit.domain.bot.ProbabilisticBotStrategy;
import org.kevinkib.cardgames.bullshit.presentation.BullshitGameActions;
import org.kevinkib.cardgames.bullshit.presentation.BullshitLifecycleBroadcaster;
import org.kevinkib.cardgames.bullshit.presentation.BullshitStateBroadcaster;
import org.kevinkib.cardgames.bullshit.presentation.bot.BullshitBotCoordinator;
import org.kevinkib.cardgames.bullshit.presentation.bot.CallDecisionDelay;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.ThinkingDelay;
import org.kevinkib.cardgames.presentation.bot.ThreadLocalRandomGenerator;
import org.kevinkib.cardgames.presentation.bot.UniformThinkingDelay;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.random.RandomGenerator;

/** The beans of the Bullshit context: factory, broadcasters, actions, bot delays and bot coordinator. */
@Configuration
public class BullshitConfig {

    @Bean
    public BullshitFactory bullshitFactory() {
        return new BullshitFactory(new ThreadLocalRandomGenerator());
    }

    @Bean
    public BullshitStateBroadcaster bullshitStateBroadcaster(GameMessagingService gameMessagingService,
                                                             SessionService sessionService) {
        return new BullshitStateBroadcaster(gameMessagingService, sessionService::botSeats,
                sessionService::seatNames);
    }

    @Bean
    public BullshitGameActions bullshitGameActions(SessionService sessionService,
                                                   BullshitStateBroadcaster bullshitStateBroadcaster) {
        return new BullshitGameActions(sessionService, bullshitStateBroadcaster);
    }

    @Bean
    public GameLifecycleBroadcaster bullshitLifecycleBroadcaster(BullshitStateBroadcaster bullshitStateBroadcaster) {
        return new BullshitLifecycleBroadcaster(bullshitStateBroadcaster);
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
    public BullshitBotCoordinator bullshitBotCoordinator(SessionService sessionService,
                                                         BullshitGameActions actions,
                                                         BullshitStateBroadcaster broadcaster,
                                                         BotScheduler botScheduler,
                                                         ThinkingDelay botThinkingDelay,
                                                         CallDecisionDelay callDecisionDelay) {
        RandomGenerator random = new ThreadLocalRandomGenerator();
        BullshitBotCoordinator coordinator = new BullshitBotCoordinator(
                sessionService::botSeats, actions, botScheduler, botThinkingDelay, callDecisionDelay,
                game -> new ProbabilisticBotStrategy(game.getClaimMode(), random, BotTuning.DEFAULT));
        broadcaster.addListener(coordinator);
        return coordinator;
    }
}
