package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.ninetynine.domain.DeckSource;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNineFactory;
import org.kevinkib.cardgames.ninetynine.domain.RandomDeckSource;
import org.kevinkib.cardgames.ninetynine.domain.ScriptedDeckSource;
import org.kevinkib.cardgames.ninetynine.domain.bot.NinetyNineBots;
import org.kevinkib.cardgames.ninetynine.presentation.NinetyNineGameActions;
import org.kevinkib.cardgames.ninetynine.presentation.NinetyNineLifecycleBroadcaster;
import org.kevinkib.cardgames.ninetynine.presentation.NinetyNineRoundPause;
import org.kevinkib.cardgames.ninetynine.presentation.bot.NinetyNineBotCoordinator;
import org.kevinkib.cardgames.ninetynine.presentation.dto.NinetyNineDto;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.bot.UniformThinkingDelay;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;
import java.util.Random;

/**
 * The beans of the 99 context: factory, deck source, broadcasters, actions, the pause between rounds and the bot
 * coordinator. Nothing is registered in {@code AppConfig}.
 */
@Configuration
public class NinetyNineConfig {

    /** A bot "thinks" for a random 700-1300 ms before each play (properties {@code ninetynine.bots.delay-min-ms} / {@code delay-max-ms}). */
    public static final String DEFAULT_DELAY_MIN_MS = "700";
    public static final String DEFAULT_DELAY_MAX_MS = "1300";

    /** The table shows who lost a life for this long before the next deal (the card that busted shows first, then the overlay) (property {@code ninetynine.round-pause-ms}). */
    public static final String DEFAULT_ROUND_PAUSE_MS = "4000";

    /** Production: a random deal. */
    @Bean
    @Profile("!test")
    public DeckSource ninetyNineDeckSource() {
        return new RandomDeckSource();
    }

    /** End-to-end tests (profile {@code test}): random unless a test fixes the deal, see NinetyNineTestDealController. */
    @Bean
    @Profile("test")
    public ScriptedDeckSource ninetyNineScriptedDeckSource() {
        return new ScriptedDeckSource(new RandomDeckSource());
    }

    @Bean
    public NinetyNineFactory ninetyNineFactory(DeckSource ninetyNineDeckSource) {
        return new NinetyNineFactory(ninetyNineDeckSource);
    }

    @Bean
    public SeatStateBroadcaster<NinetyNine, NinetyNineDto> ninetyNineStateBroadcaster(
            GameMessagingService gameMessagingService, SessionService sessionService,
            BotSeatOptions botSeatOptions) {
        return new SeatStateBroadcaster<>(gameMessagingService, sessionService::botSeats, sessionService::seatNames,
                (game, viewer, names, bots) -> NinetyNineDto.forViewer(game, viewer, names, bots, botSeatOptions));
    }

    @Bean
    public NinetyNineGameActions ninetyNineGameActions(SessionService sessionService,
            SeatStateBroadcaster<NinetyNine, NinetyNineDto> ninetyNineStateBroadcaster) {
        return new NinetyNineGameActions(sessionService, ninetyNineStateBroadcaster);
    }

    @Bean
    public GameLifecycleBroadcaster ninetyNineLifecycleBroadcaster(
            SeatStateBroadcaster<NinetyNine, NinetyNineDto> ninetyNineStateBroadcaster,
            NinetyNineGameActions ninetyNineGameActions) {
        return new NinetyNineLifecycleBroadcaster(ninetyNineStateBroadcaster, ninetyNineGameActions);
    }

    /** Deals the next round {@code ninetynine.round-pause-ms} after a round end (0 under the {@code test} profile). */
    @Bean
    public NinetyNineRoundPause ninetyNineRoundPause(
            NinetyNineGameActions actions,
            SeatStateBroadcaster<NinetyNine, NinetyNineDto> ninetyNineStateBroadcaster,
            BotScheduler botScheduler,
            @Value("${ninetynine.round-pause-ms:" + DEFAULT_ROUND_PAUSE_MS + "}") long pauseMillis) {
        NinetyNineRoundPause pause = new NinetyNineRoundPause(actions, botScheduler, Duration.ofMillis(pauseMillis));
        ninetyNineStateBroadcaster.addListener(pause);
        return pause;
    }

    /**
     * Drives the computer seats. The random source of the easy and hard levels and of the thinking delay is built here
     * and not published as a bean, so it does not join the other games' generators; the {@code test} profile gives it
     * a seed ({@code ninetynine.bots.seed}) and pins the delay to 0.
     */
    @Bean
    public NinetyNineBotCoordinator ninetyNineBotCoordinator(
            SessionService sessionService,
            BotSeatOptions botSeatOptions,
            NinetyNineGameActions actions,
            SeatStateBroadcaster<NinetyNine, NinetyNineDto> ninetyNineStateBroadcaster,
            BotScheduler botScheduler,
            @Value("${ninetynine.bots.seed:}") String seed,
            @Value("${ninetynine.bots.delay-min-ms:" + DEFAULT_DELAY_MIN_MS + "}") long minMillis,
            @Value("${ninetynine.bots.delay-max-ms:" + DEFAULT_DELAY_MAX_MS + "}") long maxMillis) {
        Random random = seed.isBlank() ? new Random() : new Random(Long.parseLong(seed.trim()));
        NinetyNineBotCoordinator coordinator = new NinetyNineBotCoordinator(
                sessionService::botSeats, botSeatOptions, actions, botScheduler,
                new UniformThinkingDelay(minMillis, maxMillis, random),
                level -> NinetyNineBots.forLevel(level, random));
        ninetyNineStateBroadcaster.addListener(coordinator);
        return coordinator;
    }
}
