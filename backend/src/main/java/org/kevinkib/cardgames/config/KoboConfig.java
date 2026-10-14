package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.kobo.presentation.dto.KoboDto;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.KoboFactory;
import org.kevinkib.cardgames.kobo.domain.bot.HeuristicKoboBot;
import org.kevinkib.cardgames.kobo.presentation.KoboGameActions;
import org.kevinkib.cardgames.kobo.presentation.bot.KoboBotCoordinator;
import org.kevinkib.cardgames.kobo.presentation.bot.UniformReactionDelay;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.bot.ThreadLocalRandomGenerator;
import org.kevinkib.cardgames.presentation.bot.UniformThinkingDelay;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.kevinkib.cardgames.kobo.domain.DeckSource;
import org.kevinkib.cardgames.kobo.domain.RandomDeckSource;
import org.kevinkib.cardgames.kobo.domain.ScriptedDeckSource;

import java.util.Random;
import java.util.random.RandomGenerator;

/** The beans of the Kobo context: factory, broadcasters, actions and the bot coordinator. */
@Configuration
public class KoboConfig {

    /** A bot "thinks" for a random 600-1200 ms before each step (properties {@code kobo.bots.delay-min-ms} / {@code delay-max-ms}). */
    public static final String DEFAULT_DELAY_MIN_MS = "600";
    public static final String DEFAULT_DELAY_MAX_MS = "1200";
    /** Multiplier of the reaction times of the levels (property {@code kobo.bots.reaction-scale}); 0 means no delay. */
    public static final String DEFAULT_REACTION_SCALE = "1.0";

    /** Production: a random deal. */
    @Bean
    @Profile("!test")
    public DeckSource koboDeckSource() {
        return new RandomDeckSource();
    }

    /** End-to-end tests (profile {@code test}): random unless a test fixes the deal, see KoboTestDealController. */
    @Bean
    @Profile("test")
    public ScriptedDeckSource koboScriptedDeckSource() {
        return new ScriptedDeckSource(new RandomDeckSource());
    }

    /** Production: the bots forget and wait for real. */
    @Bean("koboBotRandom")
    @Profile("!test")
    public RandomGenerator koboBotRandom() {
        return new ThreadLocalRandomGenerator();
    }

    /**
     * End-to-end tests (profile {@code test}): a generator with a fixed seed, so a bot's forgetting is repeatable. It
     * is not a constant: a constant 0 would make every {@code nextDouble() < p} true and erase a medium bot's memory.
     */
    @Bean("koboBotRandom")
    @Profile("test")
    public RandomGenerator koboBotRandomSeeded() {
        return new Random(20261014L);
    }

    @Bean
    public KoboFactory koboFactory(DeckSource deckSource) {
        return new KoboFactory(deckSource);
    }

    @Bean
    public SeatStateBroadcaster<Kobo, KoboDto> koboStateBroadcaster(GameMessagingService gameMessagingService,
                                                     SessionService sessionService) {
        return new SeatStateBroadcaster<>(gameMessagingService, sessionService::botSeats, sessionService::seatNames,
                (game, viewer, names, bots) -> KoboDto.forViewer(game, viewer, names, bots));
    }

    @Bean
    public KoboGameActions koboGameActions(SessionService sessionService,
                                           SeatStateBroadcaster<Kobo, KoboDto> koboStateBroadcaster) {
        return new KoboGameActions(sessionService, koboStateBroadcaster);
    }

    @Bean
    public GameLifecycleBroadcaster koboLifecycleBroadcaster(SeatStateBroadcaster<Kobo, KoboDto> koboStateBroadcaster) {
        return new SeatLifecycleBroadcaster<>(Kobo.class, koboStateBroadcaster);
    }

    /**
     * Drives the computer seats. The delays are built here and not published as beans, so they do not compete with
     * the other games' {@code ThinkingDelay} beans; the {@code test} profile pins them to 0.
     */
    @Bean
    public KoboBotCoordinator koboBotCoordinator(
            SessionService sessionService,
            BotSeatOptions botSeatOptions,
            KoboGameActions actions,
            SeatStateBroadcaster<Kobo, KoboDto> koboStateBroadcaster,
            BotScheduler botScheduler,
            @Qualifier("koboBotRandom") RandomGenerator koboBotRandom,
            @Value("${kobo.bots.delay-min-ms:" + DEFAULT_DELAY_MIN_MS + "}") long minMillis,
            @Value("${kobo.bots.delay-max-ms:" + DEFAULT_DELAY_MAX_MS + "}") long maxMillis,
            @Value("${kobo.bots.reaction-scale:" + DEFAULT_REACTION_SCALE + "}") double reactionScale) {
        KoboBotCoordinator coordinator = new KoboBotCoordinator(
                sessionService::botSeats, botSeatOptions, actions, botScheduler,
                new UniformThinkingDelay(minMillis, maxMillis, koboBotRandom),
                new UniformReactionDelay(reactionScale, koboBotRandom),
                level -> new HeuristicKoboBot(level.tuning()),
                koboBotRandom);
        koboStateBroadcaster.addListener(coordinator);
        return coordinator;
    }
}
