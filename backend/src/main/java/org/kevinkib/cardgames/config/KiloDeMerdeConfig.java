package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.kilodemerde.domain.DeckSource;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeFactory;
import org.kevinkib.cardgames.kilodemerde.domain.RandomDeckSource;
import org.kevinkib.cardgames.kilodemerde.domain.ScriptedDeckSource;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeBotLevel;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeBotTuning;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeBotTuning.Range;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeBots;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeReflex;
import org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeDeadlines;
import org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeGameActions;
import org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeLifecycleBroadcaster;
import org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdePassTimer;
import org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeRaceTimeout;
import org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeReadyTimeout;
import org.kevinkib.cardgames.kilodemerde.presentation.bot.KiloDeMerdeBotCoordinator;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.KiloDeMerdeDto;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.time.Clock;
import java.util.Random;

/**
 * The beans of the Kilo de merde context: factory, deck source, broadcasters, actions, the three timers (pass step,
 * race, ready barrier) and the bot coordinator. Nothing is registered in {@code AppConfig}.
 */
@Configuration
public class KiloDeMerdeConfig {

    /** A step lasts 15 s before the server passes for the slow seats; 0 turns the timer off (property {@code kilodemerde.pass-timer-ms}). */
    public static final String DEFAULT_PASS_TIMER_MS = "15000";

    /** A race stays open 5 s before the seats that did not slap draw (property {@code kilodemerde.race-timeout-ms}). */
    public static final String DEFAULT_RACE_TIMEOUT_MS = "5000";

    /**
     * The table waits 60 s for every seat to confirm it is ready (before the first round and after each round) before
     * it counts the missing ones ready (property {@code kilodemerde.ready-timeout-ms}; 0 waits for every confirmation).
     */
    public static final String DEFAULT_READY_TIMEOUT_MS = "60000";

    /** Production: a random deal. */
    @Bean
    @Profile("!test")
    public DeckSource kiloDeMerdeDeckSource() {
        return new RandomDeckSource();
    }

    /** End-to-end tests (profile {@code test}): random unless a test fixes the deal, see KiloDeMerdeTestDealController. */
    @Bean
    @Profile("test")
    public ScriptedDeckSource kiloDeMerdeScriptedDeckSource() {
        return new ScriptedDeckSource(new RandomDeckSource());
    }

    @Bean
    public KiloDeMerdeFactory kiloDeMerdeFactory(DeckSource kiloDeMerdeDeckSource) {
        return new KiloDeMerdeFactory(kiloDeMerdeDeckSource);
    }

    /** The end of the step and of the race, shared by the states (countdown) and the timers. */
    @Bean
    public KiloDeMerdeDeadlines kiloDeMerdeDeadlines(
            Clock clock,
            @Value("${kilodemerde.pass-timer-ms:" + DEFAULT_PASS_TIMER_MS + "}") long passTimerMs,
            @Value("${kilodemerde.race-timeout-ms:" + DEFAULT_RACE_TIMEOUT_MS + "}") long raceTimeoutMs,
            @Value("${kilodemerde.ready-timeout-ms:" + DEFAULT_READY_TIMEOUT_MS + "}") long readyTimeoutMs) {
        return new KiloDeMerdeDeadlines(clock, passTimerMs, raceTimeoutMs, readyTimeoutMs);
    }

    @Bean
    public SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto> kiloDeMerdeStateBroadcaster(
            GameMessagingService gameMessagingService, SessionService sessionService,
            BotSeatOptions botSeatOptions, KiloDeMerdeDeadlines kiloDeMerdeDeadlines) {
        return new SeatStateBroadcaster<>(gameMessagingService, sessionService::botSeats, sessionService::seatNames,
                (game, viewer, names, bots) -> KiloDeMerdeDto.forViewer(game, viewer, names, bots, botSeatOptions,
                        kiloDeMerdeDeadlines));
    }

    @Bean
    public KiloDeMerdeGameActions kiloDeMerdeGameActions(SessionService sessionService,
            SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto> kiloDeMerdeStateBroadcaster) {
        return new KiloDeMerdeGameActions(sessionService, kiloDeMerdeStateBroadcaster);
    }

    @Bean
    public GameLifecycleBroadcaster kiloDeMerdeLifecycleBroadcaster(
            SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto> kiloDeMerdeStateBroadcaster,
            KiloDeMerdeGameActions kiloDeMerdeGameActions) {
        return new KiloDeMerdeLifecycleBroadcaster(kiloDeMerdeStateBroadcaster, kiloDeMerdeGameActions);
    }

    /** Passes for the slow seats when the step is up ({@code kilodemerde.pass-timer-ms}, off under the {@code test} profile). */
    @Bean
    public KiloDeMerdePassTimer kiloDeMerdePassTimer(
            KiloDeMerdeGameActions actions,
            SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto> kiloDeMerdeStateBroadcaster,
            BotScheduler botScheduler, KiloDeMerdeDeadlines kiloDeMerdeDeadlines) {
        KiloDeMerdePassTimer timer = new KiloDeMerdePassTimer(actions, botScheduler, kiloDeMerdeDeadlines);
        kiloDeMerdeStateBroadcaster.addListener(timer);
        return timer;
    }

    /** Makes the seats that did not slap draw when the race is up ({@code kilodemerde.race-timeout-ms}). */
    @Bean
    public KiloDeMerdeRaceTimeout kiloDeMerdeRaceTimeout(
            KiloDeMerdeGameActions actions,
            SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto> kiloDeMerdeStateBroadcaster,
            BotScheduler botScheduler, KiloDeMerdeDeadlines kiloDeMerdeDeadlines) {
        KiloDeMerdeRaceTimeout timeout = new KiloDeMerdeRaceTimeout(actions, botScheduler, kiloDeMerdeDeadlines);
        kiloDeMerdeStateBroadcaster.addListener(timeout);
        return timeout;
    }

    /** Counts the missing seats ready when the wait is up ({@code kilodemerde.ready-timeout-ms}, off under the {@code test} profile). */
    @Bean
    public KiloDeMerdeReadyTimeout kiloDeMerdeReadyTimeout(
            KiloDeMerdeGameActions actions,
            SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto> kiloDeMerdeStateBroadcaster,
            BotScheduler botScheduler, KiloDeMerdeDeadlines kiloDeMerdeDeadlines) {
        KiloDeMerdeReadyTimeout timeout = new KiloDeMerdeReadyTimeout(actions, botScheduler, kiloDeMerdeDeadlines);
        kiloDeMerdeStateBroadcaster.addListener(timeout);
        return timeout;
    }

    /**
     * Drives the computer seats. The random source of the {@code easy} level and of the reaction delays is built here
     * and not published as a bean, so it does not join the other games' generators; the {@code test} profile gives it
     * a seed ({@code kilodemerde.bots.seed}) and pins every delay to 0.
     */
    @Bean
    public KiloDeMerdeBotCoordinator kiloDeMerdeBotCoordinator(
            SessionService sessionService,
            BotSeatOptions botSeatOptions,
            KiloDeMerdeGameActions actions,
            SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto> kiloDeMerdeStateBroadcaster,
            BotScheduler botScheduler,
            Environment environment,
            @Value("${kilodemerde.bots.seed:}") String seed) {
        Random random = seed.isBlank() ? new Random() : new Random(Long.parseLong(seed.trim()));
        KiloDeMerdeBotCoordinator coordinator = new KiloDeMerdeBotCoordinator(
                sessionService::botSeats, botSeatOptions, actions, botScheduler,
                new KiloDeMerdeReflex(botTuning(environment)), random,
                level -> KiloDeMerdeBots.forLevel(level, random));
        kiloDeMerdeStateBroadcaster.addListener(coordinator);
        return coordinator;
    }

    /**
     * The reaction delays of the bots, from the {@code kilodemerde.bots.*} properties; an absent property keeps the
     * default of {@link KiloDeMerdeBotTuning#defaults()}. Keys: {@code pass-min-ms}, {@code pass-max-ms}, and for
     * each level ({@code easy}, {@code medium}, {@code hard}) {@code <level>.own-min-ms}, {@code <level>.own-max-ms},
     * {@code <level>.follow-min-ms}, {@code <level>.follow-max-ms}.
     */
    static KiloDeMerdeBotTuning botTuning(Environment environment) {
        KiloDeMerdeBotTuning defaults = KiloDeMerdeBotTuning.defaults();
        return new KiloDeMerdeBotTuning(
                range(environment, "kilodemerde.bots.pass", defaults.pass()),
                slap(environment, KiloDeMerdeBotLevel.EASY, defaults),
                slap(environment, KiloDeMerdeBotLevel.MEDIUM, defaults),
                slap(environment, KiloDeMerdeBotLevel.HARD, defaults));
    }

    private static KiloDeMerdeBotTuning.Slap slap(Environment environment, KiloDeMerdeBotLevel level,
                                                  KiloDeMerdeBotTuning defaults) {
        String prefix = "kilodemerde.bots." + level.key();
        KiloDeMerdeBotTuning.Slap base = defaults.forLevel(level);
        return new KiloDeMerdeBotTuning.Slap(range(environment, prefix + ".own", base.ownSet()),
                range(environment, prefix + ".follow", base.follow()));
    }

    private static Range range(Environment environment, String prefix, Range fallback) {
        long min = environment.getProperty(prefix + "-min-ms", Long.class, fallback.minMs());
        long max = environment.getProperty(prefix + "-max-ms", Long.class, fallback.maxMs());
        return new Range(min, max);
    }
}
